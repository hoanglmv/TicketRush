#!/usr/bin/env bash
# =============================================================================
# TicketRush — Master Test Runner Shell Script
# =============================================================================
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$DIR")"

cd "$ROOT_DIR"
python3 "$DIR/run_all_tests.py" "$@"
