"""
pipeline.py - Otsu Threshold Tool

Segments MRI volumes using Otsu's method, which finds the intensity threshold
that best separates background from tissue by minimizing within-class variance.

This tool is intended to run after N4 bias field correction for Run Tool Workflow.
N4 flattens the intensity field so tissue voxels cluster tightly in the histogram,
giving Otsu a clean bimodal distribution to split. Running Otsu on an uncorrected image
often produces a skewed threshold because the bias field smears tissue
intensities across a wide range.

Produces two output DICOM series imported into HealthImaging:
    1. Masked intensity image: original pixel values where mask=1 (foreground),
       zeroed where mask=0 (background). This is a valid MRI intensity image and
       can be passed to further tools.
    2. Binary mask: pure 0/255 image showing the segmentation boundary directly.
       Useful for reviewing the segmentation result in the DICOM viewer.

Produces a PDF report showing the original middle slice, masked intensity slice,
binary mask slice, and an intensity histogram with the Otsu threshold marked.

Intended workflow for Run Tool Workflow:
    N4 Bias Field Correction -> Otsu Threshold (on the N4-corrected image set)

Usage (ECS / S3 source, via environment variables):
    ORIGINAL_S3_BUCKET=<bucket> ORIGINAL_S3_PREFIX=<prefix>
    OUTPUT_S3_BUCKET=<bucket>   OUTPUT_S3_PREFIX=<prefix>
    IMPORT_ROLE_ARN=<arn>
    otsu-threshold

Usage (local file, for development -- PDF report only, no DICOM output):
    otsu-threshold --input <image_path> [--output-dir <dir>]

Supported local input formats (anything itk can read):
    .nii, .nii.gz
    .dcm (single file or directory)
    .mha, .mhd, .nrrd, .nhdr
"""

import argparse
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
# Image loading
# ---------------------------------------------------------------------------

def load_as_float(path: Path) -> itk.Image:
    # If path is a directory, assume it's a DICOM series and use the series reader
    # itk.imread can't handle directories directly, it needs the sorted file list
    if Path(path).is_dir():
        names_gen = itk.GDCMSeriesFileNames.New()
        names_gen.SetDirectory(str(path))
        file_names = names_gen.GetInputFileNames()
        if not file_names:
            raise ValueError(f"No DICOM series found in directory: {path}")
        ImageType = itk.Image[itk.F, 3]
        reader = itk.ImageSeriesReader[ImageType].New()
        reader.SetImageIO(itk.GDCMImageIO.New())
        reader.SetFileNames(file_names)
        reader.Update()
        return reader.GetOutput()
    return itk.imread(str(path), itk.F)


def _build_direction_matrix(image_orientation: list[float]) -> np.ndarray:
    # The 6-element tag is just row and column cosines. We need the third
    # column (slice normal) too, which is just the cross product of the two
    # ITK wants columns in [row, col, normal] order
    row = np.array(image_orientation[:3])
    col = np.array(image_orientation[3:])
    normal = np.cross(row, col)
    return np.column_stack([row, col, normal])


def _get_tag(raw: dict, tag: str, default=None):
    # Pull the first value from a DICOM JSON tag dict
    # Handles the PN (person name) nested dict format too
    entry = raw.get(tag)
    if not entry:
        return default
    values = entry.get("Value", [])
    if not values:
        return default
    val = values[0]
    if isinstance(val, dict) and "Alphabetic" in val:
        return val["Alphabetic"]
    return val


def _ds_to_raw_dicom(ds) -> dict:
    # Convert a pydicom Dataset to the DICOM JSON format we use in frame_descriptors
    # We skip private tags and sequences since we only need the standard module tags
    import pydicom
    raw = {}
    for elem in ds:
        if elem.tag.is_private or elem.VR == "SQ":
            continue
        tag_str = f"{elem.tag.group:04X}{elem.tag.element:04X}"
        val = elem.value
        if elem.VR == "PN":
            values = [{"Alphabetic": str(val)}]
        elif isinstance(val, bytes):
            continue  # skip raw binary blobs like PixelData
        elif hasattr(val, "__iter__") and not isinstance(val, str):
            values = [str(v) if isinstance(v, pydicom.uid.UID) else v for v in val]
        else:
            values = [str(val) if isinstance(val, pydicom.uid.UID) else val]
        raw[tag_str] = {"vr": elem.VR, "Value": values}
    return raw


