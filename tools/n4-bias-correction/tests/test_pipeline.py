"""
tests/test_pipeline.py - Unit tests for n4_bias_correction.pipeline

Covers pure functions, ITK image operations, DICOM writing, and AWS calls.
run_n4 is excluded as it requires a real ITK N4 filter and is slow; treat it
as an integration test to run separately.
"""

import gzip
import json
import tempfile
from pathlib import Path
from unittest.mock import MagicMock, patch

import itk
import numpy as np
import pydicom
import pytest

from n4_bias_correction.pipeline import (
    _build_direction_matrix,
    _collect_frames,
    _get_tag,
    _parse_healthimaging_metadata,
    _upsample_arr,
    extract_middle_slice,
    normalize_to_uint8,
    pad_single_slice,
    shrink_image,
    start_healthimaging_import,
    upload_dicom_to_s3,
    write_corrected_dicom_series,
)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_image(z=8, y=32, x=32, spacing=(1.0, 1.0, 1.0)):
    """Create a synthetic float32 ITK 3D image. Numpy array shape is (z, y, x)."""
    rng = np.random.default_rng(0)
    arr = (rng.random((z, y, x)) * 1000 + 100).astype(np.float32)
    img = itk.GetImageFromArray(arr)
    img.SetSpacing(spacing)
    return img


def _make_raw_dicom(study_uid="1.2.3.4.5"):
    """Minimal HealthImaging DICOM JSON dict for use in frame_descriptors."""
    return {
        "00100010": {"vr": "PN", "Value": [{"Alphabetic": "Test^Patient"}]},
        "00100020": {"vr": "LO", "Value": ["TEST-001"]},
        "00100030": {"vr": "DA", "Value": ["19800101"]},
        "00100040": {"vr": "CS", "Value": ["O"]},
        "0020000D": {"vr": "UI", "Value": [study_uid]},
        "00080020": {"vr": "DA", "Value": ["20260404"]},
        "00080030": {"vr": "TM", "Value": ["120000"]},
        "00080050": {"vr": "SH", "Value": ["ACC-001"]},
        "00200010": {"vr": "SH", "Value": ["1"]},
        "00081030": {"vr": "LO", "Value": ["Test Study"]},
        "00080060": {"vr": "CS", "Value": ["MR"]},
    }


def _make_frame_descriptors(n_slices=3, study_uid="1.2.3.4.5"):
    """Minimal frame_descriptors matching the structure from _collect_frames."""
    raw = _make_raw_dicom(study_uid)
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


def _make_metadata(slices):
    """
    Build a minimal HealthImaging metadata dict.
    slices is a list of (z_position, instance_number) tuples.
    """
    instances = {}
    for i, (z_pos, instance_num) in enumerate(slices):
        instances[f"instance-{i}"] = {
            "DICOM": {
                "00200013": {"vr": "IS", "Value": [instance_num]},
                "00200032": {"vr": "DS", "Value": [0.0, 0.0, z_pos]},
                "00200037": {"vr": "DS", "Value": [1.0, 0.0, 0.0, 0.0, 1.0, 0.0]},
                "00280030": {"vr": "DS", "Value": [1.0, 1.0]},
                "00180050": {"vr": "DS", "Value": [1.0]},
            },
            "ImageFrames": [{"ID": f"frame-{i}"}],
        }
    return {"Study": {"Series": {"series-1": {"Instances": instances}}}}


# ---------------------------------------------------------------------------
# _get_tag
# ---------------------------------------------------------------------------

class TestGetTag:
    def test_scalar_string(self):
        raw = {"00100020": {"vr": "LO", "Value": ["PATIENT-001"]}}
        assert _get_tag(raw, "00100020") == "PATIENT-001"

    def test_scalar_number(self):
        raw = {"00200013": {"vr": "IS", "Value": [5]}}
        assert _get_tag(raw, "00200013") == 5

    def test_person_name_vr_unwrapped(self):
        raw = {"00100010": {"vr": "PN", "Value": [{"Alphabetic": "Smith^John"}]}}
        assert _get_tag(raw, "00100010") == "Smith^John"

    def test_missing_tag_returns_none(self):
        assert _get_tag({}, "00100020") is None

    def test_missing_tag_returns_custom_default(self):
        assert _get_tag({}, "00100020", "fallback") == "fallback"

    def test_empty_value_returns_default(self):
        raw = {"00100020": {"vr": "LO", "Value": []}}
        assert _get_tag(raw, "00100020", "default") == "default"

    def test_multi_value_returns_first(self):
        raw = {"00200037": {"vr": "DS", "Value": [1.0, 0.0, 0.0, 0.0, 1.0, 0.0]}}
        assert _get_tag(raw, "00200037") == 1.0


