"""
inspect_dicom_output.py - manual inspection script for the N4 bias correction pipeline

Loads a local DICOM file or series directory, reads each slice with pydicom,
builds an ITK image with correct spatial metadata (same as the production
load_from_s3_dicom path), runs N4, and calls write_corrected_dicom_series so
you can inspect the output .dcm files in a local DICOM viewer.

Use this to verify that StudyInstanceUID is preserved, pixel data round-trips
correctly, and the output is a valid DICOM series before running the full
ECS/HealthImaging reimport path.

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


def load_dicom_datasets(input_path: Path) -> tuple:
    """
    Read all DICOM files from a local file or directory with pydicom, sort by
    InstanceNumber (fallback: ImagePositionPatient Z), assemble into a 3D float32
    ITK image with correct spatial metadata, and return it ready for N4 along with
    the sorted pydicom datasets for tag-preserving output.

    Mirrors the production load_from_s3_dicom() path, but reads from disk instead
    of S3.

    Returns (itk_image, sorted_datasets).
    """
    dcm_files = find_dicom_files(input_path)
    if not dcm_files:
        sys.exit(f"Error: no DICOM files found in {input_path}")

    print(f"  Loading        : {len(dcm_files)} DICOM file(s) from {input_path}")
    datasets = [pydicom.dcmread(str(f)) for f in dcm_files]

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

    row = np.array(image_orientation[:3])
    col = np.array(image_orientation[3:])
    normal = np.cross(row, col)
    direction_mat = np.column_stack([row, col, normal])
    itk_direction = itk.matrix_from_array(direction_mat.astype(np.float64))
    image_3d.SetDirection(itk_direction)

    return image_3d, datasets


def main():
    parser = argparse.ArgumentParser(
        description="Test the N4 pipeline locally using a DICOM input."
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
        sys.exit(f"Error: not found, {input_path}")

    out_dir = Path(args.output_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    stem = input_path.name.split(".")[0] if input_path.is_file() else input_path.name

    # --- Load DICOM ---
    original, datasets = load_dicom_datasets(input_path)

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
        datasets = datasets[:1]

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
    write_corrected_dicom_series(datasets, corrected_arr, dicom_dir)

    dcm_out = sorted(dicom_dir.glob("*.dcm"))
    print(f"\nDone. Wrote {len(dcm_out)} DICOM file(s) to {dicom_dir}/")

    # Quick tag summary from the first output slice
    first_out = pydicom.dcmread(str(dcm_out[0]))
    print("\nFirst output slice tags:")
    print(f"  StudyInstanceUID  : {first_out.StudyInstanceUID}")
    print(f"  SeriesInstanceUID : {first_out.SeriesInstanceUID}")
    print(f"  SOPInstanceUID    : {first_out.SOPInstanceUID}")
    print(f"  ImageType         : {first_out.ImageType}")
    print(f"  SeriesDescription : {first_out.SeriesDescription}")
    print(f"  Rows x Columns    : {first_out.Rows} x {first_out.Columns}")
    print(f"  RescaleSlope      : {first_out.RescaleSlope}")
    print(f"  RescaleIntercept  : {first_out.RescaleIntercept}")

    # Verify StudyInstanceUID was preserved from input
    src_ds = datasets[0]
    if str(first_out.StudyInstanceUID) == str(src_ds.StudyInstanceUID):
        print("\n  StudyInstanceUID matches source.")
    else:
        print("\n  WARNING: StudyInstanceUID mismatch.")


if __name__ == "__main__":
    main()