def load_from_s3_dicom(bucket: str, prefix: str) -> tuple:
    # Download all the .dcm files from S3, sort them by slice position, stack
    # into a 3D float32 ITK image, and return it along with frame_descriptors
    # so write_masked_dicom_series can copy the tags without holding pydicom objects
    # RescaleSlope/Intercept applied so Otsu sees real-valued intensities
    import pydicom

    s3 = boto3.client("s3")
    prefix_norm = prefix.rstrip("/") + "/"

    paginator = s3.get_paginator("list_objects_v2")
    dcm_keys = []
    for page in paginator.paginate(Bucket=bucket, Prefix=prefix_norm):
        for obj in page.get("Contents", []):
            if obj["Key"].lower().endswith(".dcm"):
                dcm_keys.append(obj["Key"])

    if not dcm_keys:
        raise ValueError(f"No .dcm files found at s3://{bucket}/{prefix_norm}")

    print(f"  Found {len(dcm_keys)} DICOM files at s3://{bucket}/{prefix_norm}", flush=True)

    datasets = []
    with tempfile.TemporaryDirectory() as tmp:
        for key in dcm_keys:
            local_path = Path(tmp) / Path(key).name
            s3.download_file(bucket, key, str(local_path))
            datasets.append(pydicom.dcmread(str(local_path)))

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

    pixel_arrays = []
    for ds in datasets:
        arr = ds.pixel_array.astype(np.float32)
        slope = float(getattr(ds, "RescaleSlope", 1.0))
        intercept = float(getattr(ds, "RescaleIntercept", 0.0))
        pixel_arrays.append(arr * slope + intercept)

    volume = np.stack(pixel_arrays, axis=0)
    image_3d = itk.GetImageFromArray(volume)

    first = datasets[0]
    pixel_spacing = [float(v) for v in getattr(first, "PixelSpacing", [1.0, 1.0])]
    image_position = [float(v) for v in getattr(first, "ImagePositionPatient", [0.0, 0.0, 0.0])]
    image_orientation = [float(v) for v in getattr(first, "ImageOrientationPatient", [1, 0, 0, 0, 1, 0])]

    if len(datasets) > 1:
        pos0 = np.array([float(v) for v in getattr(datasets[0], "ImagePositionPatient", [0, 0, 0])])
        pos1 = np.array([float(v) for v in getattr(datasets[1], "ImagePositionPatient", [0, 0, 0])])
        slice_spacing = float(np.linalg.norm(pos1 - pos0))
        if slice_spacing == 0.0:
            t = getattr(first, "SliceThickness", None)
            slice_spacing = float(t) if t is not None else 1.0
    else:
        t = getattr(first, "SliceThickness", None)
        slice_spacing = float(t) if t is not None else 1.0

    # ITK spacing is (x, y, z), DICOM PixelSpacing is [row=y, col=x]
    image_3d.SetSpacing([pixel_spacing[1], pixel_spacing[0], slice_spacing])
    image_3d.SetOrigin(image_position)
    direction = _build_direction_matrix(image_orientation)
    itk_direction = itk.matrix_from_array(direction.astype(np.float64))
    image_3d.SetDirection(itk_direction)

    frame_descriptors = []
    for i, ds in enumerate(datasets):
        image_pos = [float(v) for v in getattr(ds, "ImagePositionPatient", [0.0, 0.0, float(i)])]
        image_ori = [float(v) for v in getattr(ds, "ImageOrientationPatient", [1, 0, 0, 0, 1, 0])]
        pix_sp = [float(v) for v in getattr(ds, "PixelSpacing", [1.0, 1.0])]
        thickness = getattr(ds, "SliceThickness", None)
        frame_descriptors.append({
            "frame_id": f"frame-{i:04d}",
            "instance_number": int(getattr(ds, "InstanceNumber", i + 1)),
            "image_position": image_pos,
            "image_orientation": image_ori,
            "pixel_spacing": pix_sp,
            "slice_thickness": float(thickness) if thickness is not None else None,
            "raw_dicom": _ds_to_raw_dicom(ds),
        })

    return image_3d, frame_descriptors


# ---------------------------------------------------------------------------
# Otsu thresholding
# ---------------------------------------------------------------------------

