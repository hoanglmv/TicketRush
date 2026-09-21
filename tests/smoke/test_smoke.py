#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
TicketRush — Automated End-to-End System Smoke Test Suite
=============================================================================
Kịch bản kiểm thử nhanh (Smoke Test) toàn diện cho hệ thống TicketRush:
- Stage 1: Kiểm tra kết nối Ports & Hạ tầng (Backend, Frontend, MySQL, Redis, Kafka, Qdrant)
- Stage 2: Kiểm tra Frontend Web Server (Vite Bundle)
- Stage 3: Kiểm tra Backend Core APIs (Public Events, Details, Auth Login, Admin)
- Stage 4: Kiểm tra AI Agent & RAG Pipeline (Qdrant Semantic Search, LLM Inference, Metrics)
- Stage 5: Tổng kết Scorecard đạt chuẩn CI/CD

Chạy kiểm thử:
    python3 tests/smoke/test_smoke.py
    # Hoặc xuất báo cáo JSON:
    python3 tests/smoke/test_smoke.py --json
=============================================================================
"""

import sys
import os
import json
import time
import socket
import argparse
import urllib.request
import urllib.error
from datetime import datetime
from typing import Dict, Any, Tuple, Optional

# ANSI Color Codes
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"

TEST_RESULTS = []

def record_test(name: str, passed: bool, message: str = "", duration_ms: float = 0.0):
    TEST_RESULTS.append({
        "name": name,
        "passed": passed,
        "message": message,
        "duration_ms": round(duration_ms, 2)
    })
    status_icon = f"{GREEN}✔ PASS{RESET}" if passed else f"{RED}✖ FAIL{RESET}"
    time_str = f"{CYAN}({duration_ms:.1f}ms){RESET}" if duration_ms > 0 else ""
    print(f"  [{status_icon}] {name} {time_str}")
    if message:
        indent = "         "
        color = GREEN if passed else YELLOW if not passed else RESET
        print(f"{indent}{color}{message}{RESET}")

def check_tcp_port(host: str, port: int, timeout: float = 2.0) -> bool:
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(timeout)
    try:
        s.connect((host, port))
        s.close()
        return True
    except Exception:
        return False

def http_request(url: str, method: str = "GET", data: Optional[Dict] = None, 
                 headers: Optional[Dict[str, str]] = None, timeout: float = 8.0) -> Tuple[int, Any, float]:
    start_time = time.time()
    req_headers = {
        "Content-Type": "application/json",
        "User-Agent": "TicketRush-SmokeTest/1.0"
    }
    if headers:
        req_headers.update(headers)

    body_bytes = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=body_bytes, headers=req_headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            duration_ms = (time.time() - start_time) * 1000
            resp_body = response.read().decode("utf-8")
            try:
                parsed_json = json.loads(resp_body)
                return response.status, parsed_json, duration_ms
            except Exception:
                return response.status, resp_body, duration_ms
    except urllib.error.HTTPError as e:
        duration_ms = (time.time() - start_time) * 1000
        error_body = e.read().decode("utf-8")
        try:
            return e.code, json.loads(error_body), duration_ms
        except Exception:
            return e.code, error_body, duration_ms
    except Exception as e:
        duration_ms = (time.time() - start_time) * 1000
        return 0, str(e), duration_ms


def run_smoke_tests(args):
    backend_url = args.backend_url.rstrip("/")
    frontend_url = args.frontend_url.rstrip("/")
    qdrant_url = args.qdrant_url.rstrip("/")

    print(f"\n{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}   🚀 TICKETRUSH — AUTOMATED SYSTEM SMOKE TEST SUITE                        {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"[*] Thời gian kiểm thử: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"[*] Backend Target    : {backend_url}")
    print(f"[*] Frontend Target   : {frontend_url}")
    print(f"[*] Qdrant Target     : {qdrant_url}")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}\n")

    overall_start = time.time()

    # =========================================================================
    # STAGE 1: INFRASTRUCTURE & NETWORK PORTS
    # =========================================================================
    print(f"{BOLD}{BLUE}▶ STAGE 1: KIỂM TRA HẠ TẦNG CỔNG KẾT NỐI (INFRASTRUCTURE PORTS){RESET}")
    ports = [
        ("Backend REST API", "127.0.0.1", 8080),
        ("Frontend Web App", "127.0.0.1", 5173),
        ("MySQL Database", "127.0.0.1", 3307),
        ("Redis Cache & Queue", "127.0.0.1", 6380),
        ("Qdrant Vector DB", "127.0.0.1", 6333),
        ("Kafka Broker", "127.0.0.1", 9092),
    ]
    for name, host, port in ports:
        t0 = time.time()
        is_open = check_tcp_port(host, port)
        dt = (time.time() - t0) * 1000
        msg = f"Đã kết nối {host}:{port}" if is_open else f"Không thể kết nối cổng {host}:{port} (Service có thể chưa khởi động)"
        record_test(f"Port {port} ({name})", is_open, msg, dt)
    print()

    # =========================================================================
    # STAGE 2: FRONTEND WEB APPLICATION
    # =========================================================================
    print(f"{BOLD}{BLUE}▶ STAGE 2: KIỂM TRA FRONTEND WEB APPLICATION{RESET}")
    status, body, dt = http_request(frontend_url, method="GET")
    fe_pass = (status == 200 and ("<html" in str(body).lower() or "<!doctype" in str(body).lower()))
    record_test("Frontend Index Bundle (HTTP 200 OK)", fe_pass, 
                f"Status: {status}, HTML Bundle trả về hợp lệ" if fe_pass else f"Lỗi HTTP {status}: {body[:80]}", dt)
    print()

    # =========================================================================
    # STAGE 3: BACKEND REST APIS & CORE BUSINESS
    # =========================================================================
    print(f"{BOLD}{BLUE}▶ STAGE 3: KIỂM TRA BACKEND CORE APIS & NGHIỆP VỤ{RESET}")
    
    # 3.1 Public Events List
    status, body, dt = http_request(f"{backend_url}/api/events")
    has_events = False
    event_count = 0
    categories = {}
    if status == 200 and isinstance(body, dict):
        data = body.get("data", [])
        event_count = len(data)
        has_events = event_count > 0
        for e in data:
            c = e.get("category", "UNKNOWN")
            categories[c] = categories.get(c, 0) + 1
        cat_summary = ", ".join([f"{k}: {v}" for k, v in categories.items()])
        msg = f"Tìm thấy {event_count} sự kiện ({cat_summary})"
    else:
        msg = f"Lỗi API: status {status}"
    record_test("Public Events API (GET /api/events)", has_events, msg, dt)

    # 3.2 Single Event Details & Zones
    status, body, dt = http_request(f"{backend_url}/api/events/1")
    event_detail_ok = False
    if status == 200 and isinstance(body, dict) and body.get("data"):
        ev = body["data"]
        name = ev.get("name", "N/A")
        event_detail_ok = True
        msg = f"Chi tiết Event ID 1: '{name[:30]}...' - Ngày: {ev.get('eventDate')}"
    else:
        msg = f"Không lấy được thông tin sự kiện ID 1 (status {status})"
    record_test("Event Detail & Zones (GET /api/events/1)", event_detail_ok, msg, dt)

    # 3.3 Auth Login (Admin)
    admin_token = None
    login_payload = {"username": args.admin_user, "password": args.admin_pass}
    status, body, dt = http_request(f"{backend_url}/api/auth/login", method="POST", data=login_payload)
    login_pass = False
    if status == 200 and isinstance(body, dict) and body.get("data", {}).get("accessToken"):
        admin_token = body["data"]["accessToken"]
        role = body["data"].get("role", "ROLE_ADMIN")
        login_pass = True
        msg = f"Đăng nhập Admin thành công (Role: {role}, Token: {admin_token[:18]}...)"
    else:
        msg = f"Đăng nhập thất bại: status {status}, response: {str(body)[:60]}"
    record_test("Admin Authentication (POST /api/auth/login)", login_pass, msg, dt)

    # 3.4 Protected Admin Events
    if admin_token:
        status, body, dt = http_request(
            f"{backend_url}/api/admin/events", 
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        admin_api_ok = (status == 200)
        msg = f"Xác thực RBAC Admin thành công, HTTP {status}" if admin_api_ok else f"Lỗi Admin API: {status}"
    else:
        admin_api_ok = False
        dt = 0
        msg = "Bỏ qua do không có Admin JWT Token"
    record_test("Admin Protected API (GET /api/admin/events)", admin_api_ok, msg, dt)
    print()

    # =========================================================================
    # STAGE 4: AI CONCIERGE & RAG ENGINE
    # =========================================================================
    print(f"{BOLD}{BLUE}▶ STAGE 4: KIỂM TRA AI AGENT & VECTOR RAG PIPELINE{RESET}")

    # 4.1 Qdrant Vector DB Status
    status, body, dt = http_request(f"{qdrant_url}/collections")
    qdrant_ok = (status == 200 and isinstance(body, dict))
    if qdrant_ok:
        collections = [c.get("name") for c in body.get("result", {}).get("collections", [])]
        msg = f"Qdrant trực tuyến. Collections: {collections}"
    else:
        msg = f"Qdrant không phản hồi (Status: {status})"
    record_test("Qdrant Vector DB Status (GET /collections)", qdrant_ok, msg, dt)

    # 4.2 Chatbot Welcome Endpoint
    status, body, dt = http_request(f"{backend_url}/api/chatbot/welcome")
    welcome_ok = False
    if status == 200 and isinstance(body, dict) and body.get("data"):
        welcome_ok = True
        reply = body["data"].get("reply", "")
        suggestions = body["data"].get("suggestions", [])
        msg = f"Welcome prompt nạp tốt ({len(reply)} ký tự, {len(suggestions)} gợi ý)"
    else:
        msg = f"Welcome endpoint lỗi (status {status})"
    record_test("Chatbot Welcome API (GET /api/chatbot/welcome)", welcome_ok, msg, dt)

    # 4.3 Chatbot RAG Semantic Query
    query_payload = {"message": "Concert Anh Trai Say Hi đêm 3 diễn ra ở đâu?"}
    status, body, dt = http_request(f"{backend_url}/api/chatbot/message", method="POST", data=query_payload, timeout=12.0)
    chat_ok = False
    if status == 200 and isinstance(body, dict) and body.get("data"):
        d = body["data"]
        reply = d.get("reply", "")
        model_used = d.get("modelUsed", "unknown")
        rag_score = d.get("ragScore")
        latency = d.get("latencyMs", dt)
        chat_ok = len(reply) > 10
        msg = f"Model: '{model_used}' | Latency: {latency}ms | RAG Score: {rag_score}\n         Trả lời: \"{reply[:70]}...\""
    else:
        msg = f"Lỗi chatbot: status {status}"
    record_test("Chatbot RAG Inference (POST /api/chatbot/message)", chat_ok, msg, dt)

    # 4.4 Chatbot Metrics & Audit Logs
    status, body, dt = http_request(f"{backend_url}/api/chatbot/metrics")
    metrics_ok = False
    if status == 200 and isinstance(body, dict) and body.get("data"):
        metrics_ok = True
        m = body["data"]
        total_q = m.get("totalQueries", 0)
        hit_rate = m.get("ragHitRate", 0.0)
        satisfaction = m.get("userSatisfactionRate", 0.0)
        msg = f"Tổng truy vấn: {total_q} | RAG Hit Rate: {hit_rate}% | Hài lòng: {satisfaction}%"
    else:
        msg = f"Metrics endpoint lỗi (status {status})"
    record_test("AI Metrics & Audit Logs (GET /api/chatbot/metrics)", metrics_ok, msg, dt)
    print()

    # =========================================================================
    # STAGE 5: SCORECARD SUMMARY
    # =========================================================================
    total_duration = time.time() - overall_start
    total_tests = len(TEST_RESULTS)
    passed_tests = sum(1 for t in TEST_RESULTS if t["passed"])
    failed_tests = total_tests - passed_tests
    pass_percent = (passed_tests / total_tests) * 100 if total_tests > 0 else 0

    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}                      BẢNG TỔNG KẾT KẾT QUẢ SMOKE TEST                      {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"  • Tổng số bài kiểm thử : {BOLD}{total_tests}{RESET}")
    print(f"  • Số bài kiểm thử đạt  : {BOLD}{GREEN}{passed_tests}{RESET}")
    print(f"  • Số bài kiểm thử lỗi  : {BOLD}{RED if failed_tests > 0 else GREEN}{failed_tests}{RESET}")
    print(f"  • Tỷ lệ hoàn thành     : {BOLD}{GREEN if pass_percent >= 80 else YELLOW}{pass_percent:.1f}%{RESET}")
    print(f"  • Tổng thời gian chạy  : {BOLD}{total_duration:.2f} giây{RESET}")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}")

    if args.json:
        report = {
            "timestamp": datetime.now().isoformat(),
            "total_duration_seconds": round(total_duration, 2),
            "summary": {
                "total": total_tests,
                "passed": passed_tests,
                "failed": failed_tests,
                "pass_rate_percent": round(pass_percent, 2)
            },
            "tests": TEST_RESULTS
        }
        with open("smoke_test_report.json", "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2)
        print(f"[✔] Đã xuất báo cáo chi tiết: {BOLD}smoke_test_report.json{RESET}")

    if failed_tests == 0:
        print(f"\n{BOLD}{GREEN}🎉 TẤT CẢ CÁC THÀNH PHẦN CỦA DỰ ÁN ĐỀU HOẠT ĐỘNG HOÀN HẢO!{RESET}\n")
        return 0
    else:
        print(f"\n{BOLD}{YELLOW}⚠️  CÓ {failed_tests} THÀNH PHẦN CẦN LƯU Ý. Hãy kiểm tra lại container tương ứng!{RESET}\n")
        return 1

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="TicketRush Smoke Test Suite")
    parser.add_argument("pos_url", nargs="?", default=None, help="Optional positional Backend URL")
    parser.add_argument("--backend-url", default=None, help="Backend API base URL")
    parser.add_argument("--frontend-url", default="http://localhost:5173", help="Frontend Web base URL")
    parser.add_argument("--qdrant-url", default="http://localhost:6333", help="Qdrant Vector DB base URL")
    parser.add_argument("--admin-user", default="admin", help="Admin username")
    parser.add_argument("--admin-pass", default="admin123", help="Admin password")
    parser.add_argument("--json", action="store_true", help="Export test report to smoke_test_report.json")
    args = parser.parse_args()

    if not args.backend_url:
        args.backend_url = args.pos_url if args.pos_url else "http://localhost:8080"

    sys.exit(run_smoke_tests(args))
