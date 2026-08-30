#!/bin/bash
# StockSense - FastAPI AI/OCR service (macOS/Linux)
# Mirrors start.bat: creates a project-local venv so it doesn't pollute the
# system Python, then starts uvicorn on http://localhost:8000
set -e
cd "$(dirname "$0")"

echo "=========================================="
echo " StockSense - FastAPI AI/OCR Service"
echo "=========================================="
echo ""

# ── Check Python ────────────────────────────────────────────────────────
PYTHON_BIN=""
for candidate in python3 python; do
    if command -v "$candidate" &> /dev/null; then
        PYTHON_BIN="$candidate"
        break
    fi
done

if [ -z "$PYTHON_BIN" ]; then
    echo "[ERROR] Python not found."
    echo "macOS:  brew install python3"
    echo "Linux:  sudo apt-get install python3 python3-venv"
    exit 1
fi
echo "[OK] Python: $($PYTHON_BIN --version)"

# ── Create / reuse virtual environment ────────────────────────────────────
if [ ! -d "venv" ]; then
    echo "[1/3] Creating virtual environment..."
    "$PYTHON_BIN" -m venv venv
else
    echo "[1/3] Reusing existing virtual environment..."
fi

# shellcheck disable=SC1091
source venv/bin/activate
echo "[OK] Virtual environment activated."

# ── Install dependencies ──────────────────────────────────────────────────
echo "[2/3] Installing dependencies..."
python -m pip install --upgrade pip --quiet
python -m pip install -r requirements.txt --quiet

# ── Check Tesseract ────────────────────────────────────────────────────────
echo "[3/3] Checking Tesseract..."
if command -v tesseract &> /dev/null; then
    echo "       Tesseract: $(tesseract --version 2>&1 | head -1)"
else
    echo "       WARNING: Tesseract not found"
    echo "       macOS:         brew install tesseract"
    echo "       Ubuntu/Debian: sudo apt-get install tesseract-ocr"
    echo "       PDF invoices still work fine without Tesseract."
fi

echo ""
echo "=========================================="
echo " Starting server on http://localhost:8000"
echo " API Docs: http://localhost:8000/docs"
echo " Press Ctrl+C to stop"
echo "=========================================="
echo ""

# "exec" lets Spring Boot own and stop this process cleanly.  Avoid --reload
# for automatic startup because its extra watcher process can be left behind.
exec python -m uvicorn main:app --host 127.0.0.1 --port 8000