def compute_otsu_threshold(image: itk.Image) -> float:
    # OtsuThresholdImageFilter needs explicit input and output types
    # The output is a binary mask (UC = unsigned char), but we throw it away
    # and only keep the threshold value
    ImageType = type(image)
    ndim = image.GetImageDimension()
    OutputType = itk.Image[itk.UC, ndim]
    otsu = itk.OtsuThresholdImageFilter[ImageType, OutputType].New(Input=image)
    otsu.Update()
    return float(otsu.GetThreshold())


def apply_otsu_mask(image: itk.Image, threshold: float) -> np.ndarray:
    # Keep original intensity values for foreground voxels (above threshold)
    # and zero everything else. This so N4 can run on the output
    arr = itk.GetArrayFromImage(image).astype(np.float32)
    return np.where(arr > threshold, arr, 0.0).astype(np.float32)


# ---------------------------------------------------------------------------
# DICOM output pipeline
# ---------------------------------------------------------------------------

def _build_dataset_from_frame(fd: dict, sop_uid: str, series_uid: str):
    # Build a minimal but valid pydicom FileDataset from a frame_descriptor
    # Pulls patient/study tags from raw_dicom and spatial metadata from the
    # frame_descriptor fields. The caller sets the derived/pixel fields
    import pydicom
    from pydicom.dataset import FileDataset, FileMetaDataset
    from pydicom.uid import ExplicitVRLittleEndian, generate_uid

    raw = fd["raw_dicom"]

    file_meta = FileMetaDataset()
    file_meta.MediaStorageSOPClassUID = _get_tag(raw, "00080016") or "1.2.840.10008.5.1.4.1.1.4"
    file_meta.MediaStorageSOPInstanceUID = sop_uid
    file_meta.TransferSyntaxUID = ExplicitVRLittleEndian

    ds = FileDataset("", {}, file_meta=file_meta, preamble=b"\x00" * 128)
    ds.is_implicit_VR = False
    ds.is_little_endian = True

    ds.PatientName = _get_tag(raw, "00100010") or "Unknown"
    ds.PatientID = str(_get_tag(raw, "00100020") or "")
    ds.PatientBirthDate = str(_get_tag(raw, "00100030") or "")
    ds.PatientSex = str(_get_tag(raw, "00100040") or "")

    ds.StudyInstanceUID = str(_get_tag(raw, "0020000D") or generate_uid())
    ds.StudyDate = str(_get_tag(raw, "00080020") or "")
    ds.StudyTime = str(_get_tag(raw, "00080030") or "")
    ds.AccessionNumber = str(_get_tag(raw, "00080050") or "")
    ds.StudyID = str(_get_tag(raw, "00200010") or "")
    ds.StudyDescription = str(_get_tag(raw, "00081030") or "")

    ds.Modality = str(_get_tag(raw, "00080060") or "OT")
    ds.SeriesInstanceUID = series_uid
    ds.SOPClassUID = file_meta.MediaStorageSOPClassUID
    ds.SOPInstanceUID = sop_uid
    ds.SpecificCharacterSet = "ISO_IR 6"

    ds.ImagePositionPatient = [str(v) for v in fd["image_position"]]
    ds.ImageOrientationPatient = [str(v) for v in fd["image_orientation"]]
    ds.PixelSpacing = [str(v) for v in fd["pixel_spacing"]]
    if fd.get("slice_thickness") is not None:
        ds.SliceThickness = str(fd["slice_thickness"])
    ds.InstanceNumber = str(fd.get("instance_number", 1))
    ds.FrameOfReferenceUID = str(_get_tag(raw, "00200052") or generate_uid())

    return ds


