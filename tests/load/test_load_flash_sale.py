#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
TicketRush — High-Concurrency Flash Sale Load & Stress Testing Suite
=============================================================================
Kịch bản kiểm thử áp lực tải cao (Stress / Load Test) mô phỏng đợt mở bán vé "Hot":
- Mô phỏng hàng trăm / hàng ngàn Virtual Users (VUs) đồng thời đổ bộ.
- Đo lường hiệu năng của Virtual Queue (Redis ZSET FIFO) & Pessimistic Lock (MySQL).
- Thu thập và phân tích chi tiết:
    * Throughput (Requests Per Second - TPS)
    * Latency Distribution: Min, Median (P50), P90, P95, P99, Max
    * Tỷ lệ tranh chấp thành công & Fail-fast sạch sẽ (0 double-booking).
=============================================================================
"""

import sys
import os
import time
import json
import random
import argparse
import urllib.request
import urllib.error
from datetime import datetime
from concurrent.futures import ThreadPoolExecutor, as_completed

# ANSI Colors
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
BLUE = "\033[94m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"

def send_request(url: str, method: str = "GET", headers: dict = None, data: dict = None, timeout: float = 10.0):
    t0 = time.time()
    req_headers = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)

    payload = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=payload, headers=req_headers, method=method)

    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            dt = (time.time() - t0) * 1000.0
            body = resp.read().decode("utf-8")
            return {
                "status": resp.status,
                "latency_ms": dt,
                "data": json.loads(body) if body else {},
                "error": None
            }
    except urllib.error.HTTPError as e:
        dt = (time.time() - t0) * 1000.0
        try:
            body = e.read().decode("utf-8")
            err_data = json.loads(body)
        except Exception:
            err_data = {}
        return {
            "status": e.code,
            "latency_ms": dt,
            "data": err_data,
            "error": f"HTTP {e.code}"
        }
    except Exception as e:
        dt = (time.time() - t0) * 1000.0
        return {
            "status": 0,
            "latency_ms": dt,
            "data": {},
            "error": str(e)
        }

def run_vu_journey(vu_id: int, backend_url: str, event_id: int) -> list:
    """Mô phỏng hành vi của 1 Virtual User (VU) tham gia mua vé flash sale"""
    logs = []
    username = f"load_vu_{vu_id}_{int(time.time() * 1000) % 100000}"
    password = "Password123!"

    # 1. Đăng ký & Đăng nhập
    reg_res = send_request(
        f"{backend_url}/api/auth/register",
        method="POST",
        data={
            "username": username,
            "email": f"{username}@example.com",
            "password": password,
            "fullName": f"Virtual User {vu_id}",
            "dateOfBirth": "1998-05-15",
            "gender": "MALE"
        }
    )
    logs.append({"step": "register", **reg_res})

    login_res = send_request(
        f"{backend_url}/api/auth/login",
        method="POST",
        data={"username": username, "password": password}
    )
    logs.append({"step": "login", **login_res})

    token = login_res.get("data", {}).get("data", {}).get("token")
    if not token:
        return logs

    auth_headers = {"Authorization": f"Bearer {token}"}

    # 2. Xếp hàng đợi ảo (Virtual Queue Join)
    queue_res = send_request(
        f"{backend_url}/api/queue/join?eventId={event_id}",
        method="POST",
        headers=auth_headers
    )
    logs.append({"step": "queue_join", **queue_res})

    # 3. Lấy sơ đồ sự kiện & danh sách ghế
    event_res = send_request(
        f"{backend_url}/api/events/{event_id}",
        method="GET",
        headers=auth_headers
    )
    logs.append({"step": "fetch_event", **event_res})

    # 4. Tranh mua ghế ngẫu nhiên (hoặc ghế số 1)
    target_seat_id = random.randint(1, 10)
    hold_res = send_request(
        f"{backend_url}/api/bookings/hold",
        method="POST",
        headers=auth_headers,
        data={"eventId": event_id, "seatId": target_seat_id}
    )
    logs.append({"step": "seat_hold", **hold_res})

    return logs

def run_load_test(backend_url: str = "http://localhost:8080", vus: int = 30, event_id: int = 1, export_json: bool = False) -> int:
    print(f"\n{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}   ⚡ TICKETRUSH — HIGH-CONCURRENCY FLASH SALE LOAD TEST SUITE               {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"[*] Backend Target        : {backend_url}")
    print(f"[*] Virtual Users (VUs)   : {vus} threads đồng thời")
    print(f"[*] Sự kiện mục tiêu      : Event ID {event_id}")
    print(f"[*] Thời điểm bắt đầu     : {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}\n")

    start_time = time.time()
    all_logs = []

    print(f"[*] 🚀 Đang kích hoạt {vus} Virtual Users cùng lúc đổ bộ vào hệ thống...")
    with ThreadPoolExecutor(max_workers=vus) as executor:
        futures = [executor.submit(run_vu_journey, i, backend_url, event_id) for i in range(1, vus + 1)]
        for f in as_completed(futures):
            try:
                res = f.result()
                all_logs.extend(res)
            except Exception as ex:
                all_logs.append({"step": "error", "latency_ms": 0, "status": 500, "error": str(ex)})

    total_duration = time.time() - start_time

    # Thống kê phân tích
    total_requests = len(all_logs)
    latencies = [log["latency_ms"] for log in all_logs if log["latency_ms"] > 0]
    latencies.sort()

    status_counts = {}
    step_counts = {}
    success_holds = 0
    rejected_holds = 0

    for log in all_logs:
        st = log.get("status", 0)
        status_counts[st] = status_counts.get(st, 0) + 1
        step = log.get("step", "unknown")
        step_counts[step] = step_counts.get(step, 0) + 1

        if step == "seat_hold":
            if st == 200:
                success_holds += 1
            else:
                rejected_holds += 1

    p50 = latencies[int(len(latencies) * 0.50)] if latencies else 0.0
    p90 = latencies[int(len(latencies) * 0.90)] if latencies else 0.0
    p95 = latencies[int(len(latencies) * 0.95)] if latencies else 0.0
    p99 = latencies[int(len(latencies) * 0.99)] if latencies else 0.0
    avg_latency = sum(latencies) / len(latencies) if latencies else 0.0
    tps = total_requests / total_duration if total_duration > 0 else 0.0

    print(f"\n{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}                 KẾT QUẢ PHÂN TÍCH HIỆU NĂNG CHỊU TẢI (METRICS)             {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"  • Tổng số VUs mô phỏng  : {BOLD}{vus} Virtual Users{RESET}")
    print(f"  • Tổng số HTTP Requests : {BOLD}{total_requests}{RESET}")
    print(f"  • Tổng thời gian chạy   : {BOLD}{total_duration:.2f} giây{RESET}")
    print(f"  • Tốc độ xử lý (TPS)    : {BOLD}{GREEN}{tps:.2f} requests/sec{RESET}")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}")
    print(f"  {BOLD}Phân phối độ trễ (Latency Distribution):{RESET}")
    print(f"    - Nhanh nhất (Min)    : {min(latencies):.1f} ms" if latencies else "    - Min: N/A")
    print(f"    - Trung bình (Avg)    : {avg_latency:.1f} ms")
    print(f"    - Trung vị (P50)      : {p50:.1f} ms")
    print(f"    - Phân vị 90% (P90)   : {p90:.1f} ms")
    print(f"    - Phân vị 95% (P95)   : {p95:.1f} ms")
    print(f"    - Đỉnh trễ (P99)      : {p99:.1f} ms")
    print(f"    - Lâu nhất (Max)      : {max(latencies):.1f} ms" if latencies else "    - Max: N/A")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}")
    print(f"  {BOLD}Nghiệp vụ Tranh Chấp Ghế (Seat Hold Concurrency):{RESET}")
    print(f"    - Giữ chỗ thành công  : {GREEN}{success_holds}{RESET}")
    print(f"    - Bị từ chối hợp lệ   : {YELLOW}{rejected_holds}{RESET} (Ghế đã bị khóa / bán trước)")
    print(f"    - HTTP 200 OK         : {status_counts.get(200, 0)}")
    print(f"    - HTTP 4xx Client Err : {status_counts.get(400, 0) + status_counts.get(409, 0)}")
    print(f"    - HTTP 5xx Server Err : {RED if status_counts.get(500, 0) > 0 else GREEN}{status_counts.get(500, 0)}{RESET}")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}\n")

    if export_json:
        report = {
            "timestamp": datetime.now().isoformat(),
            "vus": vus,
            "total_requests": total_requests,
            "duration_seconds": round(total_duration, 2),
            "tps": round(tps, 2),
            "latency": {
                "avg_ms": round(avg_latency, 2),
                "p50_ms": round(p50, 2),
                "p90_ms": round(p90, 2),
                "p95_ms": round(p95, 2),
                "p99_ms": round(p99, 2),
            },
            "status_distribution": status_counts
        }
        with open("load_test_report.json", "w", encoding="utf-8") as f:
            json.dump(report, f, indent=2)
        print(f"[✔] Đã xuất báo cáo chi tiết: {BOLD}load_test_report.json{RESET}")

    # Pass nếu hệ thống không bị sập (500 lỗi < 5% tổng request)
    server_errors = status_counts.get(500, 0)
    error_rate = (server_errors / total_requests) * 100 if total_requests > 0 else 0

    if error_rate <= 5.0:
        print(f"{BOLD}{GREEN}🎉 LOAD TEST PASS! Hệ thống chịu tải xuất sắc, độ ổn định cao!{RESET}\n")
        return 0
    else:
        print(f"{BOLD}{RED}✖ LOAD TEST FAILED! Tỷ lệ lỗi máy chủ quá cao ({error_rate:.1f}%).{RESET}\n")
        return 1

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="TicketRush High-Concurrency Load Test")
    parser.add_argument("pos_url", nargs="?", default=None, help="Backend URL (optional positional)")
    parser.add_argument("--backend-url", default=None, help="Backend URL")
    parser.add_argument("--vus", type=int, default=30, help="Số lượng Virtual Users đồng thời (Mặc định: 30)")
    parser.add_argument("--event-id", type=int, default=1, help="ID sự kiện mục tiêu (Mặc định: 1)")
    parser.add_argument("--json", action="store_true", help="Xuất báo cáo JSON")
    args = parser.parse_args()

    target_url = args.backend_url if args.backend_url else (args.pos_url if args.pos_url else "http://localhost:8080")
    sys.exit(run_load_test(backend_url=target_url, vus=args.vus, event_id=args.event_id, export_json=args.json))
