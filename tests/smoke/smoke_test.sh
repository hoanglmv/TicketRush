#!/usr/bin/env bash
# =============================================================================
# TicketRush — Smoke Test Runner Script
# =============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$(dirname "$DIR")")"

cd "$ROOT_DIR"

if command -v python3 &>/dev/null; then
    python3 "$DIR/test_smoke.py" "$@"
elif command -v python &>/dev/null; then
    python "$DIR/test_smoke.py" "$@"
else
    echo "[!] Không tìm thấy Python 3 để chạy Smoke Test Suite."
    exit 1
fi
