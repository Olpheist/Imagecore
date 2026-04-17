"""
pipeline.py - N4 Bias Field Correction Tool

Corrects B1 field inhomogeneity in MRI volumes using the ITK N4 algorithm.
Produces a PDF report with side-by-side comparison of the original, corrected,
and bias field middle slices. The corrected volume is written as a DICOM series
(deep-copied from the originals with only pixel data replaced) and reimported
into HealthImaging under the same Study Instance UID.

Usage (ECS / S3 source, via environment variables):
    ORIGINAL_S3_BUCKET=<bucket> ORIGINAL_S3_PREFIX=<prefix>
    OUTPUT_S3_BUCKET=<bucket>   OUTPUT_S3_PREFIX=<prefix>
    IMPORT_ROLE_ARN=<arn>
    n4-bias-correction

Usage (local file, for development):
    n4-bias-correction --input <image_path> [--output-dir <dir>] [--shrink-factor <n>]

Supported local input formats (anything itk can read):
    .nii, .nii.gz
    .dcm (single file or directory)
    .mha, .mhd, .nrrd, .nhdr
"""

import argparse
import copy
import json
import os
import sys
import tempfile
from datetime import datetime
from pathlib import Path

import boto3
import itk
import numpy as np
from PIL import Image

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (
    HRFlowable,
    Image as RLImage,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


# ---------------------------------------------------------------------------
# Image loading and correction
# ---------------------------------------------------------------------------

def load_as_float(path: Path) -> itk.Image:
    """Load an itk-supported image cast to float32, as required by N4."""
    return itk.imread(str(path), itk.F)


def pad_single_slice(img: itk.Image, shrink_factor: int) -> tuple:
    """
    Ensure the image has enough slices in z for N4's internal B-spline lattice.

    N4 fits a B-spline field over the image volume. If the z dimension is too
    small (e.g. a single-slice DICOM), the lattice computation produces zero-
    valued spacing and ITK refuses to proceed. This function replicates slices
    in z until there are at least shrink_factor * 8 slices, which guarantees
    that after shrinking there are still >= 8 slices, enough for the B-spline
    to be well-defined.

    Spatial metadata (spacing, origin, direction) is copied to the padded image
    so downstream filters see correct physical coordinates.

    Returns (image, was_padded). If was_padded is True the caller should crop
    the output arrays back to the original z extent after correction.
    """
    if img.GetImageDimension() != 3:
        return img, False
    z_size = img.GetLargestPossibleRegion().GetSize()[2]
    min_z = shrink_factor * 8  # ensures shrunken z >= 8
    if z_size >= min_z:
        return img, False

    # Tile the existing slices in z until we reach min_z, then trim to exactly min_z
    arr = itk.GetArrayFromImage(img)           # shape: (z, H, W)
    repeats = int(np.ceil(min_z / z_size))
    arr_padded = np.tile(arr, (repeats, 1, 1))[:min_z]

    padded = itk.GetImageFromArray(arr_padded)
    padded.SetSpacing(img.GetSpacing())
    padded.SetOrigin(img.GetOrigin())
    padded.SetDirection(img.GetDirection())
    return padded, True


# ---------------------------------------------------------------------------
# S3 DICOM loader
# ---------------------------------------------------------------------------

def _build_direction_matrix(image_orientation: list[float]) -> np.ndarray:
    """
    Construct a 3x3 ITK direction matrix from the 6-element
    Image Orientation Patient vector (row cosines + col cosines).
    The slice normal is the cross product of the two.
    """
    row = np.array(image_orientation[:3])
    col = np.array(image_orientation[3:])
    normal = np.cross(row, col)
    # ITK direction columns are: row-direction, col-direction, slice-direction
    mat = np.column_stack([row, col, normal])
    return mat


def load_from_s3_dicom(bucket: str, prefix: str) -> tuple:
    """
    Download all .dcm files from s3://bucket/prefix, read them with pydicom,
    sort by InstanceNumber (fallback: ImagePositionPatient Z), assemble into
    a 3D float32 ITK image with correct spatial metadata, and return it ready
    for N4 along with the sorted pydicom datasets for tag-preserving output.

    RescaleSlope and RescaleIntercept are applied when building the float
    volume so N4 operates on real-valued intensities (HU for CT, signal
    units for MRI).

    Returns (itk_image, sorted_datasets).

    IAM permissions required:
        s3:GetObject, s3:ListBucket
    """
    import pydicom

    s3 = boto3.client("s3")
    prefix_norm = prefix.rstrip("/") + "/"

    # List all .dcm objects under the prefix
    paginator = s3.get_paginator("list_objects_v2")
    dcm_keys = []
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix_norm):
        for obj in page.get("Contents", []):
            if obj["Key"].lower().endswith(".dcm"):
                dcm_keys.append(obj["Key"])

    if not dcm_keys:
        raise ValueError(f"No .dcm files found at s3://{bucket}/{prefix_norm}")

    print(f"  Found {len(dcm_keys)} DICOM files at s3://{bucket}/{prefix_norm}", flush=True)

    # Download and read all files, keeping datasets in memory
    datasets = []
    with tempfile.TemporaryDirectory() as tmp:
        for key in dcm_keys:
            local_path = Path(tmp) / Path(key).name
            s3.download_file(bucket, key, str(local_path))
            ds = pydicom.dcmread(str(local_path))
            datasets.append(ds)

    # Sort by InstanceNumber, fallback to ImagePositionPatient Z
    def _sort_key(ds):
        try:
            return (0, int(ds.InstanceNumber))
        except (AttributeError, ValueError, TypeError):
            pass
        try:
            return (0, float(ds.ImagePositionPatient[2]))
        except (AttributeError, IndexError, ValueError, TypeError):
            pass
        return (1, 0)

    datasets.sort(key=_sort_key)

    # Build float32 pixel volume, applying rescale slope/intercept if present
    pixel_arrays = []
    for ds in datasets:
        arr = ds.pixel_array.astype(np.float32)
        slope = float(getattr(ds, "RescaleSlope", 1.0))
        intercept = float(getattr(ds, "RescaleIntercept", 0.0))
        pixel_arrays.append(arr * slope + intercept)

    volume = np.stack(pixel_arrays, axis=0)  # (z, y, x)
    image_3d = itk.GetImageFromArray(volume)

    # Set spatial metadata from the first slice
    first = datasets[0]
    pixel_spacing = [float(v) for v in getattr(first, "PixelSpacing", [1.0, 1.0])]
    image_position = [float(v) for v in getattr(first, "ImagePositionPatient", [0.0, 0.0, 0.0])]
    image_orientation = [float(v) for v in getattr(first, "ImageOrientationPatient", [1, 0, 0, 0, 1, 0])]

    if len(datasets) > 1:
        pos0 = np.array([float(v) for v in getattr(datasets[0], "ImagePositionPatient", [0, 0, 0])])
        pos1 = np.array([float(v) for v in getattr(datasets[1], "ImagePositionPatient", [0, 0, 0])])
        slice_spacing = float(np.linalg.norm(pos1 - pos0))
        if slice_spacing == 0.0:
            thickness = getattr(first, "SliceThickness", None)
            slice_spacing = float(thickness) if thickness is not None else 1.0
    else:
        thickness = getattr(first, "SliceThickness", None)
        slice_spacing = float(thickness) if thickness is not None else 1.0

    # ITK spacing: (x, y, z), DICOM PixelSpacing is [row=y, col=x]
    image_3d.SetSpacing([pixel_spacing[1], pixel_spacing[0], slice_spacing])
    image_3d.SetOrigin(image_position)

    direction = _build_direction_matrix(image_orientation)
    itk_direction = itk.matrix_from_array(direction.astype(np.float64))
    image_3d.SetDirection(itk_direction)

    return image_3d, datasets


