"""
inspect_dicom_output.py, manual inspection script for the N4 bias correction pipeline

Loads a local DICOM file or series directory, reads the real DICOM tags with
pydicom to build frame_descriptors (the same structure that load_from_healthimaging
produces), runs N4, and calls write_corrected_dicom_series so you can inspect the
output .dcm files in a local DICOM viewer.

Use this to verify that StudyInstanceUID is preserved, pixel data round-trips
correctly, and the output is a valid DICOM series before testing the full
HealthImaging reimport path.

Usage (inside Docker or with the package installed):
    python3 inspect_dicom_output.py --input <file.dcm or dicom-dir/> [--output-dir output] [--shrink-factor 2]
"""

import argparse
import sys
from pathlib import Path

import itk
import pydicom
import numpy as np

from n4_bias_correction.pipeline import (
    load_as_float,
    pad_single_slice,
    shrink_image,
    run_n4,
    _upsample_arr,
    extract_middle_slice,
    save_panel_png,
    build_pdf,
    write_corrected_dicom_series,
)


def find_dicom_files(input_path: Path) -> list[Path]:
    """Return an ordered list of .dcm files from a file or directory."""
    if input_path.is_dir():
        files = sorted(input_path.glob("*.dcm"))
        if not files:
            # Some series use no extension
            files = sorted(f for f in input_path.iterdir() if f.is_file())
        return files
    return [input_path]


def build_frame_descriptors(dcm_files: list[Path]) -> list[dict]:
    """
    Build frame_descriptors from real DICOM files using pydicom.

    This produces the same structure that load_from_healthimaging() builds from
    HealthImaging's DICOM JSON metadata. raw_dicom is populated via pydicom's
    to_json_dict(), which outputs the same {tag: {"vr": ..., "Value": [...]}}
    format that _get_tag() expects.
    """
    descriptors = []
    for dcm_path in dcm_files:
        ds = pydicom.dcmread(str(dcm_path), stop_before_pixels=True)

        image_position = [float(v) for v in getattr(ds, "ImagePositionPatient", [0.0, 0.0, 0.0])]
        image_orientation = [float(v) for v in getattr(ds, "ImageOrientationPatient", [1, 0, 0, 0, 1, 0])]
        pixel_spacing_raw = getattr(ds, "PixelSpacing", [1.0, 1.0])
        pixel_spacing = [float(v) for v in pixel_spacing_raw]
        slice_thickness_raw = getattr(ds, "SliceThickness", None)
        slice_thickness = float(slice_thickness_raw) if slice_thickness_raw is not None else None
        instance_number = int(getattr(ds, "InstanceNumber", 0))

        descriptors.append({
            "frame_id": str(getattr(ds, "SOPInstanceUID", "")),
            "instance_number": instance_number,
            "image_position": image_position,
            "image_orientation": image_orientation,
            "pixel_spacing": pixel_spacing,
            "slice_thickness": slice_thickness,
            # to_json_dict() produces the same {tag: {"vr": ..., "Value": [...]}} format
            # that HealthImaging metadata uses, so _get_tag() works unchanged.
            "raw_dicom": ds.to_json_dict(),
        })

    # Sort by z-position then instance number, same ordering as _collect_frames()
    descriptors.sort(key=lambda f: (f["image_position"][2], f["instance_number"]))
    return descriptors


