# n4-bias-correction

A CLI tool that applies N4 bias field correction to MRI volumes and produces a PDF report comparing the original, corrected, and bias field middle slices. In production it runs as an ECS Fargate task: it reads the original DICOM series from S3, applies N4, writes the corrected series back to S3, and triggers a HealthImaging import job to register the corrected series in the catalog.

## Installation

```bash
pip install .
```

For development:

```bash
pip install -e ".[dev]"
```

## Usage

### ECS Fargate (production)

Configuration is passed as environment variables injected by the Spring application when the ECS task is launched.

```bash
ORIGINAL_S3_BUCKET=<bucket> \
ORIGINAL_S3_PREFIX=<prefix> \
OUTPUT_S3_BUCKET=<bucket> \
OUTPUT_S3_PREFIX=<prefix> \
DATASTORE_ID=<datastore-id> \
IMPORT_ROLE_ARN=<arn> \
n4-bias-correction
```

### Local file (development)

Reads from a local file or DICOM directory. Produces the PDF report and corrected DICOM locally; does not upload to S3 or trigger HealthImaging import.

```bash
n4-bias-correction --input scan.dcm --output-dir ./results
```

```bash
n4-bias-correction --input ./dicom-series/ --output-dir ./results
```

## Options

| Flag | Default | Description |
|------|---------|-------------|
| `--input` | | Path to a local image file or DICOM directory. Mutually exclusive with `--s3-input-bucket`. |
| `--s3-input-bucket` | `ORIGINAL_S3_BUCKET` env | S3 bucket containing the original DICOM upload. |
| `--s3-input-prefix` | `ORIGINAL_S3_PREFIX` env | S3 key prefix of the original DICOM upload. |
| `--output-dir` | `./output` | Directory where local outputs are written. |
| `--shrink-factor` | `2` | Uniform downsample factor applied before N4 estimation. Use `1` to run at full resolution (slower). |
| `--s3-bucket` | `OUTPUT_S3_BUCKET` env | S3 bucket for output files (PDF report, corrected DICOM staging). |
| `--s3-prefix` | `OUTPUT_S3_PREFIX` env | S3 key prefix for output files. |
| `--datastore-id` | `DATASTORE_ID` env | HealthImaging datastore ID for reimporting the corrected series. |
| `--import-role-arn` | `IMPORT_ROLE_ARN` env | IAM role ARN that HealthImaging assumes to read staged DICOM from S3. |

## Outputs

| File | Description |
|------|-------------|
| `<stem>_n4_report.pdf` | PDF report with original, corrected, and bias field middle slice comparison. Uploaded to `<s3-prefix>/report.pdf` when running with S3 output. |
| `<stem>-corrected-dicom/` | Per-slice corrected DICOM series (deep-copied from originals with only pixel data replaced). Staged to `<s3-prefix>/dicom/` then imported into HealthImaging. |
| `output.json` | Written to `<s3-prefix>/output.json`. Contains `healthImagingImportJobId` so the Spring application can register the corrected series in the catalog after the task stops. |

## Supported Input Formats

| Format | Extension(s) |
|--------|-------------|
| DICOM | `.dcm` (single file or directory) |
| NIfTI | `.nii`, `.nii.gz` |
| MetaImage | `.mha`, `.mhd` |
| NRRD | `.nrrd`, `.nhdr` |

Note: non-DICOM formats are supported via `--input` for local development only. The production ECS path always reads DICOM from S3. Corrected DICOM output and HealthImaging reimport require DICOM input (pydicom datasets must be available for tag-preserving output).

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

`inspect_dicom_output.py` is a manual inspection script for verifying the pipeline end-to-end locally before running against ECS. It reads a local DICOM file or series directory using pydicom (the same path as the production S3 loader), runs N4, writes the corrected DICOM series and PDF report to disk, and prints a tag summary so you can confirm that `StudyInstanceUID` is preserved and pixel data round-trips correctly.

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

## Algorithm Parameters

N4 is run with the following defaults (matching common research pipelines):

| Parameter | Value |
|-----------|-------|
| Fitting levels | 4 |
| Max iterations per level | 50 |
| Convergence threshold | 0.001 |

## IAM Permissions

The ECS task role requires:

| Permission | Purpose |
|------------|---------|
| `s3:GetObject`, `s3:ListBucket` on the source bucket | Download the original DICOM series |
| `s3:PutObject` on the output bucket | Upload the corrected DICOM series, PDF report, and `output.json` |
| `medical-imaging:StartDICOMImportJob` | Trigger reimport of the corrected series into HealthImaging |
| `iam:PassRole` for the import role | Allow HealthImaging to assume the import role to read staged DICOM from S3 |

## Notes

- Input images with very small or negative intensity values can produce poor results, as N4 operates in log-intensity space.
- The `--shrink-factor` controls how much the image is downsampled before bias estimation. The bias field is always applied at full resolution. Higher shrink factors are faster but may reduce correction accuracy for fine-scale inhomogeneity.
- The corrected DICOM series shares the original `StudyInstanceUID`, so HealthImaging associates it with the same study as the original upload.
- Single-slice inputs (z=1) are padded in z before N4 and cropped back to a single slice afterward.

## Future Plans

- Otsu thresholding tool to precede this tool to threshold the image to separate tissue from background, passing the binary result as the mask (use `OtsuThresholdImageFilter`)
- Multi-slice PDF reports
