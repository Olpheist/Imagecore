"""
tests/test_pipeline.py - Unit tests for otsu_threshold.pipeline

Tests the pure utility functions, Otsu threshold logic, DICOM writing, and
AWS calls. compute_otsu_threshold and apply_otsu_mask are tested with simple
synthetic arrays rather than real medical images so the tests run fast.
"""

import tempfile
from pathlib import Path
from unittest.mock import MagicMock, patch

import itk
import numpy as np
import pydicom
import pytest

from otsu_threshold.pipeline import (
    _build_direction_matrix,
    apply_otsu_mask,
    compute_otsu_threshold,
    extract_middle_slice,
    normalize_to_uint8,
    start_healthimaging_import,
    upload_dicom_to_s3,
    write_masked_dicom_series,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_image(z=8, y=32, x=32, spacing=(1.0, 1.0, 1.0)):
    # Synthetic float32 ITK image with random intensities in [100, 1100)
    rng = np.random.default_rng(0)
    arr = (rng.random((z, y, x)) * 1000 + 100).astype(np.float32)
    img = itk.GetImageFromArray(arr)
    img.SetSpacing(spacing)
    return img


def _make_frame_descriptors(n_slices=3, study_uid="1.2.3.4.5"):
    # Minimal per-slice metadata dicts, matching what load_from_s3_dicom returns
    # Used to test write_masked_dicom_series without actually hitting S3
    raw = {
        "00100010": {"vr": "PN", "Value": [{"Alphabetic": "Test^Patient"}]},
        "00100020": {"vr": "LO", "Value": ["TEST-001"]},
        "00100030": {"vr": "DA", "Value": ["19800101"]},
        "00100040": {"vr": "CS", "Value": ["O"]},
        "0020000D": {"vr": "UI", "Value": [study_uid]},
        "00080020": {"vr": "DA", "Value": ["20260420"]},
        "00080030": {"vr": "TM", "Value": ["120000"]},
        "00080050": {"vr": "SH", "Value": ["ACC-001"]},
        "00200010": {"vr": "SH", "Value": ["1"]},
        "00081030": {"vr": "LO", "Value": ["Test Study"]},
        "00080060": {"vr": "CS", "Value": ["MR"]},
    }
    return [
        {
            "frame_id": f"frame-{i:04d}",
            "instance_number": i + 1,
            "image_position": [0.0, 0.0, float(i)],
            "image_orientation": [1.0, 0.0, 0.0, 0.0, 1.0, 0.0],
            "pixel_spacing": [1.0, 1.0],
            "slice_thickness": 1.0,
            "raw_dicom": raw,
        }
        for i in range(n_slices)
    ]


# ---------------------------------------------------------------------------
# _build_direction_matrix
# ---------------------------------------------------------------------------

class TestBuildDirectionMatrix:
    def test_standard_axial_columns(self):
        # Standard axial: row=[1,0,0], col=[0,1,0], so normal should be [0,0,1]
        mat = _build_direction_matrix([1.0, 0.0, 0.0, 0.0, 1.0, 0.0])
        assert mat.shape == (3, 3)
        np.testing.assert_allclose(mat[:, 0], [1.0, 0.0, 0.0])
        np.testing.assert_allclose(mat[:, 1], [0.0, 1.0, 0.0])
        np.testing.assert_allclose(mat[:, 2], [0.0, 0.0, 1.0])

    def test_columns_are_orthonormal(self):
        # Each column should be a unit vector and orthogonal to the others
        mat = _build_direction_matrix([1.0, 0.0, 0.0, 0.0, 1.0, 0.0])
        for i in range(3):
            np.testing.assert_allclose(np.linalg.norm(mat[:, i]), 1.0, atol=1e-6)
        np.testing.assert_allclose(mat[:, 0] @ mat[:, 1], 0.0, atol=1e-6)
        np.testing.assert_allclose(mat[:, 0] @ mat[:, 2], 0.0, atol=1e-6)
        np.testing.assert_allclose(mat[:, 1] @ mat[:, 2], 0.0, atol=1e-6)


# ---------------------------------------------------------------------------
# compute_otsu_threshold
# ---------------------------------------------------------------------------

class TestComputeOtsuThreshold:
    def test_returns_float(self):
        # Should be a plain Python float, not an itk or numpy scalar
        result = compute_otsu_threshold(_make_image())
        assert isinstance(result, float)

    def test_threshold_within_intensity_range(self):
        # The threshold has to land between the image min and max
        img = _make_image()
        arr = itk.GetArrayFromImage(img)
        threshold = compute_otsu_threshold(img)
        assert arr.min() <= threshold <= arr.max()

    def test_bimodal_image_splits_correctly(self):
        # Two clearly separated clusters (10 and 200) should get a threshold
        # somewhere between them
        arr = np.zeros((8, 32, 32), dtype=np.float32)
        arr[:4] = 10.0
        arr[4:] = 200.0
        img = itk.GetImageFromArray(arr)
        threshold = compute_otsu_threshold(img)
        assert 10.0 < threshold < 200.0


# ---------------------------------------------------------------------------
# apply_otsu_mask
# ---------------------------------------------------------------------------

class TestApplyOtsuMask:
    def test_output_shape_matches_input(self):
        img = _make_image(z=4, y=16, x=16)
        threshold = compute_otsu_threshold(img)
        result = apply_otsu_mask(img, threshold)
        assert result.shape == (4, 16, 16)

    def test_output_dtype_is_float32(self):
        img = _make_image()
        threshold = compute_otsu_threshold(img)
        assert apply_otsu_mask(img, threshold).dtype == np.float32

    def test_background_voxels_are_zero(self):
        # Anything at or below the threshold should be zeroed out
        img = _make_image()
        arr = itk.GetArrayFromImage(img)
        threshold = compute_otsu_threshold(img)
        result = apply_otsu_mask(img, threshold)
        np.testing.assert_array_equal(result[arr <= threshold], 0.0)

    def test_foreground_voxels_retain_original_value(self):
        # Anything above the threshold should keep its original float value
        img = _make_image()
        arr = itk.GetArrayFromImage(img)
        threshold = compute_otsu_threshold(img)
        result = apply_otsu_mask(img, threshold)
        np.testing.assert_array_equal(result[arr > threshold], arr[arr > threshold])


# ---------------------------------------------------------------------------
# extract_middle_slice
# ---------------------------------------------------------------------------

class TestExtractMiddleSlice:
    def test_3d_returns_2d(self):
        assert extract_middle_slice(np.zeros((100, 64, 64), dtype=np.float32)).ndim == 2

    def test_middle_z_slice_selected(self):
        arr = np.zeros((100, 64, 64), dtype=np.float32)
        arr[50] = 1.0
        assert extract_middle_slice(arr).max() == 1.0

    def test_single_z_squeezed(self):
        # (1, 256, 224) should squeeze to (256, 224) so we get a real 2D slice
        assert extract_middle_slice(np.zeros((1, 256, 224), dtype=np.float32)).shape == (256, 224)

    def test_2d_returned_unchanged(self):
        arr = np.zeros((64, 64), dtype=np.float32)
        assert extract_middle_slice(arr).shape == (64, 64)


# ---------------------------------------------------------------------------
# normalize_to_uint8
# ---------------------------------------------------------------------------

class TestNormalizeToUint8:
    def test_min_maps_to_0_max_maps_to_255(self):
        result = normalize_to_uint8(np.array([0.0, 0.5, 1.0], dtype=np.float32))
        assert result[0] == 0
        assert result[-1] == 255

    def test_output_dtype_is_uint8(self):
        assert normalize_to_uint8(np.array([0.0, 1.0], dtype=np.float32)).dtype == np.uint8

    def test_uniform_array_returns_zeros(self):
        # All-same-value array would divide by zero, so we just return zeros
        result = normalize_to_uint8(np.full((4, 4), 5.0, dtype=np.float32))
        np.testing.assert_array_equal(result, np.zeros((4, 4), dtype=np.uint8))


# ---------------------------------------------------------------------------
# write_masked_dicom_series
# ---------------------------------------------------------------------------

class TestWriteMaskedDicomSeries:
    def test_writes_one_file_per_slice(self):
        arr = np.ones((3, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_masked_dicom_series(arr, _make_frame_descriptors(3), Path(tmp) / "out")
            assert len(sorted(out_dir.glob("*.dcm"))) == 3

    def test_study_uid_preserved(self):
        # StudyInstanceUID has to stay the same so HealthImaging links the output
        # to the same study as the original upload
        study_uid = "1.2.840.99999.1234"
        arr = np.ones((2, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_masked_dicom_series(arr, _make_frame_descriptors(2, study_uid), Path(tmp) / "out")
            ds = pydicom.dcmread(str(sorted(out_dir.glob("*.dcm"))[0]))
            assert str(ds.StudyInstanceUID) == study_uid

    def test_series_description_contains_otsu_mask(self):
        arr = np.ones((2, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_masked_dicom_series(arr, _make_frame_descriptors(2), Path(tmp) / "out")
            ds = pydicom.dcmread(str(sorted(out_dir.glob("*.dcm"))[0]))
            assert "[Otsu Mask]" in ds.SeriesDescription

    def test_image_type_is_derived_secondary(self):
        arr = np.ones((2, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_masked_dicom_series(arr, _make_frame_descriptors(2), Path(tmp) / "out")
            ds = pydicom.dcmread(str(sorted(out_dir.glob("*.dcm"))[0]))
            assert "DERIVED" in ds.ImageType
            assert "SECONDARY" in ds.ImageType

    def test_all_slices_share_series_uid(self):
        arr = np.ones((3, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_masked_dicom_series(arr, _make_frame_descriptors(3), Path(tmp) / "out")
            uids = {str(pydicom.dcmread(str(f)).SeriesInstanceUID) for f in out_dir.glob("*.dcm")}
            assert len(uids) == 1

    def test_sop_instance_uids_are_unique(self):
        arr = np.ones((3, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_masked_dicom_series(arr, _make_frame_descriptors(3), Path(tmp) / "out")
            uids = [str(pydicom.dcmread(str(f)).SOPInstanceUID) for f in sorted(out_dir.glob("*.dcm"))]
            assert len(uids) == len(set(uids))

    def test_bits_allocated_is_8(self):
        # Output is uint8 DICOM, so BitsAllocated must be 8
        arr = np.ones((2, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_masked_dicom_series(arr, _make_frame_descriptors(2), Path(tmp) / "out")
            ds = pydicom.dcmread(str(sorted(out_dir.glob("*.dcm"))[0]))
            assert ds.BitsAllocated == 8

    def test_pixel_dimensions_match_input(self):
        arr = np.ones((2, 24, 32), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_masked_dicom_series(arr, _make_frame_descriptors(2), Path(tmp) / "out")
            ds = pydicom.dcmread(str(sorted(out_dir.glob("*.dcm"))[0]))
            assert ds.Rows == 24
            assert ds.Columns == 32


# ---------------------------------------------------------------------------
# upload_dicom_to_s3
# ---------------------------------------------------------------------------

class TestUploadDicomToS3:
    def test_uploads_each_dcm_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            dicom_dir = Path(tmp) / "dicom"
            dicom_dir.mkdir()
            for i in range(3):
                (dicom_dir / f"slice_{i:04d}.dcm").write_bytes(b"fake")

            mock_s3 = MagicMock()
            with patch("otsu_threshold.pipeline.boto3.client", return_value=mock_s3):
                upload_dicom_to_s3(dicom_dir, "my-bucket", "otsu-threshold/test")

            assert mock_s3.upload_file.call_count == 3

    def test_returns_correct_s3_uri(self):
        with tempfile.TemporaryDirectory() as tmp:
            dicom_dir = Path(tmp) / "dicom"
            dicom_dir.mkdir()
            (dicom_dir / "slice_0000.dcm").write_bytes(b"fake")

            mock_s3 = MagicMock()
            with patch("otsu_threshold.pipeline.boto3.client", return_value=mock_s3):
                uri = upload_dicom_to_s3(dicom_dir, "my-bucket", "otsu-threshold/test")

            assert uri == "s3://my-bucket/otsu-threshold/test/"


# ---------------------------------------------------------------------------
# start_healthimaging_import
# ---------------------------------------------------------------------------

class TestStartHealthImagingImport:
    def test_passes_correct_args_to_boto3(self):
        mock_client = MagicMock()
        mock_client.start_dicom_import_job.return_value = {"jobId": "job-abc-123"}

        with patch("otsu_threshold.pipeline.boto3.client", return_value=mock_client):
            start_healthimaging_import(
                datastore_id="ds-001",
                input_s3_uri="s3://bucket/input/",
                output_s3_uri="s3://bucket/logs/",
                import_role_arn="arn:aws:iam::123:role/import-role",
            )

        kwargs = mock_client.start_dicom_import_job.call_args[1]
        assert kwargs["datastoreId"] == "ds-001"
        assert kwargs["inputS3Uri"] == "s3://bucket/input/"
        assert kwargs["outputS3Uri"] == "s3://bucket/logs/"
        assert kwargs["dataAccessRoleArn"] == "arn:aws:iam::123:role/import-role"

    def test_returns_job_id(self):
        mock_client = MagicMock()
        mock_client.start_dicom_import_job.return_value = {"jobId": "job-abc-123"}

        with patch("otsu_threshold.pipeline.boto3.client", return_value=mock_client):
            job_id = start_healthimaging_import(
                datastore_id="ds-001",
                input_s3_uri="s3://bucket/input/",
                output_s3_uri="s3://bucket/logs/",
                import_role_arn="arn:aws:iam::123:role/import-role",
            )

        assert job_id == "job-abc-123"
