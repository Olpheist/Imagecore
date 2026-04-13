#!/bin/bash
set -euo pipefail
echo "[diag] container started" && sync
python -c "import sys; print('[diag] python ok', flush=True)"
python -c "import numpy; print('[diag] numpy ok', flush=True)"
python -c "import itk; print('[diag] itk ok', flush=True)"
python -c "import pydicom; print('[diag] pydicom ok', flush=True)"
echo "[diag] launching tool" && sync
exec n4-bias-correction "$@"
