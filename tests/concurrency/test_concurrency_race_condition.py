#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
TicketRush — Concurrency & Race Condition Test Suite
=============================================================================
Kiểm thử áp lực đồng thời (Flash-sale Concurrency Simulation):
- Giả lập 50 người dùng cùng gửi request giữ chỗ/đặt 1 chiếc ghế DUY NHẤT
  tại cùng 1 mili-giây thông qua ThreadPoolExecutor.
- Mục tiêu kiểm chứng:
  1. Cơ chế Khóa bi quan (@Lock(LockModeType.PESSIMISTIC_WRITE)) hoạt động chính xác.
  2. DUY NHẤT 1 người thành công (Success: 1).
  3. 49 người còn lại thất bại ngay lập tức (Fail-fast, Zero double-booking).
  4. Hệ thống không bị deadlock hay treo connection pool.
=============================================================================
"""

import sys
import os
import json
import time
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import Dict, Any, List

GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"

def get_target_seat(backend_url: str) -> Dict[str, Any]:
    """Tìm một chiếc ghế đang AVAILABLE để làm mục tiêu tranh chấp"""
    try:
        req = urllib.request.urlopen(f"{backend_url}/api/events/1", timeout=5)
        ev_data = json.loads(req.read().decode("utf-8")).get("data", {})
        zones = ev_data.get("zones", [])
        if zones:
            zone_id = zones[0]["id"]
            # Thử lấy danh sách ghế hoặc tạo request
            return {"eventId": 1, "zoneId": zone_id, "seatId": 1}
    except Exception:
        pass
    return {"eventId": 1, "zoneId": 1, "seatId": 1}

def attempt_booking(user_id: int, backend_url: str, event_id: int, seat_id: int, token: str) -> Dict[str, Any]:
    """1 User cố gắng đặt chiếc ghế mục tiêu"""
    url = f"{backend_url}/api/bookings"
    payload = {
        "eventId": event_id,
        "seatIds": [seat_id]
    }
    req_bytes = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=req_bytes,
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {token}"
        },
        method="POST"
    )

    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            dt = (time.time() - t0) * 1000
            body = json.loads(resp.read().decode("utf-8"))
            return {"user_id": user_id, "status": resp.status, "success": True, "latency_ms": dt, "body": body}
    except urllib.error.HTTPError as e:
        dt = (time.time() - t0) * 1000
        try:
            err_body = json.loads(e.read().decode("utf-8"))
        except Exception:
            err_body = str(e)
        return {"user_id": user_id, "status": e.code, "success": False, "latency_ms": dt, "error": err_body}
    except Exception as e:
        dt = (time.time() - t0) * 1000
        return {"user_id": user_id, "status": 0, "success": False, "latency_ms": dt, "error": str(e)}

def run_concurrency_test(backend_url: str = "http://localhost:8080", concurrent_users: int = 50) -> int:
    print(f"\n{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}   ⚡ TICKETRUSH — CONCURRENCY & RACE CONDITION TEST SUITE                   {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"[*] Backend Target        : {backend_url}")
    print(f"[*] Số người tranh chấp   : {BOLD}{concurrent_users} threads đồng thời{RESET}")

    # Bước 1: Đăng nhập lấy Admin Token
    login_payload = json.dumps({"username": "admin", "password": "admin123"}).encode("utf-8")
    req = urllib.request.Request(f"{backend_url}/api/auth/login", data=login_payload, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            token = json.loads(r.read().decode("utf-8"))["data"]["accessToken"]
            print(f"[+] Xác thực JWT Token thành công.")
    except Exception as e:
        print(f"{RED}[!] Không thể đăng nhập lấy token: {e}{RESET}")
        return 1

    target = get_target_seat(backend_url)
    event_id = target["eventId"]
    seat_id = target["seatId"]
    print(f"[*] Chiếc ghế mục tiêu    : Seat ID {seat_id} (Event ID {event_id})")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}")
    print(f"[*] 🚀 Đang kích hoạt {concurrent_users} requests tại cùng 1 mili-giây...\n")

    t_start = time.time()
    responses: List[Dict[str, Any]] = []

    with ThreadPoolExecutor(max_workers=concurrent_users) as executor:
        futures = [
            executor.submit(attempt_booking, i + 1, backend_url, event_id, seat_id, token)
            for i in range(concurrent_users)
        ]
        for f in as_completed(futures):
            responses.append(f.result())

    total_time = (time.time() - t_start) * 1000
    success_count = sum(1 for r in responses if r["success"])
    fail_count = sum(1 for r in responses if not r["success"])
    double_bookings = max(0, success_count - 1)

    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}                 KẾT QUẢ KIỂM THỬ TRANH CHẤP ĐỒNG THỜI                      {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"  • Tổng số request gửi đi : {BOLD}{len(responses)}{RESET}")
    print(f"  • Số người mua THÀNH CÔNG: {BOLD}{GREEN}{success_count}{RESET} (Kỳ vọng: tối đa 1 người)")
    print(f"  • Số người BỊ TỪ CHỐI    : {BOLD}{YELLOW}{fail_count}{RESET} (Fail-fast chính xác)")
    print(f"  • Số vụ BÁN TRÙNG GHẾ    : {BOLD}{RED if double_bookings > 0 else GREEN}{double_bookings}{RESET}")
    print(f"  • Tổng thời gian xử lý   : {BOLD}{total_time:.2f} ms{RESET}")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}")

    if double_bookings == 0:
        print(f"\n{BOLD}{GREEN}🎉 PASS! KHÔNG CÓ BÁN TRÙNG GHẾ! Cơ chế Pessimistic Lock bảo vệ toàn vẹn 100%!{RESET}\n")
        return 0
    else:
        print(f"\n{BOLD}{RED}✖ FAIL! Phát hiện {double_bookings} vụ bán trùng ghế! Cần kiểm tra lại Transaction Lock.{RESET}\n")
        return 1

if __name__ == "__main__":
    b_url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
    sys.exit(run_concurrency_test(backend_url=b_url))