def write_masked_dicom_series(
    masked_arr: np.ndarray,
    frame_descriptors: list,
    out_dir: Path,
) -> Path:
    # Build one DICOM file per slice from the frame_descriptor metadata and
    # the normalized uint8 pixel data. StudyInstanceUID is preserved so
    # HealthImaging keeps this series in the same study
    import pydicom
    from pydicom.uid import generate_uid

    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    series_uid = generate_uid()

    for i, (slice_float, fd) in enumerate(zip(masked_arr, frame_descriptors)):
        sop_uid = generate_uid()
        ds = _build_dataset_from_frame(fd, sop_uid, series_uid)

        raw = fd["raw_dicom"]
        orig_desc = str(_get_tag(raw, "0008103E") or "")
        ds.SeriesDescription = (orig_desc + " [Otsu Mask]").strip()
        try:
            orig_series_num = int(_get_tag(raw, "00200011") or 0)
        except (ValueError, TypeError):
            orig_series_num = 0
        ds.SeriesNumber = str(orig_series_num + 900)
        ds.ImageType = ["DERIVED", "SECONDARY"]

        pixel_data = normalize_to_uint8(slice_float)
        ds.BitsAllocated = 8
        ds.BitsStored = 8
        ds.HighBit = 7
        ds.PixelRepresentation = 0
        ds.SamplesPerPixel = 1
        ds.PhotometricInterpretation = "MONOCHROME2"
        ds.Rows = pixel_data.shape[0]
        ds.Columns = pixel_data.shape[1]
        ds.PixelData = pixel_data.tobytes()

        pydicom.dcmwrite(str(out_dir / f"slice_{i:04d}.dcm"), ds)

    return out_dir


def write_binary_mask_dicom_series(
    original_arr: np.ndarray,
    threshold: float,
    frame_descriptors: list,
    out_dir: Path,
) -> Path:
    # Same structure as write_masked_dicom_series but stores a pure binary mask:
    # 255 where voxel > threshold, 0 everywhere else. This is what the Otsu filter
    # would normally produce as its primary output, and is useful for viewing the
    # segmentation result directly in the DICOM viewer
    # SeriesNumber offset is +901 to distinguish from the masked intensity series at +900
    import pydicom
    from pydicom.uid import generate_uid

    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    series_uid = generate_uid()

    for i, (slice_arr, fd) in enumerate(zip(original_arr, frame_descriptors)):
        sop_uid = generate_uid()
        ds = _build_dataset_from_frame(fd, sop_uid, series_uid)

        raw = fd["raw_dicom"]
        orig_desc = str(_get_tag(raw, "0008103E") or "")
        ds.SeriesDescription = (orig_desc + " [Otsu Binary Mask]").strip()
        try:
            orig_series_num = int(_get_tag(raw, "00200011") or 0)
        except (ValueError, TypeError):
            orig_series_num = 0
        ds.SeriesNumber = str(orig_series_num + 901)
        ds.ImageType = ["DERIVED", "SECONDARY"]

        pixel_data = (slice_arr > threshold).astype(np.uint8) * 255
        ds.BitsAllocated = 8
        ds.BitsStored = 8
        ds.HighBit = 7
        ds.PixelRepresentation = 0
        ds.SamplesPerPixel = 1
        ds.PhotometricInterpretation = "MONOCHROME2"
        ds.Rows = pixel_data.shape[0]
        ds.Columns = pixel_data.shape[1]
        ds.PixelData = pixel_data.tobytes()

        pydicom.dcmwrite(str(out_dir / f"slice_{i:04d}.dcm"), ds)

    return out_dir


def upload_dicom_to_s3(dicom_dir: Path, bucket: str, s3_prefix: str, region: str | None = None) -> str:
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
    client = boto3.client("medical-imaging", **({"region_name": region} if region else {}))
    response = client.start_dicom_import_job(
        datastoreId=datastore_id,
        dataAccessRoleArn=import_role_arn,
        inputS3Uri=input_s3_uri,
        outputS3Uri=output_s3_uri,
        jobName=f"otsu-threshold-{datetime.now().strftime('%Y%m%d-%H%M%S')}",
    )
    return response["jobId"]


# ---------------------------------------------------------------------------
# Slice extraction and PNG rendering
# ---------------------------------------------------------------------------

