"""
pipeline.py - N4 Bias Field Correction Tool

Corrects B1 field inhomogeneity in MRI volumes using the ITK N4 algorithm.
Produces a PDF report with side-by-side comparison of the original, corrected,
and bias field middle slices. Optionally writes the corrected volume as a DICOM
series and reimports it into AWS HealthImaging under the same study as the source.

Usage (local file):
    n4-bias-correction --input <image_path> [--output-dir <dir>] [--shrink-factor <n>]

Usage (AWS HealthImaging):
    n4-bias-correction --datastore-id <id> --image-set-id <id> [--output-dir <dir>] [--shrink-factor <n>]

Usage (AWS HealthImaging with reimport):
    n4-bias-correction --datastore-id <id> --image-set-id <id> --reimport
                       --s3-bucket <bucket> --import-role-arn <arn>
                       [--output-dir <dir>] [--shrink-factor <n>]

Supported local input formats (anything itk can read):
    .nii, .nii.gz
    .dcm (single file or directory)
    .mha, .mhd, .nrrd, .nhdr
"""

import argparse
import gzip
import json
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
# HealthImaging loader
# ---------------------------------------------------------------------------

def _parse_healthimaging_metadata(raw_bytes: bytes) -> dict:
    """
    Decompress and parse the gzipped DICOM JSON blob returned by
    get_image_set_metadata().  Returns the top-level dict.
    """
    return json.loads(gzip.decompress(raw_bytes))


def _collect_frames(metadata: dict) -> list[dict]:
    """
    Walk the Study → Series → Instances hierarchy and return a flat list of
    frame descriptors, each containing:
        frame_id       : str  , HealthImaging frame ID
        instance_number: int  , DICOM Instance Number (used for slice ordering)
        image_position : list , Image Position Patient [x, y, z]
        image_orientation: list, Image Orientation Patient (6 floats)
        pixel_spacing  : list , [row_spacing, col_spacing] in mm
        slice_thickness: float, in mm (may be None)
    """
    frames = []
    for series in metadata.get("Study", {}).get("Series", {}).values():
        for instance in series.get("Instances", {}).values():
            dicom = instance.get("DICOM", {})

            def get_val(tag, default=None):
                entry = dicom.get(tag, {})
                vals = entry.get("Value")
                if not vals:
                    return default
                return vals if len(vals) > 1 else vals[0]

            instance_number = int(get_val("00200013") or 0)
            image_position = [float(v) for v in (get_val("00200032") or [0, 0, 0])]
            image_orientation = [float(v) for v in (get_val("00200037") or [1, 0, 0, 0, 1, 0])]
            pixel_spacing_raw = get_val("00280030") or [1.0, 1.0]
            pixel_spacing = [float(v) for v in pixel_spacing_raw]
            slice_thickness_raw = get_val("00180050")
            slice_thickness = float(slice_thickness_raw) if slice_thickness_raw is not None else None

            for frame_info in instance.get("ImageFrames", []):
                frames.append({
                    "frame_id": frame_info["ID"],
                    "instance_number": instance_number,
                    "image_position": image_position,
                    "image_orientation": image_orientation,
                    "pixel_spacing": pixel_spacing,
                    "slice_thickness": slice_thickness,
                    # Full DICOM JSON for this instance, used when writing the
                    # corrected series back to DICOM for HealthImaging reimport
                    "raw_dicom": dicom,
                })

    # Sort by z-position (Image Position Patient[2]) then instance number
    frames.sort(key=lambda f: (f["image_position"][2], f["instance_number"]))
    return frames


def _build_direction_matrix(image_orientation: list[float]) -> np.ndarray:
    """
    Construct a 3×3 ITK direction matrix from the 6-element
    Image Orientation Patient vector (row cosines + col cosines).
    The slice normal is the cross product of the two.
    """
    row = np.array(image_orientation[:3])
    col = np.array(image_orientation[3:])
    normal = np.cross(row, col)
    # ITK direction columns are: row-direction, col-direction, slice-direction
    mat = np.column_stack([row, col, normal])
    return mat


