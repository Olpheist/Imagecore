"""
pipeline.py - Medical Image Slice to PDF Report Tool

Reads a 2D or 3D image via itk, extracts the middle slice, saves it
as a PNG with correct physical aspect ratio, and produces a formatted PDF report.

Usage:
    python pipeline.py --input <image_path> [--output-dir <dir>]

Supported input formats (anything itk can read):
    .png, .jpg, .tiff, .bmp
    .nii, .nii.gz
    .dcm (single file or directory)
    .mha, .mhd, .nrrd, .nhdr
"""

import argparse
import sys
from datetime import datetime
from pathlib import Path

import itk
import numpy as np
from PIL import Image

from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.units import mm
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Image as RLImage,
    HRFlowable, Table, TableStyle,
)
from reportlab.lib.enums import TA_CENTER, TA_LEFT


def load_image(path: Path) -> tuple[np.ndarray, dict]:
    """Load any itk-supported image. Returns (2-D numpy array, metadata)."""
    itk_img = itk.imread(str(path))

    # itk stores size/spacing as (x, y, z); numpy array will be (z, y, x)
    region  = itk_img.GetLargestPossibleRegion()
    size    = region.GetSize()
    spacing = itk_img.GetSpacing()
    origin  = itk_img.GetOrigin()
    ndim    = itk_img.GetImageDimension()

    meta: dict = {
        "filename":   path.name,
        "format":     "".join(path.suffixes).lower(),
        "dimensions": str(ndim) + "D",
        "size":       " x ".join(str(size[i]) for i in range(ndim)),
        "spacing_mm": "  ".join(f"{spacing[i]:.3f}" for i in range(ndim)),
        "origin":     "  ".join(f"{origin[i]:.2f}" for i in range(ndim)),
        "pixel_type": str(itk.template(itk_img)[1][0]),
        "components": str(itk_img.GetNumberOfComponentsPerPixel()),
    }

    # Convert to numpy — itk gives (z, y, x) ordering for 3D
    arr = itk.GetArrayFromImage(itk_img)

    if arr.ndim == 2:
        # 2D image: in-plane spacing is (row=y=spacing[1], col=x=spacing[0])
        meta["slice_spacing"] = (float(spacing[1]), float(spacing[0]))
        return arr.astype(float), meta

    if arr.ndim == 3:
        axis = int(np.argmax(arr.shape))
        mid  = arr.shape[axis] // 2
        slc  = np.take(arr, mid, axis=axis)
        axis_name = ["z", "y", "x"][axis]
        meta["slice_axis"]  = axis_name
        meta["slice_index"] = str(mid)

        # Determine the two in-plane spacings for the extracted slice.
        sp = [float(spacing[i]) for i in range(ndim)]
        plane_spacing = {
            "z": (sp[1], sp[0]),
            "y": (sp[2], sp[0]),
            "x": (sp[2], sp[1]),
        }
        meta["slice_spacing"] = plane_spacing.get(axis_name, (1.0, 1.0))
        return slc.astype(float), meta

    # 4-D (e.g. time series) — take mid volume then mid slice
    mid_vol = arr.shape[0] // 2
    vol     = arr[mid_vol]
    mid_slc = vol.shape[0] // 2
    meta["slice_index"]   = f"vol {mid_vol}, slice {mid_slc}"
    meta["slice_spacing"] = (1.0, 1.0)
    return vol[mid_slc].astype(float), meta


def save_slice_png(arr: np.ndarray, out_path: Path, spacing: tuple[float, float] = (1.0, 1.0)) -> Path:
    """
    Normalize arr to 0-255, resample to correct physical aspect ratio, save as PNG.

    spacing: (row_spacing_mm, col_spacing_mm) — the physical size of each pixel
             along the row and column axes of the slice.
    """
    if arr.ndim == 3 and arr.shape[2] in (3, 4):
        img = Image.fromarray(arr.astype(np.uint8))
    else:
        lo, hi = arr.min(), arr.max()
        norm = ((arr - lo) / (hi - lo) * 255).astype(np.uint8) if hi > lo else np.zeros_like(arr, dtype=np.uint8)
        img = Image.fromarray(norm, mode="L")

    sp_row, sp_col = spacing
    if abs(sp_row - sp_col) > 1e-4:
        # Rescale so each output pixel represents a square physical region.
        # Keep the larger dimension fixed to avoid upsampling artefacts.
        h, w = arr.shape[:2]
        phys_h = h * sp_row   # physical height in mm
        phys_w = w * sp_col   # physical width  in mm
        ref    = min(sp_row, sp_col)   # target pixel size = smallest spacing
        new_h  = int(round(phys_h / ref))
        new_w  = int(round(phys_w / ref))
        img = img.resize((new_w, new_h), Image.LANCZOS)

    img.save(out_path)
    return out_path


# ---------------------------------------------------------------------------
# PDF generation
# ---------------------------------------------------------------------------

BRAND_DARK  = colors.HexColor("#1A2B4A")
BRAND_MID   = colors.HexColor("#2E6DA4")
BRAND_LIGHT = colors.HexColor("#EAF2FB")
GREY        = colors.HexColor("#6B7280")
DIVIDER     = colors.HexColor("#CBD5E1")


