#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
TicketRush — End-to-End (E2E) User Journey Test Suite
=============================================================================
Kiểm thử toàn bộ hành trình người dùng từ đầu đến cuối:
- Bước 1: Đăng ký tài khoản người dùng mới (Randomized username & email)
- Bước 2: Đăng nhập lấy chuỗi JWT Bearer Token
- Bước 3: Xem thông tin cá nhân (User Profile)
- Bước 4: Khám phá danh sách sự kiện công khai (Public Events Catalog)
- Bước 5: Xem chi tiết sự kiện & sơ đồ khu vực vé (Event Details & Zones)
- Bước 6: Tương tác với Trợ lý AI Concierge tư vấn sự kiện
- Bước 7: Kiểm tra hộp vé cá nhân (My Tickets Box)
=============================================================================
"""

import sys
import os
import json
import time
import urllib.request
import urllib.error
from datetime import datetime

GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"

def http_json(url: str, method: str = "GET", data=None, token: str = None) -> tuple:
    headers = {"Content-Type": "application/json", "User-Agent": "TicketRush-E2E/1.0"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body_bytes = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body_bytes, headers=headers, method=method)
    t0 = time.time()
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            dt = (time.time() - t0) * 1000
            return resp.status, json.loads(resp.read().decode("utf-8")), dt
    except urllib.error.HTTPError as e:
        dt = (time.time() - t0) * 1000
        try:
            return e.code, json.loads(e.read().decode("utf-8")), dt
        except Exception:
            return e.code, str(e), dt
    except Exception as e:
        dt = (time.time() - t0) * 1000
        return 0, str(e), dt

def run_e2e_tests(backend_url: str = "http://localhost:8080") -> int:
    print(f"\n{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}   🔄 TICKETRUSH — END-TO-END (E2E) USER JOURNEY SUITE                     {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"[*] Backend Target : {backend_url}")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}\n")

    timestamp = int(time.time())
    username = f"e2e_user_{timestamp % 10000}"
    email = f"e2e_user_{timestamp % 10000}@example.com"
    password = "Password@123"

    passed_steps = 0
    total_steps = 7

    # BƯỚC 1: ĐĂNG KÝ
    print(f"{BOLD}[Bước 1/7] Đăng ký tài khoản người dùng mới: '{username}'{RESET}")
    reg_payload = {
        "username": username,
        "email": email,
        "password": password,
        "fullName": "Nguyễn Văn E2E",
        "phone": "0987654321",
        "dateOfBirth": "1998-05-15",
        "gender": "MALE"
    }
    status, body, dt = http_json(f"{backend_url}/api/auth/register", method="POST", data=reg_payload)
    if status == 200:
        print(f"      {GREEN}✔ PASS{RESET} ({dt:.1f}ms): Đăng ký thành công.")
        passed_steps += 1
    else:
        print(f"      {RED}✖ FAIL{RESET} ({dt:.1f}ms): Lỗi đăng ký HTTP {status} - {body}")

    # BƯỚC 2: ĐĂNG NHẬP
    print(f"{BOLD}[Bước 2/7] Đăng nhập bằng tài khoản vừa tạo để lấy JWT Token{RESET}")
    login_payload = {"username": username, "password": password}
    status, body, dt = http_json(f"{backend_url}/api/auth/login", method="POST", data=login_payload)
    user_token = None
    if status == 200 and isinstance(body, dict) and body.get("data", {}).get("accessToken"):
        user_token = body["data"]["accessToken"]
        print(f"      {GREEN}✔ PASS{RESET} ({dt:.1f}ms): Token: {user_token[:20]}...")
        passed_steps += 1
    else:
        print(f"      {RED}✖ FAIL{RESET} ({dt:.1f}ms): Đăng nhập thất bại: {body}")

    # BƯỚC 3: PROFILE
    print(f"{BOLD}[Bước 3/7] Truy vấn hồ sơ tài khoản cá nhân (/api/users/me){RESET}")
    if user_token:
        status, body, dt = http_json(f"{backend_url}/api/users/me", token=user_token)
        if status == 200 and body.get("data"):
            u = body["data"]
            print(f"      {GREEN}✔ PASS{RESET} ({dt:.1f}ms): Khớp hồ sơ: {u.get('fullName')} ({u.get('email')})")
            passed_steps += 1
        else:
            print(f"      {RED}✖ FAIL{RESET} ({dt:.1f}ms): Lỗi profile: {status} - {body}")
    else:
        print(f"      {YELLOW}⊘ SKIP{RESET}: Bỏ qua do không có JWT Token")

    # BƯỚC 4: DANH SÁCH SỰ KIỆN
    print(f"{BOLD}[Bước 4/7] Khám phá danh mục sự kiện công khai (/api/events){RESET}")
    status, body, dt = http_json(f"{backend_url}/api/events")
    events = []
    if status == 200 and isinstance(body, dict):
        events = body.get("data", [])
        print(f"      {GREEN}✔ PASS{RESET} ({dt:.1f}ms): Tải thành công {len(events)} sự kiện.")
        passed_steps += 1
    else:
        print(f"      {RED}✖ FAIL{RESET} ({dt:.1f}ms): Lỗi tải sự kiện: {status}")

    # BƯỚC 5: CHI TIẾT SỰ KIỆN & SƠ ĐỒ KHU VỰC
    selected_event_id = events[0]["id"] if events else 1
    print(f"{BOLD}[Bước 5/7] Xem chi tiết sự kiện ID {selected_event_id} & sơ đồ khu vực vé{RESET}")
    status, body, dt = http_json(f"{backend_url}/api/events/{selected_event_id}")
    if status == 200 and body.get("data"):
        ev = body["data"]
        print(f"      {GREEN}✔ PASS{RESET} ({dt:.1f}ms): Sự kiện: '{ev.get('name')[:35]}...' - Địa điểm: {ev.get('venue')}")
        passed_steps += 1
    else:
        print(f"      {RED}✖ FAIL{RESET} ({dt:.1f}ms): Lỗi chi tiết sự kiện: {status}")

    # BƯỚC 6: CHATBOT TƯ VẤN
    print(f"{BOLD}[Bước 6/7] Chatbot AI Concierge chào mừng & giải đáp thắc mắc{RESET}")
    status, body, dt = http_json(f"{backend_url}/api/chatbot/welcome")
    if status == 200 and body.get("data"):
        print(f"      {GREEN}✔ PASS{RESET} ({dt:.1f}ms): Trợ lý AI sẵn sàng hỗ trợ khách hàng.")
        passed_steps += 1
    else:
        print(f"      {RED}✖ FAIL{RESET} ({dt:.1f}ms): Lỗi kết nối chatbot: {status}")

    # BƯỚC 7: VÉ CỦA TÔI
    print(f"{BOLD}[Bước 7/7] Kiểm tra hộp lưu trữ vé của tôi (/api/tickets/my){RESET}")
    if user_token:
        status, body, dt = http_json(f"{backend_url}/api/tickets/my", token=user_token)
        if status == 200:
            tickets = body.get("data", [])
            print(f"      {GREEN}✔ PASS{RESET} ({dt:.1f}ms): Hộp vé hợp lệ (Hiện có {len(tickets)} vé đã mua).")
            passed_steps += 1
        else:
            print(f"      {RED}✖ FAIL{RESET} ({dt:.1f}ms): Lỗi lấy vé: {status} - {body}")
    else:
        print(f"      {YELLOW}⊘ SKIP{RESET}: Bỏ qua do không có JWT Token")

    print(f"\n{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}                  BẢNG TỔNG KẾT HÀNH TRÌNH NGƯỜI DÙNG (E2E)                  {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"  • Số bước thực hiện    : {total_steps}")
    print(f"  • Số bước THÀNH CÔNG   : {BOLD}{GREEN}{passed_steps}{RESET}")
    print(f"  • Số bước THẤT BẠI     : {BOLD}{RED if passed_steps < total_steps else GREEN}{total_steps - passed_steps}{RESET}")
    print(f"  • Tỷ lệ hoàn thành     : {BOLD}{GREEN if passed_steps == total_steps else YELLOW}{(passed_steps/total_steps)*100:.1f}%{RESET}")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}\n")

    return 0 if passed_steps == total_steps else 1

if __name__ == "__main__":
    b_url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
    sys.exit(run_e2e_tests(backend_url=b_url))