# ---------------------------------------------------------------------------
# _parse_healthimaging_metadata
# ---------------------------------------------------------------------------

class TestParseHealthImagingMetadata:
    def test_decompresses_and_parses(self):
        payload = {"Study": {"Series": {}}}
        raw_bytes = gzip.compress(json.dumps(payload).encode())
        assert _parse_healthimaging_metadata(raw_bytes) == payload

    def test_returns_dict(self):
        raw_bytes = gzip.compress(b"{}")
        assert isinstance(_parse_healthimaging_metadata(raw_bytes), dict)


# ---------------------------------------------------------------------------
# _collect_frames
# ---------------------------------------------------------------------------

class TestCollectFrames:
    def test_returns_one_frame_per_instance(self):
        frames = _collect_frames(_make_metadata([(0.0, 1), (1.0, 2), (2.0, 3)]))
        assert len(frames) == 3

    def test_sorted_by_z_position(self):
        frames = _collect_frames(_make_metadata([(2.0, 3), (0.0, 1), (1.0, 2)]))
        z_positions = [f["image_position"][2] for f in frames]
        assert z_positions == sorted(z_positions)

    def test_frame_has_required_keys(self):
        frame = _collect_frames(_make_metadata([(0.0, 1)]))[0]
        for key in ("frame_id", "instance_number", "image_position",
                    "image_orientation", "pixel_spacing", "slice_thickness", "raw_dicom"):
            assert key in frame

    def test_raw_dicom_populated(self):
        frame = _collect_frames(_make_metadata([(0.0, 1)]))[0]
        assert isinstance(frame["raw_dicom"], dict)
        assert len(frame["raw_dicom"]) > 0

    def test_empty_metadata_returns_empty_list(self):
        assert _collect_frames({}) == []

    def test_slice_thickness_none_when_absent(self):
        metadata = {"Study": {"Series": {"s": {"Instances": {"i": {
            "DICOM": {
                "00200013": {"vr": "IS", "Value": [1]},
                "00200032": {"vr": "DS", "Value": [0.0, 0.0, 0.0]},
                "00200037": {"vr": "DS", "Value": [1.0, 0.0, 0.0, 0.0, 1.0, 0.0]},
                "00280030": {"vr": "DS", "Value": [1.0, 1.0]},
            },
            "ImageFrames": [{"ID": "f0"}],
        }}}}}}
        assert _collect_frames(metadata)[0]["slice_thickness"] is None


# ---------------------------------------------------------------------------
# _build_direction_matrix
# ---------------------------------------------------------------------------

class TestBuildDirectionMatrix:
    def test_standard_axial_columns(self):
        # Standard axial: row=[1,0,0], col=[0,1,0], normal=[0,0,1]
        mat = _build_direction_matrix([1.0, 0.0, 0.0, 0.0, 1.0, 0.0])
        assert mat.shape == (3, 3)
        np.testing.assert_allclose(mat[:, 0], [1.0, 0.0, 0.0])
        np.testing.assert_allclose(mat[:, 1], [0.0, 1.0, 0.0])
        np.testing.assert_allclose(mat[:, 2], [0.0, 0.0, 1.0])

    def test_columns_are_orthonormal(self):
        mat = _build_direction_matrix([1.0, 0.0, 0.0, 0.0, 1.0, 0.0])
        for i in range(3):
            np.testing.assert_allclose(np.linalg.norm(mat[:, i]), 1.0, atol=1e-6)
        np.testing.assert_allclose(mat[:, 0] @ mat[:, 1], 0.0, atol=1e-6)
        np.testing.assert_allclose(mat[:, 0] @ mat[:, 2], 0.0, atol=1e-6)
        np.testing.assert_allclose(mat[:, 1] @ mat[:, 2], 0.0, atol=1e-6)


# ---------------------------------------------------------------------------
# pad_single_slice
# ---------------------------------------------------------------------------

