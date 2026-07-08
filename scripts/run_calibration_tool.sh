#!/usr/bin/env bash
# Run the calibration anchor tool (creates venv, installs Pillow — no global pip needed).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VENV="$ROOT/scripts/.venv-calibration"
TOOL="$ROOT/scripts/calibration_anchor_tool.py"

if ! python3 -c "import tkinter" 2>/dev/null; then
  echo "ERROR: tkinter no está instalado en tu Python."
  echo ""
  echo "En macOS con Homebrew ejecuta:"
  echo "  brew install python-tk@3.14"
  echo ""
  echo "Si usas otra versión de Python, ajusta el número (python3 --version)."
  exit 1
fi

if [[ ! -d "$VENV" ]]; then
  echo "Creating venv at scripts/.venv-calibration ..."
  python3 -m venv "$VENV"
fi

# shellcheck disable=SC1091
source "$VENV/bin/activate"

python -m pip install -q --upgrade pip
python -m pip install -q pillow

exec python "$TOOL" "$@"