def extract_middle_slice(arr: np.ndarray) -> np.ndarray:
    # ITK gives us (z, y, x) so axis 0 is always slices. Squeeze first so
    # single-slice inputs (z=1) collapse to 2D instead of returning a
    # (1, H, W) array that would render as a 1-pixel-tall line (previous error
    # from N4 output)
    arr = arr.squeeze()
    if arr.ndim == 2:
        return arr
    return arr[arr.shape[0] // 2]


def normalize_to_uint8(arr: np.ndarray) -> np.ndarray:
    # Scale to [0, 255]. If every value is the same (e.g. all-zero background
    # slice) just return zeros to avoid dividing by zero
    lo, hi = arr.min(), arr.max()
    if hi > lo:
        return ((arr - lo) / (hi - lo) * 255).astype(np.uint8)
    return np.zeros_like(arr, dtype=np.uint8)


def save_panel_png(arr: np.ndarray, out_path: Path) -> Path:
    img = Image.fromarray(normalize_to_uint8(arr), mode="L")
    img.save(out_path)
    return out_path


def save_binary_mask_png(arr: np.ndarray, threshold: float, out_path: Path) -> Path:
    # Pure black-and-white mask: white (255) where voxel > threshold, black (0) elsewhere
    # This is what the Otsu filter would normally produce as its primary output
    binary = ((arr > threshold).astype(np.uint8) * 255)
    Image.fromarray(binary, mode="L").save(out_path)
    return out_path


def build_histogram_png(arr: np.ndarray, threshold: float, out_path: Path) -> Path:
    # Only include non-zero values so the zeroed background doesn't dominate
    # the plot and bury the foreground intensity distribution
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    flat = arr.flatten()
    flat = flat[flat > 0]

    fig, ax = plt.subplots(figsize=(10, 4))
    fig.patch.set_facecolor("#EAF2FB")
    ax.set_facecolor("#EAF2FB")
    ax.hist(flat, bins=128, color="#2E6DA4", alpha=0.8, edgecolor="none")
    ax.axvline(
        threshold,
        color="#1A2B4A",
        linewidth=1.5,
        linestyle="--",
        label=f"Otsu threshold = {threshold:.1f}",
    )
    ax.legend(fontsize=8, framealpha=0.8)
    ax.set_xlabel("Intensity", fontsize=9, color="#374151")
    ax.set_ylabel("Voxel count", fontsize=9, color="#374151")
    ax.tick_params(colors="#6B7280", labelsize=8)
    for spine in ax.spines.values():
        spine.set_edgecolor("#CBD5E1")
    plt.tight_layout()
    plt.savefig(str(out_path), dpi=150, bbox_inches="tight")
    plt.close(fig)
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
    masked_png: Path,
    binary_png: Path,
    hist_png: Path,
    meta: dict,
) -> None:
    page_w, page_h = A4
    margin = 18 * mm

    doc = SimpleDocTemplate(
        str(pdf_path),
        pagesize=A4,
        leftMargin=margin,
        rightMargin=margin,
        topMargin=10 * mm,
        bottomMargin=10 * mm,
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

    story.append(Paragraph("Otsu Threshold Report", title_style))
    story.append(Paragraph(
        f"Generated: {datetime.now().strftime('%d %B %Y, %H:%M')}",
        subtitle_style,
    ))
    story.append(HRFlowable(width="100%", thickness=2, color=BRAND_MID, spaceAfter=10))

    story.append(Paragraph("Image Information", section_style))

    label_map = {
        "filename": "File Name",
        "format": "Format",
        "dimensions": "Dimensions",
        "size": "Size (voxels)",
        "spacing_mm": "Spacing (mm)",
        "origin": "Origin",
        "pixel_type": "Output Pixel Type",
        "otsu_threshold": "Otsu Threshold",
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

    story.append(Paragraph("Results", section_style))
    story.append(HRFlowable(width="100%", thickness=0.5, color=DIVIDER, spaceAfter=6))

    # Three slice panels in row 1, histogram same size centered on row 2
    panel_w = (page_w - 2 * margin - 8 * mm) / 3
    panel_h = 55 * mm

    slice_panels = [
        (_fit_image(orig_png, panel_w, panel_h), "Original"),
        (_fit_image(masked_png, panel_w, panel_h), "Masked Intensity"),
        (_fit_image(binary_png, panel_w, panel_h), "Binary Mask"),
    ]

    img_tbl = Table([[p for p, _ in slice_panels]], colWidths=[panel_w] * 3)
    img_tbl.setStyle(TableStyle([
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 2),
        ("RIGHTPADDING", (0, 0), (-1, -1), 2),
    ]))
    cap_tbl = Table([[Paragraph(label, caption_style) for _, label in slice_panels]], colWidths=[panel_w] * 3)
    cap_tbl.setStyle(TableStyle([
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
    ]))

    story.append(img_tbl)
    story.append(cap_tbl)
    story.append(Spacer(1, 2 * mm))

    # Histogram spans the full content width and takes all remaining vertical space
    hist_w = col_w
    hist_h = 88 * mm
    hist_img = _fit_image(hist_png, hist_w, hist_h)
    hist_tbl = Table([[hist_img]], colWidths=[hist_w])
    hist_tbl.setStyle(TableStyle([
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
    ]))
    hist_cap_tbl = Table([[Paragraph("Intensity Histogram", caption_style)]], colWidths=[hist_w])
    hist_cap_tbl.setStyle(TableStyle([
        ("ALIGN", (0, 0), (-1, -1), "CENTER"),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
    ]))

    story.append(hist_tbl)
    story.append(hist_cap_tbl)

    story.append(Spacer(1, 3 * mm))
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
        description="Apply Otsu thresholding to an MRI volume and produce a PDF report."
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
        "--s3-bucket",
        metavar="BUCKET",
        help="S3 bucket for output files and masked DICOM staging (overrides OUTPUT_S3_BUCKET env var)",
    )
    parser.add_argument(
        "--s3-prefix",
        metavar="PREFIX",
        help="S3 key prefix for output files (overrides OUTPUT_S3_PREFIX env var)",
    )
    parser.add_argument(
        "--datastore-id",
        metavar="ID",
        help="HealthImaging datastore ID for reimporting the masked series "
             "(overrides DATASTORE_ID env var)",
    )
    parser.add_argument(
        "--import-role-arn",
        metavar="ARN",
        help="IAM role ARN that HealthImaging assumes to read staged DICOM from S3 "
             "(overrides IMPORT_ROLE_ARN env var)",
    )
    args = parser.parse_args()

    # Fall back to environment variables for anything not set on the command line
    # This is how ECS Fargate passes config to the container
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

    # Need exactly one input source
    if not args.input and not (s3_input_bucket and s3_input_prefix):
        parser.error(
            "an input source is required: use --input for a local file, or set "
            "ORIGINAL_S3_BUCKET + ORIGINAL_S3_PREFIX (or --s3-input-bucket + --s3-input-prefix) "
            "for an S3 DICOM source"
        )
    if args.input and (s3_input_bucket or s3_input_prefix):
        parser.error("--input cannot be combined with S3 input arguments")

    # S3 DICOM path needs the downstream AWS config too
    if s3_input_bucket and s3_input_prefix:
        if not args.datastore_id:
            parser.error("DATASTORE_ID (or --datastore-id) is required with S3 DICOM input")
        if not args.s3_bucket:
            parser.error("OUTPUT_S3_BUCKET (or --s3-bucket) is required with S3 DICOM input")
        if not args.import_role_arn:
            parser.error("IMPORT_ROLE_ARN (or --import-role-arn) is required with S3 DICOM input")

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    # S3 DICOM (ECS): download original .dcm files, read with pydicom, return
    # ITK image + frame_descriptors for tag-preserving output
    # Local (dev): ITK reads the file directly, no reimport step
    if s3_input_bucket and s3_input_prefix:
        stem = Path(s3_input_prefix.rstrip("/")).name
        print(f"  Loading        : s3://{s3_input_bucket}/{s3_input_prefix}", flush=True)
        original, frame_descriptors = load_from_s3_dicom(s3_input_bucket, s3_input_prefix)
        print(f"  Loaded {len(frame_descriptors)} slices", flush=True)
        source_label = f"s3://{s3_input_bucket}/{s3_input_prefix}"
        source_format = "dicom (s3)"
    else:
        input_path = Path(args.input)
        if not input_path.exists():
            sys.exit(f"Error: file not found, {input_path}")
        stem = input_path.name.split(".")[0]
        print(f"  Loading        : {input_path}", flush=True)
        original = load_as_float(input_path)
        frame_descriptors = None
        source_label = str(input_path)
        source_format = "".join(input_path.suffixes).lower()

    # Grab spatial metadata before doing anything to the image
    region = original.GetLargestPossibleRegion()
    size = region.GetSize()
    spacing = original.GetSpacing()
    origin = original.GetOrigin()
    ndim = original.GetImageDimension()

    # Compute threshold and build the masked intensity volume
    print("  Computing Otsu threshold", flush=True)
    threshold = compute_otsu_threshold(original)
    print(f"  Otsu threshold : {threshold:.4f}", flush=True)
    masked_arr = apply_otsu_mask(original, threshold)

    meta = {
        "filename": source_label,
        "format": source_format,
        "dimensions": f"{ndim}D",
        "size": " x ".join(str(size[i]) for i in range(ndim)),
        "spacing_mm": "  ".join(f"{spacing[i]:.3f}" for i in range(ndim)),
        "origin": "  ".join(f"{origin[i]:.2f}" for i in range(ndim)),
        "pixel_type": "uint8 (output)",
        "otsu_threshold": f"{threshold:.4f}",
    }

    # Build the three PNG panels for the PDF
    original_arr = itk.GetArrayFromImage(original)
    orig_slice = extract_middle_slice(original_arr)
    masked_slice = extract_middle_slice(masked_arr)

    binary_slice = extract_middle_slice((original_arr > threshold).astype(np.float32))

    orig_png = save_panel_png(orig_slice, out_dir / f"{stem}_orig_slice.png")
    masked_png = save_panel_png(masked_slice, out_dir / f"{stem}_masked_slice.png")
    binary_png = save_binary_mask_png(binary_slice, 0.5, out_dir / f"{stem}_binary_mask.png")
    hist_png = build_histogram_png(original_arr, threshold, out_dir / f"{stem}_histogram.png")

    pdf_path = out_dir / f"{stem}_otsu_report.pdf"
    print(f"  PDF report     : {pdf_path}", flush=True)
    build_pdf(pdf_path, orig_png, masked_png, binary_png, hist_png, meta)

    # Upload the PDF so the Spring backend can generate a presigned download URL
    # Key is always <s3_prefix>/report.pdf so the backend can find it from the
    # job ID without extra DB state
    if args.s3_bucket and args.s3_prefix:
        s3 = boto3.client("s3")
        pdf_s3_key = args.s3_prefix.rstrip("/") + "/report.pdf"
        s3.upload_file(str(pdf_path), args.s3_bucket, pdf_s3_key)
        print(f"  PDF uploaded   : s3://{args.s3_bucket}/{pdf_s3_key}", flush=True)

    # Write both DICOM series, stage on S3, trigger two HealthImaging import jobs,
    # and write output.json with both job IDs so the Spring app can catalog
    # two image sets per Otsu job. Only runs on the S3 DICOM input path (ECS)
    if frame_descriptors is not None:
        s3_prefix = (args.s3_prefix or f"otsu-threshold/{stem}").rstrip("/")
        base_uri = f"s3://{args.s3_bucket}/{s3_prefix.rstrip('/')}"

        print("  Writing DICOM  : masked intensity series", flush=True)
        masked_dicom_dir = write_masked_dicom_series(
            masked_arr, frame_descriptors, out_dir / "masked-dicom"
        )
        masked_input_uri = upload_dicom_to_s3(
            masked_dicom_dir, args.s3_bucket, f"{s3_prefix}/dicom-masked"
        )
        print("  Starting import: masked intensity", flush=True)
        masked_job_id = start_healthimaging_import(
            args.datastore_id,
            masked_input_uri,
            f"{base_uri}/import-logs-masked/",
            args.import_role_arn,
        )
        print(f"  Import job ID  : {masked_job_id}", flush=True)

        print("  Writing DICOM  : binary mask series", flush=True)
        binary_dicom_dir = write_binary_mask_dicom_series(
            original_arr, threshold, frame_descriptors, out_dir / "binary-dicom"
        )
        binary_input_uri = upload_dicom_to_s3(
            binary_dicom_dir, args.s3_bucket, f"{s3_prefix}/dicom-binary"
        )
        print("  Starting import: binary mask", flush=True)
        binary_job_id = start_healthimaging_import(
            args.datastore_id,
            binary_input_uri,
            f"{base_uri}/import-logs-binary/",
            args.import_role_arn,
        )
        print(f"  Import job ID  : {binary_job_id}", flush=True)

        s3_out = boto3.client("s3")
        output_key = s3_prefix.rstrip("/") + "/output.json"
        s3_out.put_object(
            Bucket=args.s3_bucket,
            Key=output_key,
            Body=json.dumps({
                "healthImagingImportJobId": masked_job_id,
                "healthImagingBinaryMaskImportJobId": binary_job_id,
            }),
            ContentType="application/json",
        )
        print(f"  Output metadata: s3://{args.s3_bucket}/{output_key}", flush=True)

    print("\nDone.")
    print(f"  PDF report      : {pdf_path}")
    if frame_descriptors is not None:
        print(f"  Masked job ID   : {masked_job_id}")
        print(f"  Binary job ID   : {binary_job_id}")


if __name__ == "__main__":
    main()
