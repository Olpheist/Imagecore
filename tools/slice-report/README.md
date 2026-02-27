# image-pipeline

A lightweight CLI tool that reads a medical image, extracts the middle slice, and produces a formatted PDF report alongside the extracted slice as a PNG.

## Installation

```bash
pip install .
```

For development:

```bash
pip install -e ".[dev]"
```

## Usage

```bash
image-pipeline --input scan.nii.gz --output-dir ./results
```

Or run directly with Python:

```bash
python -m image_pipeline.pipeline --input scan.png --output-dir ./results
```

### Options

| Flag | Default | Description |
|------|---------|-------------|
| `--input` | *(required)* | Path to the input image |
| `--output-dir` | `./output` | Directory where outputs are written |

### Outputs

| File | Description |
|------|-------------|
| `<stem>_slice.png` | Middle slice, intensity-normalised |
| `<stem>_report.pdf` | Formatted PDF report with metadata and slice |

## Supported Formats

| Format | Extension(s) |
|--------|-------------|
| Standard images | `.png` `.jpg` `.tiff` `.bmp` |
| NIfTI | `.nii` `.nii.gz` |
| DICOM | `.dcm` |

## Future Plans

- OHIF Viewer integration for side-by-side image + PDF review
- DICOM-compatible output images
- Multi-slice PDF reports