class TestPadSingleSlice:
    def test_single_slice_is_padded(self):
        _, was_padded = pad_single_slice(_make_image(z=1), shrink_factor=2)
        assert was_padded is True

    def test_padded_z_equals_shrink_factor_times_8(self):
        padded, _ = pad_single_slice(_make_image(z=1), shrink_factor=2)
        assert padded.GetLargestPossibleRegion().GetSize()[2] == 2 * 8

    def test_padded_z_scales_with_shrink_factor(self):
        padded, _ = pad_single_slice(_make_image(z=1), shrink_factor=4)
        assert padded.GetLargestPossibleRegion().GetSize()[2] == 4 * 8

    def test_sufficient_z_not_padded(self):
        result, was_padded = pad_single_slice(_make_image(z=20), shrink_factor=2)
        assert was_padded is False
        assert result.GetLargestPossibleRegion().GetSize()[2] == 20

    def test_spacing_preserved_after_padding(self):
        img = _make_image(z=1, spacing=(0.5, 0.5, 2.0))
        padded, _ = pad_single_slice(img, shrink_factor=2)
        np.testing.assert_allclose(padded.GetSpacing(), img.GetSpacing())

    def test_2d_image_returned_unchanged(self):
        img_2d = itk.GetImageFromArray(np.ones((32, 32), dtype=np.float32))
        _, was_padded = pad_single_slice(img_2d, shrink_factor=2)
        assert was_padded is False


# ---------------------------------------------------------------------------
# shrink_image
# ---------------------------------------------------------------------------

class TestShrinkImage:
    def test_output_size_halved(self):
        shrunk = shrink_image(_make_image(z=8, y=32, x=32), factor=2)
        size = shrunk.GetLargestPossibleRegion().GetSize()
        assert (size[0], size[1], size[2]) == (16, 16, 4)

    def test_small_z_not_shrunk(self):
        # z=3, factor=2: 3//2=1 < 4, so z should stay at 3
        shrunk = shrink_image(_make_image(z=3, y=32, x=32), factor=2)
        size = shrunk.GetLargestPossibleRegion().GetSize()
        assert size[2] == 3
        assert size[0] == 16
        assert size[1] == 16

    def test_factor_1_unchanged(self):
        img = _make_image(z=8, y=32, x=32)
        shrunk = shrink_image(img, factor=1)
        assert list(shrunk.GetLargestPossibleRegion().GetSize()) == \
               list(img.GetLargestPossibleRegion().GetSize())


# ---------------------------------------------------------------------------
# _upsample_arr
# ---------------------------------------------------------------------------

class TestUpsampleArr:
    def test_xy_upsampled_to_target(self):
        arr = np.ones((4, 16, 16), dtype=np.float32)
        assert _upsample_arr(arr, (4, 32, 32)).shape == (4, 32, 32)

    def test_z_tiled_when_less_than_target(self):
        arr = np.ones((2, 16, 16), dtype=np.float32)
        assert _upsample_arr(arr, (5, 16, 16)).shape == (5, 16, 16)

    def test_z_cropped_when_more_than_target(self):
        arr = np.ones((8, 16, 16), dtype=np.float32)
        assert _upsample_arr(arr, (4, 16, 16)).shape == (4, 16, 16)

    def test_output_dtype_is_float32(self):
        arr = np.ones((4, 16, 16), dtype=np.float32)
        assert _upsample_arr(arr, (4, 32, 32)).dtype == np.float32


# ---------------------------------------------------------------------------
# extract_middle_slice
# ---------------------------------------------------------------------------

class TestExtractMiddleSlice:
    def test_3d_returns_2d(self):
        assert extract_middle_slice(np.zeros((100, 64, 64), dtype=np.float32)).ndim == 2

    def test_middle_z_slice_selected_when_z_is_largest(self):
        arr = np.zeros((100, 64, 64), dtype=np.float32)
        arr[50] = 1.0
        assert extract_middle_slice(arr).max() == 1.0

    def test_single_z_squeezed_before_axis_selection(self):
        # (1, 256, 224) should squeeze to (256, 224) and return as-is
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
        result = normalize_to_uint8(np.full((4, 4), 5.0, dtype=np.float32))
        np.testing.assert_array_equal(result, np.zeros((4, 4), dtype=np.uint8))


# ---------------------------------------------------------------------------
# write_corrected_dicom_series
# ---------------------------------------------------------------------------