def build_pdf(pdf_path: Path, slice_png: Path, meta: dict) -> None:
    page_w, page_h = A4
    margin = 18 * mm

    doc = SimpleDocTemplate(
        str(pdf_path),
        pagesize=A4,
        leftMargin=margin, rightMargin=margin,
        topMargin=15 * mm, bottomMargin=15 * mm,
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

    story = []

    # Header
    story.append(Paragraph("Medical Image Report", title_style))
    story.append(Paragraph(
        f"Generated: {datetime.now().strftime('%d %B %Y, %H:%M')}",
        subtitle_style,
    ))
    story.append(HRFlowable(width="100%", thickness=2, color=BRAND_MID, spaceAfter=10))

    # Metadata table
    story.append(Paragraph("Image Information", section_style))

    label_map = {
        "filename":           "File Name",
        "format":             "Format",
        "dimensions":         "Dimensions",
        "size":               "Size (voxels)",
        "spacing_mm":         "Spacing (mm)",
        "origin":             "Origin",
        "pixel_type":         "Pixel Type",
        "components":         "Components",
        "modality":           "Modality",
        "patient_id":         "Patient ID",
        "study_date":         "Study Date",
        "series_description": "Series",
        "slice_axis":         "Slice Axis",
        "slice_index":        "Slice Index",
    }

    rows = [
        [Paragraph(f"<b>{label}</b>", body_style), Paragraph(str(meta[key]), body_style)]
        for key, label in label_map.items()
        if key in meta
    ]

    col_w = page_w - 2 * margin
    tbl = Table(rows, colWidths=[col_w * 0.35, col_w * 0.65])
    tbl.setStyle(TableStyle([
        ("ROWBACKGROUNDS", (0, 0), (-1, -1), [colors.white, BRAND_LIGHT]),
        ("GRID",          (0, 0), (-1, -1), 0.4, DIVIDER),
        ("TOPPADDING",    (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("LEFTPADDING",   (0, 0), (-1, -1), 8),
        ("RIGHTPADDING",  (0, 0), (-1, -1), 8),
        ("VALIGN",        (0, 0), (-1, -1), "MIDDLE"),
    ]))
    story.append(tbl)

    # Slice image
    story.append(Paragraph("Middle Slice", section_style))
    story.append(HRFlowable(width="100%", thickness=0.5, color=DIVIDER, spaceAfter=6))

    max_img_w = page_w - 2 * margin
    max_img_h = 120 * mm

    with Image.open(slice_png) as pil:
        img_w_px, img_h_px = pil.size

    scale  = min(max_img_w / img_w_px, max_img_h / img_h_px)
    rl_img = RLImage(str(slice_png), width=img_w_px * scale - 5, height=img_h_px * scale - 5)
    rl_img.hAlign = "CENTER"
    story.append(rl_img)

    slice_label = meta.get("slice_index", "middle")
    axis_label  = meta.get("slice_axis", "")
    sp          = meta.get("slice_spacing")
    sp_note     = f", spacing {sp[0]:.2f} x {sp[1]:.2f} mm" if sp else ""
    axis_part   = f", {axis_label}-axis" if axis_label else ""
    caption_txt = (
        f"Figure 1 - Middle slice (index {slice_label}{axis_part}{sp_note})"
        f" extracted from <i>{meta['filename']}</i>"
    )
    story.append(Spacer(1, 3 * mm))
    story.append(Paragraph(caption_txt, caption_style))

    # Footer
    story.append(Spacer(1, 6 * mm))
    story.append(HRFlowable(width="100%", thickness=0.5, color=DIVIDER))
    story.append(Spacer(1, 1 * mm))
    story.append(Paragraph(
        "This report is generated automatically for research and development purposes only. "
        "It does not constitute a clinical diagnosis.",
        ParagraphStyle("Footer", fontSize=7, textColor=GREY,
                       fontName="Helvetica-Oblique", alignment=TA_CENTER),
    ))

    doc.build(story)


def main():
    parser = argparse.ArgumentParser(
        description="Extract the middle slice of a medical image and produce a PDF report."
    )
    parser.add_argument("--input",      required=True, help="Path to the input image")
    parser.add_argument("--output-dir", default="output", help="Directory for outputs (default: ./output)")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        sys.exit(f"Error: file not found - {input_path}")

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    stem      = input_path.name.split(".")[0]
    slice_png = out_dir / f"{stem}_slice.png"
    pdf_path  = out_dir / f"{stem}_report.pdf"

    print(f"  Loading   : {input_path}")
    arr, meta = load_image(input_path)

    spacing = meta.get("slice_spacing", (1.0, 1.0))
    print(f"  Slice spacing: row={spacing[0]:.3f} mm, col={spacing[1]:.3f} mm")

    print(f"  Slice PNG : {slice_png}")
    save_slice_png(arr, slice_png, spacing=spacing)

    print(f"  PDF report: {pdf_path}")
    build_pdf(pdf_path, slice_png, meta)

    print("\nDone.")
    print(f"  Slice PNG : {slice_png}")
    print(f"  PDF report: {pdf_path}")


if __name__ == "__main__":
    main()