def main():
    parser = argparse.ArgumentParser(
        description="Test write_corrected_dicom_series locally using a DICOM input."
    )
    parser.add_argument(
        "--input", required=True, metavar="PATH",
        help="Path to a single .dcm file or a directory containing a DICOM series",
    )
    parser.add_argument("--output-dir", default="output", metavar="DIR")
    parser.add_argument("--shrink-factor", type=int, default=2, metavar="N")
    args = parser.parse_args()

    input_path = Path(args.input)
    if not input_path.exists():
        sys.exit(f"Error: not found,{input_path}")

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    stem = input_path.name.split(".")[0] if input_path.is_file() else input_path.name

    # --- Load DICOM ---
    dcm_files = find_dicom_files(input_path)
    if not dcm_files:
        sys.exit(f"Error: no DICOM files found in {input_path}")
    print(f"  Loading        : {len(dcm_files)} DICOM file(s) from {input_path}")

    print("  Building       : frame_descriptors from DICOM tags")
    frame_descriptors = build_frame_descriptors(dcm_files)

    print("  Loading image  : ITK float32")
    original = load_as_float(input_path)

    # --- N4 pipeline (identical to main()) ---
    n4_input, was_padded = pad_single_slice(original, args.shrink_factor)

    if args.shrink_factor > 1:
        print(f"  Shrinking      : factor {args.shrink_factor}")
        n4_image = shrink_image(n4_input, args.shrink_factor)
    else:
        n4_image = n4_input

    print("  Running N4     : fitting levels=4, iterations=[50,50,50,50]")
    corrected_arr, bias_arr = run_n4(n4_image)

    if args.shrink_factor > 1:
        target = itk.GetArrayFromImage(n4_input)
        corrected_arr = _upsample_arr(corrected_arr, target.shape)
        bias_arr = _upsample_arr(bias_arr, target.shape)

    if was_padded:
        corrected_arr = corrected_arr[1:2]
        bias_arr = bias_arr[1:2]
        frame_descriptors = frame_descriptors[:1]

    original_arr = itk.GetArrayFromImage(original)

    # --- Save PDF ---
    orig_slice = extract_middle_slice(original_arr)
    corr_slice = extract_middle_slice(corrected_arr)
    bias_slice = extract_middle_slice(bias_arr)

    orig_png = save_panel_png(orig_slice, out_dir / f"{stem}_orig_slice.png")
    corr_png = save_panel_png(corr_slice, out_dir / f"{stem}_corr_slice.png")
    bias_png = save_panel_png(bias_slice, out_dir / f"{stem}_bias_slice.png")

    size = original.GetLargestPossibleRegion().GetSize()
    spacing = original.GetSpacing()
    origin_itk = original.GetOrigin()
    ndim = original.GetImageDimension()
    meta = {
        "filename": str(input_path),
        "format": "dicom",
        "dimensions": f"{ndim}D",
        "size": " x ".join(str(size[i]) for i in range(ndim)),
        "spacing_mm": "  ".join(f"{spacing[i]:.3f}" for i in range(ndim)),
        "origin": "  ".join(f"{origin_itk[i]:.2f}" for i in range(ndim)),
        "pixel_type": "float32",
        "shrink_factor": str(args.shrink_factor),
        "fitting_levels": "4",
        "max_iterations": "50",
        "convergence_threshold": "0.001",
    }

    pdf_path = out_dir / f"{stem}_n4_report.pdf"
    print(f"  PDF report     : {pdf_path}")
    build_pdf(pdf_path, orig_png, corr_png, bias_png, meta)

    # --- Write DICOM output ---
    dicom_dir = out_dir / f"{stem}-corrected-dicom"
    print(f"  Writing DICOM  : {dicom_dir}/")
    write_corrected_dicom_series(corrected_arr, frame_descriptors, dicom_dir)

    dcm_out = sorted(dicom_dir.glob("*.dcm"))
    print(f"\nDone. Wrote {len(dcm_out)} DICOM file(s) to {dicom_dir}/")

    # Quick tag summary from the first output slice
    first = pydicom.dcmread(str(dcm_out[0]))
    print("\nFirst output slice tags:")
    print(f"  StudyInstanceUID  : {first.StudyInstanceUID}")
    print(f"  SeriesInstanceUID : {first.SeriesInstanceUID}")
    print(f"  SOPInstanceUID    : {first.SOPInstanceUID}")
    print(f"  ImageType         : {first.ImageType}")
    print(f"  SeriesDescription : {first.SeriesDescription}")
    print(f"  Rows x Columns    : {first.Rows} x {first.Columns}")
    print(f"  RescaleSlope      : {first.RescaleSlope}")
    print(f"  RescaleIntercept  : {first.RescaleIntercept}")

    # Verify StudyInstanceUID was preserved from input
    src_ds = pydicom.dcmread(str(dcm_files[0]), stop_before_pixels=True)
    if str(first.StudyInstanceUID) == str(src_ds.StudyInstanceUID):
        print("\n  StudyInstanceUID matches source.")
    else:
        print("\n  WARNING: StudyInstanceUID mismatch,check _get_tag / raw_dicom.")


if __name__ == "__main__":
    main()
