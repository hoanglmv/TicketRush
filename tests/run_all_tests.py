#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
TicketRush — Master Test Runner (Toàn bộ các loại kiểm thử)
=============================================================================
Điều phối và thực thi toàn bộ các nhóm kiểm thử trong dự án:
1. [smoke]       Smoke Test           : Kiểm tra độ sẵn sàng cổng, hạ tầng & APIs
2. [unit]        Unit Test            : Kiểm tra các quy tắc nghiệp vụ cốt lõi
3. [concurrency] Concurrency Test     : Kiểm tra tranh vé đồng thời & Pessimistic Lock
4. [golden]      Golden Dataset Test  : Đánh giá RAG & AI Chatbot theo Ground Truth
5. [e2e]         E2E User Journey     : Kiểm tra luồng người dùng từ A - Z

Cách chạy:
    python3 tests/run_all_tests.py                 # Chạy toàn bộ các bộ test
    python3 tests/run_all_tests.py --suite smoke   # Chỉ chạy Smoke Test
    python3 tests/run_all_tests.py --suite golden  # Chỉ chạy Golden Test RAG
    python3 tests/run_all_tests.py --suite concurrency # Kiểm thử tranh chấp
=============================================================================
"""

import sys
import os
import time
import argparse
import subprocess
from datetime import datetime

GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"

TEST_SUITES = [
    {
        "id": "unit",
        "name": "Domain Business Rules (Unit Test)",
        "script": "tests/unit/test_business_rules.py",
        "desc": "Kiểm thử các công thức và quy tắc nghiệp vụ độc lập"
    },
    {
        "id": "smoke",
        "name": "System Health & APIs (Smoke Test)",
        "script": "tests/smoke/test_smoke.py",
        "desc": "Kiểm tra 6 ports hạ tầng, Core REST APIs và Frontend"
    },
    {
        "id": "concurrency",
        "name": "High-Concurrency Race Condition Test",
        "script": "tests/concurrency/test_concurrency_race_condition.py",
        "desc": "Mô phỏng 50 user tranh mua 1 ghế để kiểm chứng Pessimistic Lock"
    },
    {
        "id": "golden",
        "name": "Golden Dataset Evaluation (RAG Quality)",
        "script": "tests/golden/test_golden_rag.py",
        "desc": "Đánh giá chất lượng câu trả lời của AI so với Ground Truth"
    },
    {
        "id": "e2e",
        "name": "End-to-End Complete User Journey (E2E)",
        "script": "tests/e2e/test_e2e_booking_flow.py",
        "desc": "Kiểm thử luồng người dùng từ Đăng ký, Đăng nhập, Xem vé đến Chatbot"
    },
    {
        "id": "load",
        "name": "High-Concurrency Flash Sale Stress Test",
        "script": "tests/load/test_load_flash_sale.py",
        "desc": "Mô phỏng áp lực mở bán vé nóng (Flash Sale), đo lường TPS và P95/P99 latency"
    },
]

def main():
    parser = argparse.ArgumentParser(description="TicketRush Master Test Runner")
    parser.add_argument("--suite", default="all", choices=["all", "smoke", "unit", "concurrency", "golden", "e2e", "load"],
                        help="Bộ test cần chạy (Mặc định: all)")
    parser.add_argument("--backend-url", default="http://localhost:8080", help="URL Backend")
    args = parser.parse_args()

    suites_to_run = TEST_SUITES if args.suite == "all" else [s for s in TEST_SUITES if s["id"] == args.suite]

    print(f"\n{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}      🎯 TICKETRUSH — MASTER AUTOMATED TEST SUITE RUNNER                     {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"[*] Bắt đầu lúc: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"[*] Số nhóm test: {len(suites_to_run)}")
    for s in suites_to_run:
        print(f"    • [{s['id'].upper()}] {s['name']}")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}\n")

    overall_start = time.time()
    results = []

    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

    for s in suites_to_run:
        script_path = os.path.join(root_dir, s["script"])
        print(f"{BOLD}{BLUE}============================================================================={RESET}")
        print(f"{BOLD}{BLUE}▶ ĐANG CHẠY: {s['name'].upper()}{RESET}")
        print(f"  {s['desc']}")
        print(f"{BLUE}============================================================================={RESET}")

        t0 = time.time()
        cmd = [sys.executable, script_path]
        if s["id"] in ["smoke", "concurrency", "golden", "e2e", "load"]:
            cmd.extend([args.backend_url])

        res = subprocess.run(cmd, cwd=root_dir)
        dt = time.time() - t0

        passed = (res.returncode == 0)
        results.append({
            "id": s["id"],
            "name": s["name"],
            "passed": passed,
            "duration": dt
        })
        print()

    total_duration = time.time() - overall_start
    total_count = len(results)
    passed_count = sum(1 for r in results if r["passed"])
    failed_count = total_count - passed_count
    pass_rate = (passed_count / total_count) * 100 if total_count > 0 else 0

    print(f"\n{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}                  BẢNG TỔNG KẾT TOÀN DIỆN CÁC NHÓM KIỂM THỬ                  {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    for r in results:
        status_tag = f"{GREEN}✔ PASS{RESET}" if r["passed"] else f"{RED}✖ FAIL{RESET}"
        print(f"  [{status_tag}] {r['name']:<45} ({r['duration']:.2f}s)")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}")
    print(f"  • Tổng số nhóm test : {BOLD}{total_count}{RESET}")
    print(f"  • Số nhóm ĐẠT       : {BOLD}{GREEN}{passed_count}{RESET}")
    print(f"  • Số nhóm LỖI       : {BOLD}{RED if failed_count > 0 else GREEN}{failed_count}{RESET}")
    print(f"  • Tỷ lệ đạt         : {BOLD}{GREEN if pass_rate >= 80 else YELLOW}{pass_rate:.1f}%{RESET}")
    print(f"  • Tổng thời gian    : {BOLD}{total_duration:.2f} giây{RESET}")
    print(f"{CYAN}============================================================================={RESET}\n")

    sys.exit(0 if failed_count == 0 else 1)

if __name__ == "__main__":
    main()