def load_from_healthimaging(
    datastore_id: str,
    image_set_id: str,
    region: str | None = None,
) -> itk.Image:
    """
    Fetch every HTJ2K frame for an ImageSet from AWS HealthImaging, decode
    each frame with ITKIOOpenJPH, stack the 2D slices into a 3D float32 ITK
    image with correct spatial metadata, and return it ready for N4.

    IAM permissions required:
        medical-imaging:GetImageSetMetadata
        medical-imaging:GetImageFrame
    """
    # itk_ioopenjph registers the HTJ2K (High-Throughput JPEG 2000) IO factory
    # with ITK so itk.imread can decode .jph files. Imported here rather than
    # at module level so the local-file path doesn't require this dependency
    import itk_ioopenjph  # noqa: F401, registers HTJ2K IO factory with ITK; only needed for HealthImaging path

    client_kwargs = {"service_name": "medical-imaging"}
    if region:
        client_kwargs["region_name"] = region
    client = boto3.client(**client_kwargs)

    # 1. Retrieve and parse image-set metadata
    meta_response = client.get_image_set_metadata(
        datastoreId=datastore_id,
        imageSetId=image_set_id,
    )
    raw_meta = meta_response["imageSetMetadataBlob"].read()
    metadata = _parse_healthimaging_metadata(raw_meta)

    # 2. Build an ordered list of frame descriptors
    frame_descriptors = _collect_frames(metadata)
    if not frame_descriptors:
        raise ValueError(
            f"No frames found in ImageSet {image_set_id} "
            f"(datastore {datastore_id})"
        )

    # 3. Decode each HTJ2K frame into a 2D numpy array
    # HealthImaging stores frames in HTJ2K format. Each frame is fetched as raw
    # bytes, written to a temp .jph file, and decoded by ITKIOOpenJPH into a 2D
    # float32 ITK image. Using a temp directory ensures cleanup even on failure
    slice_arrays: list[np.ndarray] = []
    with tempfile.TemporaryDirectory() as tmp_dir:
        for desc in frame_descriptors:
            frame_response = client.get_image_frame(
                datastoreId=datastore_id,
                imageSetId=image_set_id,
                imageFrameInformation={"imageFrameId": desc["frame_id"]},
            )
            htj2k_bytes = frame_response["imageFrameBlob"].read()

            tmp_path = Path(tmp_dir) / f"{desc['frame_id']}.jph"
            tmp_path.write_bytes(htj2k_bytes)

            frame_img = itk.imread(str(tmp_path), itk.F)
            slice_arrays.append(itk.GetArrayFromImage(frame_img))

    # 4. Stack the ordered 2D slices into a single (z, y, x) numpy volume,
    # then wrap it as a 3D ITK image
    volume_arr = np.stack(slice_arrays, axis=0).astype(np.float32)
    image_3d = itk.GetImageFromArray(volume_arr)

    # 5. Apply correct spatial metadata from the first frame's DICOM tags
    # Slice spacing is computed as the Euclidean distance between consecutive
    # Image Position Patient vectors (more reliable than Slice Thickness for
    # non-contiguous acquisitions). Falls back to Slice Thickness for single-
    # frame image sets
    first = frame_descriptors[0]
    orientation = first["image_orientation"]
    pixel_spacing = first["pixel_spacing"]

    if len(frame_descriptors) > 1:
        z0 = np.array(frame_descriptors[0]["image_position"])
        z1 = np.array(frame_descriptors[1]["image_position"])
        slice_spacing = float(np.linalg.norm(z1 - z0))
    else:
        slice_spacing = first["slice_thickness"] or 1.0

    # ITK spacing order is (x, y, z), pixel_spacing from DICOM is [row(y), col(x)]
    image_3d.SetSpacing([pixel_spacing[1], pixel_spacing[0], slice_spacing])
    image_3d.SetOrigin(first["image_position"])

    direction = _build_direction_matrix(orientation)
    itk_direction = itk.Matrix[itk.D, 3, 3]()
    for r in range(3):
        for c in range(3):
            itk_direction(r, c, direction[r, c])
    image_3d.SetDirection(itk_direction)

    return image_3d, frame_descriptors


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
    Upsample a 3D numpy array to target_shape using per-slice bilinear resize.

    Used to bring the corrected and bias arrays (computed on the shrunken image)
    back to the full n4_input resolution before cropping and saving. Each z-slice
    is resized independently in xy using Pillow's BILINEAR interpolation. If the
    shrunken z count is less than the target z count (can happen when the shrink
    factor doesn't divide evenly), slices are tiled to fill the gap.
    """
    from PIL import Image as PILImage
    tz, ty, tx = target_shape
    # Resize each z-slice in xy
    xy_resized = np.stack([
        np.array(PILImage.fromarray(arr[z]).resize((tx, ty), PILImage.BILINEAR))
        for z in range(arr.shape[0])
    ], axis=0)
    # Tile then crop z to match target depth
    if xy_resized.shape[0] < tz:
        repeats = int(np.ceil(tz / xy_resized.shape[0]))
        xy_resized = np.tile(xy_resized, (repeats, 1, 1))
    return xy_resized[:tz].astype(np.float32)


# ---------------------------------------------------------------------------
# DICOM reimport pipeline (HealthImaging round-trip)
# ---------------------------------------------------------------------------

def _get_tag(raw_dicom: dict, tag: str, default=None):
    """
    Extract a scalar value from a DICOM JSON tag dict (HealthImaging format).

    DICOM JSON stores each tag as {"vr": "...", "Value": [...]}.
    PersonName VR entries are {"Alphabetic": "..."} dicts, unwrap those too.
    Returns the first Value element, or default if absent.
    """
    entry = raw_dicom.get(tag, {})
    vals = entry.get("Value")
    if not vals:
        return default
    v = vals[0]
    if isinstance(v, dict) and "Alphabetic" in v:
        return v["Alphabetic"]
    return v


def write_corrected_dicom_series(
    corrected_arr: np.ndarray,
    frame_descriptors: list[dict],
    out_dir: Path,
) -> Path:
    """
    Write the corrected float32 volume as a DICOM series ready for HealthImaging reimport.

    Each z-slice becomes one .dcm file. The output series shares the original
    Study Instance UID (so HealthImaging and the OHIF viewer associate it with
    the original upload) but gets a new Series Instance UID and new SOP Instance
    UIDs, and is tagged as DERIVED\\SECONDARY.

    Float32 pixel values are linearly scaled to uint16 using global min/max.
    RescaleSlope and RescaleIntercept are written so readers reconstruct the
    original float intensities.

    Returns the output directory containing the .dcm files.
    """
    import pydicom
    from pydicom.dataset import Dataset, FileDataset, FileMetaDataset
    from pydicom.uid import generate_uid, ExplicitVRLittleEndian

    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    # Scale float32 → uint16 once across the whole volume for consistency
    lo, hi = float(corrected_arr.min()), float(corrected_arr.max())
    if hi > lo:
        slope = (hi - lo) / 65535.0
        intercept = lo
        pixel_volume = np.round((corrected_arr - intercept) / slope).astype(np.uint16)
    else:
        slope, intercept = 1.0, 0.0
        pixel_volume = np.zeros_like(corrected_arr, dtype=np.uint16)

    series_uid = generate_uid()

    for i, (slice_2d, desc) in enumerate(zip(pixel_volume, frame_descriptors)):
        raw = desc["raw_dicom"]
        sop_uid = generate_uid()

        file_meta = FileMetaDataset()
        file_meta.MediaStorageSOPClassUID = "1.2.840.10008.5.1.4.1.1.4"  # MR Image Storage
        file_meta.MediaStorageSOPInstanceUID = sop_uid
        file_meta.TransferSyntaxUID = ExplicitVRLittleEndian

        ds = FileDataset(None, {}, file_meta=file_meta, preamble=b"\x00" * 128)
        ds.is_implicit_VR = False
        ds.is_little_endian = True

        # Patient tags, copied from original
        ds.PatientName = _get_tag(raw, "00100010", "")
        ds.PatientID = _get_tag(raw, "00100020", "")
        ds.PatientBirthDate = _get_tag(raw, "00100030", "")
        ds.PatientSex = _get_tag(raw, "00100040", "")

        # Study tags, copied from original (same Study UID links series in OHIF)
        ds.StudyInstanceUID = _get_tag(raw, "0020000D", "")
        ds.StudyDate = _get_tag(raw, "00080020", "")
        ds.StudyTime = _get_tag(raw, "00080030", "")
        ds.AccessionNumber = _get_tag(raw, "00080050", "")
        ds.StudyID = _get_tag(raw, "00200010", "")
        ds.StudyDescription = _get_tag(raw, "00081030", "")

        # Series tags, new series derived from the original
        ds.Modality = _get_tag(raw, "00080060", "MR")
        ds.SeriesInstanceUID = series_uid
        ds.SeriesNumber = "900"  # high number to sort after the source series
        ds.SeriesDescription = "N4 Bias Field Corrected"

        # Instance tags
        ds.SOPClassUID = "1.2.840.10008.5.1.4.1.1.4"
        ds.SOPInstanceUID = sop_uid
        ds.InstanceNumber = str(i + 1)
        ds.ImageType = ["DERIVED", "SECONDARY"]

        # Spatial metadata from original frame
        ds.Rows = slice_2d.shape[0]
        ds.Columns = slice_2d.shape[1]
        ds.PixelSpacing = [str(v) for v in desc["pixel_spacing"]]
        ds.ImagePositionPatient = [str(v) for v in desc["image_position"]]
        ds.ImageOrientationPatient = [str(v) for v in desc["image_orientation"]]
        if desc["slice_thickness"] is not None:
            ds.SliceThickness = str(desc["slice_thickness"])

        # Pixel data
        ds.SamplesPerPixel = 1
        ds.PhotometricInterpretation = "MONOCHROME2"
        ds.BitsAllocated = 16
        ds.BitsStored = 16
        ds.HighBit = 15
        ds.PixelRepresentation = 0  # unsigned
        ds.RescaleSlope = str(slope)
        ds.RescaleIntercept = str(intercept)
        ds.PixelData = slice_2d.tobytes()

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
    the original series, making them visible alongside the original in the OHIF viewer.
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
    Extract a representative 2D slice from the array for the PDF report.

    Size-1 dimensions (e.g. z=1 on single-slice inputs after cropping) are
    squeezed first so the axis selection isn't dominated by them, without this,
    np.argmax would pick the y axis on a (1, 256, 224) array and return a
    1-pixel-tall image that renders as a line.

    For genuinely 3D volumes the middle slice is taken along the largest axis,
    which is typically z for standard axial acquisitions.
    """
    arr = arr.squeeze()
    if arr.ndim == 2:
        return arr
    axis = int(np.argmax(arr.shape))
    return np.take(arr, arr.shape[axis] // 2, axis=axis)


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

    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--input", metavar="PATH", help="Path to a local image file")
    source.add_argument(
        "--image-set-id",
        metavar="ID",
        help="AWS HealthImaging ImageSet ID (requires --datastore-id)",
    )

    parser.add_argument(
        "--datastore-id",
        metavar="ID",
        help="AWS HealthImaging datastore ID (required with --image-set-id)",
    )
    parser.add_argument(
        "--region",
        metavar="REGION",
        help="AWS region for HealthImaging (default: uses boto3 session default)",
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
        "--reimport",
        action="store_true",
        help="After correction, write a DICOM series and reimport it into HealthImaging "
             "under the same study as the source image set. Requires --image-set-id, "
             "--s3-bucket, and --import-role-arn.",
    )
    parser.add_argument(
        "--s3-bucket",
        metavar="BUCKET",
        help="S3 bucket used to stage the corrected DICOM files before reimport.",
    )
    parser.add_argument(
        "--s3-prefix",
        metavar="PREFIX",
        help="S3 key prefix for staged DICOM files (default: n4-corrected/<image-set-id>).",
    )
    parser.add_argument(
        "--import-role-arn",
        metavar="ARN",
        help="IAM role ARN that HealthImaging assumes to read the staged DICOM from S3.",
    )
    args = parser.parse_args()

    if args.image_set_id and not args.datastore_id:
        parser.error("--datastore-id is required when using --image-set-id")
    if args.reimport:
        if not args.image_set_id:
            parser.error("--reimport requires --image-set-id (HealthImaging source)")
        if not args.s3_bucket:
            parser.error("--reimport requires --s3-bucket")
        if not args.import_role_arn:
            parser.error("--reimport requires --import-role-arn")

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    # --- Load input image ---
    # Local path: ITK reads directly (DICOM directory, NIfTI, NRRD, etc.)
    # HealthImaging: fetch HTJ2K frames via boto3, decode with ITKIOOpenJPH,
    # and assemble into a 3D volume with correct DICOM spatial metadata.
    if args.input:
        input_path = Path(args.input)
        if not input_path.exists():
            sys.exit(f"Error: file not found,{input_path}")
        stem = input_path.name.split(".")[0]
        print(f"  Loading        : {input_path}")
        original = load_as_float(input_path)
        frame_descriptors = None  # not available for local files; reimport not supported
        source_label = str(input_path)
        source_format = "".join(input_path.suffixes).lower()
    else:
        stem = args.image_set_id
        print(f"  Loading        : HealthImaging {args.datastore_id}/{args.image_set_id}")
        original, frame_descriptors = load_from_healthimaging(
            args.datastore_id, args.image_set_id, args.region
        )
        source_label = f"healthimaging://{args.datastore_id}/{args.image_set_id}"
        source_format = "htj2k (healthimaging)"

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

    if args.shrink_factor > 1:
        print(f"  Shrinking      : factor {args.shrink_factor}")
        n4_image = shrink_image(n4_input, args.shrink_factor)
    else:
        n4_image = n4_input

    # --- Run N4 bias field correction ---
    print("  Running N4     : fitting levels=4, iterations=[50,50,50,50]")
    corrected_arr, bias_arr = run_n4(n4_image)

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

    # --- Reimport corrected series into HealthImaging (optional) ---
    # Converts the corrected float32 volume to a DICOM series that shares the
    # original Study Instance UID, stages it on S3, and triggers a HealthImaging
    # import job. The corrected series will appear alongside the original in the
    # OHIF viewer once the job completes.
    if args.reimport:
        s3_prefix = args.s3_prefix or f"n4-corrected/{stem}"

        print("  Writing DICOM  : corrected series")
        dicom_dir = write_corrected_dicom_series(
            corrected_arr, frame_descriptors, out_dir / "corrected-dicom"
        )

        print(f"  Uploading      : s3://{args.s3_bucket}/{s3_prefix}/dicom/")
        input_s3_uri = upload_dicom_to_s3(
            dicom_dir, args.s3_bucket, f"{s3_prefix}/dicom", args.region
        )
        output_s3_uri = f"s3://{args.s3_bucket}/{s3_prefix.rstrip('/')}/import-logs/"

        print("  Starting import: HealthImaging")
        job_id = start_healthimaging_import(
            args.datastore_id,
            input_s3_uri,
            output_s3_uri,
            args.import_role_arn,
            args.region,
        )
        print(f"  Import job ID  : {job_id}")

    print("\nDone.")
    print(f"  PDF report      : {pdf_path}")
    if args.reimport:
        print(f"  Import job ID   : {job_id}")


if __name__ == "__main__":
    main()