def shrink_image(image: itk.Image, factor: int) -> itk.Image:
    """
    Uniformly downsample the image to speed up N4 bias estimation.

    ShrinkImageFilter uses floor division, so a dimension of size N becomes
    floor(N / factor). Any dimension where that result would drop below 4 voxels
    is left at its original size, this prevents the N4 B-spline lattice from
    collapsing in low-z images even after padding.

    The bias field is smooth by definition, so estimating it on a downsampled
    image introduces negligible error while significantly reducing runtime on
    large 3D volumes.
    """
    ImageType = type(image)
    shrink = itk.ShrinkImageFilter[ImageType, ImageType].New(Input=image)
    ndim = image.GetImageDimension()
    size = image.GetLargestPossibleRegion().GetSize()
    factors = [factor if (size[i] // factor) >= 4 else 1 for i in range(ndim)]
    shrink.SetShrinkFactors(factors)
    shrink.Update()
    return shrink.GetOutput()


def run_n4(image: itk.Image) -> tuple[np.ndarray, np.ndarray]:
    """
    Run N4BiasFieldCorrectionImageFilter and derive the multiplicative bias field.

    N4 (N4ITK) is a multi-resolution B-spline algorithm that estimates and
    removes the smooth intensity inhomogeneity (bias field) common in MRI caused
    by B1 field non-uniformity. It iterates at 4 resolution levels, refining the
    B-spline control point lattice at each level.

    The corrected image is taken directly from the filter output. The bias field
    is derived as original / corrected, voxels where the corrected value is
    near zero are assigned a bias of 1.0 (no correction) to avoid division by
    zero (which would produce inf/nan and corrupt the PDF report).

    Note: itk.BSplineControlPointImageFilter (used in some pipelines to
    reconstruct the bias field from the B-spline lattice) is not wrapped in
    the ITK Python bindings for this ITK version, so the ratio approach is used
    instead.

    Returns:
        corrected_arr  - float32 numpy array of bias-corrected intensities
        bias_field_arr - float32 numpy array of the multiplicative bias field
    """
    n4 = itk.N4BiasFieldCorrectionImageFilter.New(Input=image)
    n4.SetNumberOfFittingLevels(4)
    # SetMaximumNumberOfIterations requires a plain Python list in these ITK
    # Python bindings, itk.Array[itk.UI] objects are not copied correctly
    # across the Python/C++ boundary and arrive with size 0 on the C++ side
    n4.SetMaximumNumberOfIterations([50, 50, 50, 50])
    n4.SetConvergenceThreshold(0.001)
    n4.Update()

    orig_arr = itk.GetArrayFromImage(image).astype(np.float32)
    corr_arr = itk.GetArrayFromImage(n4.GetOutput()).astype(np.float32)
    bias_arr = np.where(np.abs(corr_arr) > 1e-6, orig_arr / corr_arr, np.ones_like(orig_arr))
    return corr_arr, bias_arr


def _upsample_arr(arr: np.ndarray, target_shape: tuple) -> np.ndarray:
    """
    Upsample a 3D numpy array to target_shape using bilinear resize in XY and
    linear interpolation in Z.

    Used to bring the corrected and bias arrays (computed on the shrunken image)
    back to the full n4_input resolution before cropping and saving. Each z-slice
    is resized independently in XY using Pillow's BILINEAR interpolation. If the
    shrunken z count differs from the target z count (which happens when the shrink
    factor doesn't divide the depth evenly), the z axis is resampled via linear
    interpolation so every output slice maps to a real position in the source
    volume rather than being a repeated copy.
    """
    from PIL import Image as PILImage
    tz, ty, tx = target_shape
    # Resize each z-slice in XY
    xy_resized = np.stack([
        np.array(PILImage.fromarray(arr[z]).resize((tx, ty), PILImage.BILINEAR))
        for z in range(arr.shape[0])
    ], axis=0)
    # Resample z axis with linear interpolation when the depth doesn't match
    src_nz = xy_resized.shape[0]
    if src_nz != tz:
        src_indices = np.linspace(0, src_nz - 1, tz)
        lo = np.floor(src_indices).astype(int)
        hi = np.minimum(lo + 1, src_nz - 1)
        alpha = (src_indices - lo).astype(np.float32)
        xy_resized = (
            xy_resized[lo] * (1.0 - alpha)[:, None, None]
            + xy_resized[hi] * alpha[:, None, None]
        )
    return xy_resized.astype(np.float32)


# ---------------------------------------------------------------------------
# DICOM output pipeline
# ---------------------------------------------------------------------------

def write_corrected_dicom_series(
    datasets: list,
    corrected_arr: np.ndarray,
    out_dir: Path,
) -> Path:
    """
    Write the N4-corrected volume as a DICOM series by deep-copying the original
    pydicom datasets and replacing only the pixel data.

    All original DICOM tags (patient, study, series, spatial metadata, MR IOD
    required tags, etc.) are preserved intact. Only these fields change:
        SeriesInstanceUID  - new UID shared across all corrected slices
        SOPInstanceUID     - new UID per slice
        SeriesDescription  - original description + " [N4 Corrected]"
        SeriesNumber       - original + 900 (sorts after source series)
        ImageType          - ["DERIVED", "SECONDARY"]
        PixelData          - N4-corrected values, rescaled back to original dtype

    StudyInstanceUID is kept identical so HealthImaging associates the corrected
    series with the same study as the original upload.

    Returns the output directory containing the .dcm files.
    """
    import pydicom
    from pydicom.uid import generate_uid

    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    series_uid = generate_uid()

    for i, (ds_orig, slice_float) in enumerate(zip(datasets, corrected_arr)):
        ds = copy.deepcopy(ds_orig)

        # Assign new series and instance identities
        ds.SeriesInstanceUID = series_uid
        sop_uid = generate_uid()
        ds.SOPInstanceUID = sop_uid
        if hasattr(ds, "file_meta") and ds.file_meta is not None:
            ds.file_meta.MediaStorageSOPInstanceUID = sop_uid

        # Mark as derived and update series description
        orig_desc = str(getattr(ds_orig, "SeriesDescription", "") or "")
        ds.SeriesDescription = (orig_desc + " [N4 Corrected]").strip()
        try:
            ds.SeriesNumber = int(getattr(ds_orig, "SeriesNumber", 0) or 0) + 900
        except (ValueError, TypeError):
            ds.SeriesNumber = 900
        ds.ImageType = ["DERIVED", "SECONDARY"]

        # Convert the corrected float32 slice back to the original stored integer dtype.
        # N4 operates on real-valued intensities (slope*pixel + intercept), so reversing
        # that transform gives the corrected stored value. We keep the same RescaleSlope
        # and RescaleIntercept, so readers see unchanged rescaling metadata.
        slope = float(getattr(ds_orig, "RescaleSlope", 1.0))
        intercept = float(getattr(ds_orig, "RescaleIntercept", 0.0))
        stored_float = (slice_float - intercept) / slope

        bits = int(getattr(ds_orig, "BitsAllocated", 16))
        pixel_rep = int(getattr(ds_orig, "PixelRepresentation", 0))  # 0=unsigned, 1=signed
        if pixel_rep == 0:
            dtype = np.uint16 if bits == 16 else np.uint8
            lo, hi = 0, 2 ** bits - 1
        else:
            dtype = np.int16 if bits == 16 else np.int8
            lo, hi = -(2 ** (bits - 1)), 2 ** (bits - 1) - 1
        ds.PixelData = np.clip(np.round(stored_float), lo, hi).astype(dtype).tobytes()

        pydicom.dcmwrite(str(out_dir / f"slice_{i:04d}.dcm"), ds)

    return out_dir


def upload_dicom_to_s3(dicom_dir: Path, bucket: str, s3_prefix: str, region: str | None = None) -> str:
    """
    Upload all .dcm files in dicom_dir to s3://bucket/s3_prefix/.

    Returns the S3 URI of the uploaded prefix (the value to pass as
    inputS3Uri to StartDICOMImportJob).
    """
    s3 = boto3.client("s3", **({"region_name": region} if region else {}))
    prefix = s3_prefix.rstrip("/")
    for dcm_path in sorted(Path(dicom_dir).glob("*.dcm")):
        s3.upload_file(str(dcm_path), bucket, f"{prefix}/{dcm_path.name}")
    return f"s3://{bucket}/{prefix}/"


def start_healthimaging_import(
    datastore_id: str,
    input_s3_uri: str,
    output_s3_uri: str,
    import_role_arn: str,
    region: str | None = None,
) -> str:
    """
    Trigger a HealthImaging DICOM import job and return the job ID.

    HealthImaging fetches the DICOM files from input_s3_uri using import_role_arn,
    converts them to HTJ2K, and stores them under the same Study Instance UID as
    the original series, keeping it associated with the same study in HealthImaging.
    """
    client = boto3.client("medical-imaging", **({"region_name": region} if region else {}))
    response = client.start_dicom_import_job(
        datastoreId=datastore_id,
        dataAccessRoleArn=import_role_arn,
        inputS3Uri=input_s3_uri,
        outputS3Uri=output_s3_uri,
        jobName=f"n4-corrected-{datetime.now().strftime('%Y%m%d-%H%M%S')}",
    )
    return response["jobId"]


# ---------------------------------------------------------------------------
# Slice extraction and PNG rendering
# ---------------------------------------------------------------------------

def extract_middle_slice(arr: np.ndarray) -> np.ndarray:
    """
    Extract a representative 2D axial slice from the array for the PDF report.

    ITK's GetArrayFromImage returns arrays in (z, y, x) order, so axis 0 is
    always the slice axis regardless of how many slices there are. Slicing on
    axis 0 gives the expected axial view for any volume.

    Size-1 dimensions (e.g. z=1 on single-slice inputs after cropping) are
    squeezed first so that a (1, 256, 224) array collapses to (256, 224) and
    is returned directly without trying to slice a degenerate z axis.
    """
    arr = arr.squeeze()
    if arr.ndim == 2:
        return arr
    return arr[arr.shape[0] // 2]


def normalize_to_uint8(arr: np.ndarray) -> np.ndarray:
    """Linearly scale arr to [0, 255]."""
    lo, hi = arr.min(), arr.max()
    if hi > lo:
        return ((arr - lo) / (hi - lo) * 255).astype(np.uint8)
    return np.zeros_like(arr, dtype=np.uint8)


def save_panel_png(arr: np.ndarray, out_path: Path) -> Path:
    """Save a 2D float array as a grayscale PNG."""
    img = Image.fromarray(normalize_to_uint8(arr), mode="L")
    img.save(out_path)
    return out_path


# ---------------------------------------------------------------------------
# PDF generation
# ---------------------------------------------------------------------------

BRAND_DARK = colors.HexColor("#1A2B4A")
BRAND_MID = colors.HexColor("#2E6DA4")
BRAND_LIGHT = colors.HexColor("#EAF2FB")
GREY = colors.HexColor("#6B7280")
DIVIDER = colors.HexColor("#CBD5E1")


def _fit_image(png_path: Path, max_w: float, max_h: float) -> RLImage:
    with Image.open(png_path) as pil:
        pw, ph = pil.size
    scale = min(max_w / pw, max_h / ph)
    rl = RLImage(str(png_path), width=pw * scale, height=ph * scale)
    rl.hAlign = "CENTER"
    return rl


def build_pdf(
    pdf_path: Path,
    orig_png: Path,
    corr_png: Path,
    bias_png: Path,
    meta: dict,
) -> None:
    page_w, page_h = A4
    margin = 18 * mm

    doc = SimpleDocTemplate(
        str(pdf_path),
        pagesize=A4,
        leftMargin=margin,
        rightMargin=margin,
        topMargin=15 * mm,
        bottomMargin=15 * mm,
    )

    title_style = ParagraphStyle(
        "ReportTitle", fontSize=22, leading=28,
        textColor=BRAND_DARK, fontName="Helvetica-Bold", alignment=TA_LEFT,
    )
    subtitle_style = ParagraphStyle(
        "Subtitle", fontSize=10, textColor=GREY,
        fontName="Helvetica", alignment=TA_LEFT, spaceAfter=2,
    )
    section_style = ParagraphStyle(
        "SectionHead", fontSize=11, textColor=BRAND_MID,
        fontName="Helvetica-Bold", spaceBefore=10, spaceAfter=4,
    )
    body_style = ParagraphStyle(
        "Body", fontSize=9, textColor=colors.HexColor("#374151"),
        fontName="Helvetica", leading=14,
    )
    caption_style = ParagraphStyle(
        "Caption", fontSize=8, textColor=GREY,
        fontName="Helvetica-Oblique", alignment=TA_CENTER,
    )
    footer_style = ParagraphStyle(
        "Footer", fontSize=7, textColor=GREY,
        fontName="Helvetica-Oblique", alignment=TA_CENTER,
    )

    story = []

    # Header
    story.append(Paragraph("N4 Bias Field Correction Report", title_style))
    story.append(Paragraph(
        f"Generated: {datetime.now().strftime('%d %B %Y, %H:%M')}",
        subtitle_style,
    ))
    story.append(HRFlowable(width="100%", thickness=2, color=BRAND_MID, spaceAfter=10))

    # Metadata table
    story.append(Paragraph("Image Information", section_style))

    label_map = {
        "filename": "File Name",
        "format": "Format",
        "dimensions": "Dimensions",
        "size": "Size (voxels)",
        "spacing_mm": "Spacing (mm)",
        "origin": "Origin",
        "pixel_type": "Pixel Type",
        "shrink_factor": "Shrink Factor",
        "fitting_levels": "Fitting Levels",
        "max_iterations": "Max Iterations / Level",
        "convergence_threshold": "Convergence Threshold",
    }

    rows = [
        [Paragraph(f"<b>{label}</b>", body_style), Paragraph(str(meta[key]), body_style)]
        for key, label in label_map.items()
        if key in meta
    ]

    col_w = page_w - 2 * margin
    tbl = Table(rows, colWidths=[col_w * 0.40, col_w * 0.60])
    tbl.setStyle(TableStyle([
        ("ROWBACKGROUNDS", (0, 0), (-1, -1), [colors.white, BRAND_LIGHT]),
        ("GRID", (0, 0), (-1, -1), 0.4, DIVIDER),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ]))
    story.append(tbl)

    # Three-panel image comparison
    story.append(Paragraph("Middle Slice Comparison", section_style))
    story.append(HRFlowable(width="100%", thickness=0.5, color=DIVIDER, spaceAfter=6))

    panel_w = (page_w - 2 * margin - 8 * mm) / 3   # 8 mm total gap between 3 panels
    panel_h = 70 * mm

    panels = [
        (_fit_image(orig_png, panel_w, panel_h), "Original"),
        (_fit_image(corr_png, panel_w, panel_h), "Corrected"),
        (_fit_image(bias_png, panel_w, panel_h), "Bias Field"),
    ]

    img_row = [[p for p, _ in panels]]
    cap_row = [[Paragraph(label, caption_style) for _, label in panels]]

    panel_col_w = [panel_w] * 3
    img_tbl = Table(img_row, colWidths=panel_col_w)
    img_tbl.setStyle(TableStyle([
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 2),
        ("RIGHTPADDING", (0, 0), (-1, -1), 2),
    ]))
    cap_tbl = Table(cap_row, colWidths=panel_col_w)
    cap_tbl.setStyle(TableStyle([
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
    ]))

    story.append(img_tbl)
    story.append(cap_tbl)

    # Footer
    story.append(Spacer(1, 6 * mm))
    story.append(HRFlowable(width="100%", thickness=0.5, color=DIVIDER))
    story.append(Spacer(1, 1 * mm))
    story.append(Paragraph(
        "This report is generated automatically for research and development purposes only. "
        "It does not constitute a clinical diagnosis.",
        footer_style,
    ))

    doc.build(story)


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Apply N4 bias field correction to an MRI volume and produce a PDF report."
    )

    parser.add_argument(
        "--input",
        metavar="PATH",
        help="Path to a local image file (for development/testing)",
    )
    parser.add_argument(
        "--s3-input-bucket",
        metavar="BUCKET",
        help="S3 bucket containing the original DICOM upload (overrides ORIGINAL_S3_BUCKET env var)",
    )
    parser.add_argument(
        "--s3-input-prefix",
        metavar="PREFIX",
        help="S3 key prefix of the original DICOM upload (overrides ORIGINAL_S3_PREFIX env var)",
    )
    parser.add_argument(
        "--output-dir", default="output", help="Directory for outputs (default: ./output)"
    )
    parser.add_argument(
        "--shrink-factor",
        type=int,
        default=2,
        metavar="N",
        help="Uniform shrink factor applied before N4 estimation (default: 2). "
             "Use 1 to run at full resolution (slower).",
    )
    parser.add_argument(
        "--s3-bucket",
        metavar="BUCKET",
        help="S3 bucket for output files and corrected DICOM staging (overrides OUTPUT_S3_BUCKET env var)",
    )
    parser.add_argument(
        "--s3-prefix",
        metavar="PREFIX",
        help="S3 key prefix for output files (overrides OUTPUT_S3_PREFIX env var)",
    )
    parser.add_argument(
        "--datastore-id",
        metavar="ID",
        help="HealthImaging datastore ID for reimporting the corrected series "
             "(overrides DATASTORE_ID env var)",
    )
    parser.add_argument(
        "--import-role-arn",
        metavar="ARN",
        help="IAM role ARN that HealthImaging assumes to read staged DICOM from S3 "
             "(overrides IMPORT_ROLE_ARN env var)",
    )
    args = parser.parse_args()

    # When running as an ECS Fargate task, configuration is passed as environment
    # variables. Fall back to env vars for any arg not supplied on the command line.
    s3_input_bucket = args.s3_input_bucket or os.environ.get("ORIGINAL_S3_BUCKET")
    s3_input_prefix = args.s3_input_prefix or os.environ.get("ORIGINAL_S3_PREFIX")
    if not args.datastore_id:
        args.datastore_id = os.environ.get("DATASTORE_ID")
    if not args.s3_bucket:
        args.s3_bucket = os.environ.get("OUTPUT_S3_BUCKET")
    if not args.s3_prefix:
        args.s3_prefix = os.environ.get("OUTPUT_S3_PREFIX")
    if not args.import_role_arn:
        args.import_role_arn = os.environ.get("IMPORT_ROLE_ARN")

    # Exactly one input source must be provided
    if not args.input and not (s3_input_bucket and s3_input_prefix):
        parser.error(
            "an input source is required: use --input for a local file, or set "
            "ORIGINAL_S3_BUCKET + ORIGINAL_S3_PREFIX (or --s3-input-bucket + --s3-input-prefix) "
            "for an S3 DICOM source"
        )
    if args.input and (s3_input_bucket or s3_input_prefix):
        parser.error("--input cannot be combined with S3 input arguments")

    # Output arguments are required when using S3 DICOM input (ECS path)
    if s3_input_bucket and s3_input_prefix:
        if not args.datastore_id:
            parser.error("DATASTORE_ID (or --datastore-id) is required with S3 DICOM input")
        if not args.s3_bucket:
            parser.error("OUTPUT_S3_BUCKET (or --s3-bucket) is required with S3 DICOM input")
        if not args.import_role_arn:
            parser.error("IMPORT_ROLE_ARN (or --import-role-arn) is required with S3 DICOM input")

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    # --- Load input image ---
    # S3 DICOM path (ECS production): download original .dcm files from the upload
    # location, read with pydicom, return ITK image + datasets for tag-preserving output.
    # Local path (development): ITK reads the file directly; no reimport after correction.
    if s3_input_bucket and s3_input_prefix:
        stem = Path(s3_input_prefix.rstrip("/")).name
        print(f"  Loading        : s3://{s3_input_bucket}/{s3_input_prefix}", flush=True)
        original, datasets = load_from_s3_dicom(s3_input_bucket, s3_input_prefix)
        print(f"  Loaded {len(datasets)} slices", flush=True)
        source_label = f"s3://{s3_input_bucket}/{s3_input_prefix}"
        source_format = "dicom (s3)"
    else:
        input_path = Path(args.input)
        if not input_path.exists():
            sys.exit(f"Error: file not found, {input_path}")
        stem = input_path.name.split(".")[0]
        print(f"  Loading        : {input_path}")
        original = load_as_float(input_path)
        datasets = None  # local files have no pydicom datasets, reimport not supported
        source_label = str(input_path)
        source_format = "".join(input_path.suffixes).lower()

    # Collect spatial metadata from the original image for the PDF report
    # These are read before any padding/shrinking so they reflect the true
    # input dimensions rather than the internally padded ones
    region = original.GetLargestPossibleRegion()
    size = region.GetSize()
    spacing = original.GetSpacing()
    origin = original.GetOrigin()
    ndim = original.GetImageDimension()

    meta = {
        "filename": source_label,
        "format": source_format,
        "dimensions": f"{ndim}D",
        "size": " x ".join(str(size[i]) for i in range(ndim)),
        "spacing_mm": "  ".join(f"{spacing[i]:.3f}" for i in range(ndim)),
        "origin": "  ".join(f"{origin[i]:.2f}" for i in range(ndim)),
        "pixel_type": "float32",
        "shrink_factor": str(args.shrink_factor),
        "fitting_levels": "4",
        "max_iterations": "50",
        "convergence_threshold": "0.001",
    }

    # --- Prepare image for N4 ---
    # Single-slice DICOMs (z=1) are padded in z by replicating the slice so
    # N4's B-spline lattice has sufficient depth. The image is then shrunk for
    # faster bias estimation, shrinking is skipped in any dimension that would
    # collapse below 4 voxels
    n4_input, was_padded = pad_single_slice(original, args.shrink_factor)

    print(f"[diag] pad_single_slice complete, was_padded={was_padded}", flush=True)
    if args.shrink_factor > 1:
        print(f"  Shrinking      : factor {args.shrink_factor}", flush=True)
        n4_image = shrink_image(n4_input, args.shrink_factor)
        print(f"[diag] shrink_image complete", flush=True)
    else:
        n4_image = n4_input

    # --- Run N4 bias field correction ---
    print("  Running N4     : fitting levels=4, iterations=[50,50,50,50]", flush=True)
    corrected_arr, bias_arr = run_n4(n4_image)
    print("[diag] run_n4 complete", flush=True)

    # --- Upsample and crop back to original dimensions ---
    # If the image was shrunk for estimation, bilinear upsample the corrected
    # and bias arrays back to n4_input resolution. Then if the image was z-padded
    # for single-slice support, take only the middle slice (index 1 of 3+ copies)
    # so the output matches the original input dimensions
    if args.shrink_factor > 1:
        target = itk.GetArrayFromImage(n4_input)
        corrected_arr = _upsample_arr(corrected_arr, target.shape)
        bias_arr = _upsample_arr(bias_arr, target.shape)

    if was_padded:
        corrected_arr = corrected_arr[1:2]
        bias_arr = bias_arr[1:2]

    original_arr = itk.GetArrayFromImage(original)

    # Extract the representative middle slice from each volume for the PDF panels.
    # For single-slice inputs the squeeze in extract_middle_slice collapses z=1
    # so the PNG is a proper 2D image rather than a 1-pixel-tall line
    orig_slice = extract_middle_slice(original_arr)
    corr_slice = extract_middle_slice(corrected_arr)
    bias_slice = extract_middle_slice(bias_arr)

    orig_png = save_panel_png(orig_slice, out_dir / f"{stem}_orig_slice.png")
    corr_png = save_panel_png(corr_slice, out_dir / f"{stem}_corr_slice.png")
    bias_png = save_panel_png(bias_slice, out_dir / f"{stem}_bias_slice.png")

    pdf_path = out_dir / f"{stem}_n4_report.pdf"
    print(f"  PDF report     : {pdf_path}")
    build_pdf(pdf_path, orig_png, corr_png, bias_png, meta)

    # --- Upload PDF report to S3 ---
    # When running as an ECS task the report is uploaded so the application can
    # generate a presigned download URL for the user. The key is fixed as
    # <s3_prefix>/report.pdf so the backend can derive it from the job ID alone,
    # without storing anything extra in the database.
    if args.s3_bucket and args.s3_prefix:
        s3 = boto3.client("s3")
        pdf_s3_key = args.s3_prefix.rstrip("/") + "/report.pdf"
        s3.upload_file(str(pdf_path), args.s3_bucket, pdf_s3_key)
        print(f"  PDF uploaded   : s3://{args.s3_bucket}/{pdf_s3_key}")

    # --- Write corrected DICOM, upload, and reimport into HealthImaging ---
    # Only runs when the source was an S3 DICOM upload (datasets are available).
    # Writes tag-preserving corrected .dcm files, stages them on S3, triggers a
    # HealthImaging import job, and writes output.json so the Spring app can
    # discover the import job ID after the ECS task stops and create a catalog entry.
    if datasets is not None:
        s3_prefix = args.s3_prefix or f"n4-corrected/{stem}"

        print("  Writing DICOM  : corrected series")
        dicom_dir = write_corrected_dicom_series(
            datasets, corrected_arr, out_dir / "corrected-dicom"
        )

        print(f"  Uploading      : s3://{args.s3_bucket}/{s3_prefix}/dicom/")
        input_s3_uri = upload_dicom_to_s3(
            dicom_dir, args.s3_bucket, f"{s3_prefix}/dicom"
        )
        output_s3_uri = f"s3://{args.s3_bucket}/{s3_prefix.rstrip('/')}/import-logs/"

        print("  Starting import: HealthImaging")
        job_id = start_healthimaging_import(
            args.datastore_id,
            input_s3_uri,
            output_s3_uri,
            args.import_role_arn,
        )
        print(f"  Import job ID  : {job_id}")

        # Write the import job ID to S3 so the Spring app can register the
        # corrected image set in the catalog after the ECS task stops
        s3_out = boto3.client("s3")
        output_key = s3_prefix.rstrip("/") + "/output.json"
        s3_out.put_object(
            Bucket=args.s3_bucket,
            Key=output_key,
            Body=json.dumps({"healthImagingImportJobId": job_id}),
            ContentType="application/json",
        )
        print(f"  Output metadata: s3://{args.s3_bucket}/{output_key}")

    print("\nDone.")
    print(f"  PDF report      : {pdf_path}")
    if datasets is not None:
        print(f"  Import job ID   : {job_id}")


if __name__ == "__main__":
    main()
