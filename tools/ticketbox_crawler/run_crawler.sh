#!/usr/bin/env bash
# =============================================================================
# TicketRush — Script Chạy Ticketbox Data Crawler Tool
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$ROOT_DIR"

echo "============================================================================="
echo "   Khởi chạy Ticketbox Data Crawler Tool cho TicketRush"
echo "============================================================================="

# Đảm bảo cài đặt thư viện requests
if ! python3 -c "import requests" &> /dev/null; then
    echo "[*] Đang cài đặt thư viện requests..."
    pip install requests || python3 -m pip install requests
fi

# Chạy crawler agent
python3 tools/ticketbox_crawler/crawler.py "$@"

echo ""
echo "[*] Để kiểm tra dữ liệu sự kiện vừa tạo trên hệ thống:"
echo "    - Mở trình duyệt: http://localhost:5173"
echo "    - Hoặc gọi API: curl http://localhost:8080/api/events | jq ."
echo "============================================================================="