class TestWriteCorrectedDicomSeries:
    def test_writes_one_file_per_slice(self):
        arr = np.ones((3, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_corrected_dicom_series(arr, _make_frame_descriptors(3), Path(tmp) / "out")
            assert len(sorted(out_dir.glob("*.dcm"))) == 3

    def test_study_uid_preserved(self):
        study_uid = "1.2.840.99999.1234"
        arr = np.ones((2, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_corrected_dicom_series(arr, _make_frame_descriptors(2, study_uid), Path(tmp) / "out")
            ds = pydicom.dcmread(str(sorted(out_dir.glob("*.dcm"))[0]))
            assert str(ds.StudyInstanceUID) == study_uid

    def test_image_type_is_derived_secondary(self):
        arr = np.ones((2, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_corrected_dicom_series(arr, _make_frame_descriptors(2), Path(tmp) / "out")
            ds = pydicom.dcmread(str(sorted(out_dir.glob("*.dcm"))[0]))
            assert "DERIVED" in ds.ImageType
            assert "SECONDARY" in ds.ImageType

    def test_all_slices_share_series_uid(self):
        arr = np.ones((3, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_corrected_dicom_series(arr, _make_frame_descriptors(3), Path(tmp) / "out")
            uids = {str(pydicom.dcmread(str(f)).SeriesInstanceUID) for f in out_dir.glob("*.dcm")}
            assert len(uids) == 1

    def test_sop_instance_uids_are_unique(self):
        arr = np.ones((3, 16, 16), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_corrected_dicom_series(arr, _make_frame_descriptors(3), Path(tmp) / "out")
            uids = [str(pydicom.dcmread(str(f)).SOPInstanceUID) for f in sorted(out_dir.glob("*.dcm"))]
            assert len(uids) == len(set(uids))

    def test_pixel_dimensions_match_input(self):
        arr = np.ones((2, 24, 32), dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_corrected_dicom_series(arr, _make_frame_descriptors(2), Path(tmp) / "out")
            ds = pydicom.dcmread(str(sorted(out_dir.glob("*.dcm"))[0]))
            assert ds.Rows == 24
            assert ds.Columns == 32

    def test_rescale_tags_present(self):
        arr = np.linspace(100.0, 1000.0, 2 * 16 * 16, dtype=np.float32).reshape(2, 16, 16)
        with tempfile.TemporaryDirectory() as tmp:
            out_dir = write_corrected_dicom_series(arr, _make_frame_descriptors(2), Path(tmp) / "out")
            ds = pydicom.dcmread(str(sorted(out_dir.glob("*.dcm"))[0]))
            assert hasattr(ds, "RescaleSlope")
            assert hasattr(ds, "RescaleIntercept")

    def test_uniform_array_does_not_raise(self):
        arr = np.full((2, 16, 16), 500.0, dtype=np.float32)
        with tempfile.TemporaryDirectory() as tmp:
            write_corrected_dicom_series(arr, _make_frame_descriptors(2), Path(tmp) / "out")


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
            with patch("n4_bias_correction.pipeline.boto3.client", return_value=mock_s3):
                upload_dicom_to_s3(dicom_dir, "my-bucket", "n4-corrected/test")

            assert mock_s3.upload_file.call_count == 3

    def test_returns_correct_s3_uri(self):
        with tempfile.TemporaryDirectory() as tmp:
            dicom_dir = Path(tmp) / "dicom"
            dicom_dir.mkdir()
            (dicom_dir / "slice_0000.dcm").write_bytes(b"fake")

            mock_s3 = MagicMock()
            with patch("n4_bias_correction.pipeline.boto3.client", return_value=mock_s3):
                uri = upload_dicom_to_s3(dicom_dir, "my-bucket", "n4-corrected/test")

            assert uri == "s3://my-bucket/n4-corrected/test/"

    def test_files_uploaded_to_correct_prefix(self):
        with tempfile.TemporaryDirectory() as tmp:
            dicom_dir = Path(tmp) / "dicom"
            dicom_dir.mkdir()
            (dicom_dir / "slice_0000.dcm").write_bytes(b"fake")

            mock_s3 = MagicMock()
            with patch("n4_bias_correction.pipeline.boto3.client", return_value=mock_s3):
                upload_dicom_to_s3(dicom_dir, "my-bucket", "my-prefix")

            args = mock_s3.upload_file.call_args[0]
            assert args[1] == "my-bucket"
            assert args[2] == "my-prefix/slice_0000.dcm"


# ---------------------------------------------------------------------------
# start_healthimaging_import
# ---------------------------------------------------------------------------

class TestStartHealthImagingImport:
    def test_passes_correct_args_to_boto3(self):
        mock_client = MagicMock()
        mock_client.start_dicom_import_job.return_value = {"jobId": "job-abc-123"}

        with patch("n4_bias_correction.pipeline.boto3.client", return_value=mock_client):
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

        with patch("n4_bias_correction.pipeline.boto3.client", return_value=mock_client):
            job_id = start_healthimaging_import(
                datastore_id="ds-001",
                input_s3_uri="s3://bucket/input/",
                output_s3_uri="s3://bucket/logs/",
                import_role_arn="arn:aws:iam::123:role/import-role",
            )

        assert job_id == "job-abc-123"
