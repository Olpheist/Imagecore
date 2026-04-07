# n4-bias-correction

A CLI tool that applies N4 bias field correction to MRI volumes and produces a PDF report comparing the original, corrected, and bias field middle slices. Supports local image files and AWS HealthImaging as input sources, with an optional reimport pipeline that uploads the corrected series back to HealthImaging under the same study.

## Installation

```bash
pip install .
```

For development:

```bash
pip install -e ".[dev]"
```

## Usage

### Local file

```bash
n4-bias-correction --input scan.dcm --output-dir ./results
```

### AWS HealthImaging

Fetches the image from HealthImaging, runs N4, and always reimports the corrected series back into HealthImaging under the same study.

```bash
n4-bias-correction \
  --datastore-id <datastore-id> \
  --image-set-id <image-set-id> \
  --s3-bucket <bucket> \
  --import-role-arn <arn> \
  --output-dir ./results
```

## Options

| Flag | Default | Description |
|------|---------|-------------|
| `--input` | | Path to a local image file. Mutually exclusive with `--image-set-id`. |
| `--image-set-id` | | AWS HealthImaging ImageSet ID. Requires `--datastore-id`. |
| `--datastore-id` | | AWS HealthImaging datastore ID. |
| `--region` | boto3 default | AWS region for HealthImaging. |
| `--output-dir` | `./output` | Directory where outputs are written. |
| `--shrink-factor` | `2` | Uniform downsample factor applied before N4 estimation. Use `1` to run at full resolution (slower). |
| `--s3-bucket` | | S3 bucket used to stage corrected DICOM files before reimport (required with `--image-set-id`). |
| `--s3-prefix` | `n4-corrected/<image-set-id>` | S3 key prefix for staged DICOM files. |
| `--import-role-arn` | | IAM role ARN that HealthImaging assumes to read the staged DICOM from S3 (required with `--image-set-id`). |

## Outputs

| File | Description |
|------|-------------|
| `<stem>_n4_report.pdf` | PDF report with original, corrected, and bias field middle slice comparison |
| `<stem>_orig_slice.png` | Middle slice of the original image |
| `<stem>_corr_slice.png` | Middle slice of the corrected image |
| `<stem>_bias_slice.png` | Middle slice of the multiplicative bias field |
| `<stem>-corrected-dicom/` | Per-slice DICOM series reimported into HealthImaging (only when using `--image-set-id`) |

## Supported Input Formats

| Format | Extension(s) |
|--------|-------------|
| NIfTI | `.nii`, `.nii.gz` |
| DICOM | `.dcm` (single file or directory) |
| MetaImage | `.mha`, `.mhd` |
| NRRD | `.nrrd`, `.nhdr` |
| AWS HealthImaging | HTJ2K via `--image-set-id` |

## Building the Docker Image

From the `tools/n4-bias-correction/` directory:

```bash
docker build -f Dockerfile_n4-bias-correction -t n4-bias-correction .
```

## Running Unit Tests

Unit tests for pipeline functions are in `tests/test_pipeline.py` and run with pytest.

Install dependencies and run:

```bash
pip install -e ".[dev]"
pytest tests/
```

Inside Docker:

```bash
docker run --rm \
  -v $(pwd):/work \
  --entrypoint pytest \
  n4-bias-correction \
  tests/
```

## Local Inspection

`inspect_dicom_output.py` is a manual inspection script for verifying the pipeline output locally before running against HealthImaging. It loads a local DICOM file or series, builds the same `frame_descriptors` structure that the HealthImaging loader produces, runs N4, and writes the corrected DICOM series and PDF report so you can inspect them in a local DICOM viewer.

```bash
python3 inspect_dicom_output.py --input scan.dcm --output-dir ./results
```

With a DICOM series directory:

```bash
python3 inspect_dicom_output.py --input ./dicom-series/ --output-dir ./results
```

Inside Docker:

```bash
docker run --rm \
  -v /path/to/dicom:/data \
  -v $(pwd)/inspect_dicom_output.py:/work/inspect_dicom_output.py \
  --entrypoint python3 \
  n4-bias-correction \
  inspect_dicom_output.py --input /data/scan.dcm --output-dir /data/results
```

The script prints a tag summary on completion, including a confirmation that `StudyInstanceUID` matches the source.

## Algorithm Parameters

N4 is run with the following defaults (matching common research pipelines):

| Parameter | Value |
|-----------|-------|
| Fitting levels | 4 |
| Max iterations per level | 50 |
| Convergence threshold | 0.001 |

## IAM Permissions

When running against AWS HealthImaging the ECS task role requires:

| Permission | Purpose |
|------------|---------|
| `medical-imaging:GetImageSetMetadata` | Fetch DICOM metadata to extract frame IDs and spatial info |
| `medical-imaging:GetImageFrame` | Fetch individual HTJ2K frames |
| `medical-imaging:StartDICOMImportJob` | Trigger reimport of the corrected series |
| `s3:PutObject` on the staging bucket | Upload corrected DICOM files before reimport |
| `iam:PassRole` for the import role | Allow HealthImaging to assume the import role to read from S3 |

## Notes

- Input images with very small or negative intensity values can produce poor results, as N4 operates in log-intensity space.
- The `--shrink-factor` controls how much the image is downsampled before bias estimation. The bias field is always applied at full resolution. Higher shrink factors are faster but may reduce correction accuracy for fine-scale inhomogeneity.
- When using `--image-set-id`, the corrected DICOM series shares the original Study Instance UID, keeping it associated with the same study in HealthImaging.

## Future Plans

- Otsu thresholding tool to precede this tool to threshold teh image to separate tissue from background, passing the binary result as the mask (use `OtsuThresholdImageFilter`)
- Multi-slice PDF reports