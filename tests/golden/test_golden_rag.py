#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
=============================================================================
TicketRush — Golden Dataset Evaluation Test Suite (RAG Quality Assessment)
=============================================================================
Kiểm thử đánh giá độ chính xác của RAG và AI Agent so với Ground Truth (Golden Dataset):
- Đọc bộ câu hỏi chuẩn trong golden_dataset.json
- Gửi từng câu hỏi tới API /api/chatbot/message
- Chấm điểm Keyword Coverage, Semantic Retrieval Match, và Latency
- Xuất báo cáo Scorecard chi tiết cho từng câu hỏi
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

def run_golden_test(backend_url: str = "http://localhost:8080", dataset_path: str = None) -> int:
    if not dataset_path:
        curr_dir = os.path.dirname(os.path.abspath(__file__))
        dataset_path = os.path.join(curr_dir, "golden_dataset.json")

    if not os.path.exists(dataset_path):
        print(f"{RED}[!] Không tìm thấy file Golden Dataset tại {dataset_path}{RESET}")
        return 1

    with open(dataset_path, "r", encoding="utf-8") as f:
        dataset = json.load(f)

    print(f"\n{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}   🎯 TICKETRUSH — GOLDEN DATASET RAG EVALUATION SUITE                     {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"[*] Tập dữ liệu chuẩn : {len(dataset)} câu hỏi kiểm định (Ground Truth)")
    print(f"[*] API Endpoint      : {backend_url}/api/chatbot/message")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}\n")

    passed_count = 0
    total_latency = 0.0
    results = []

    for idx, item in enumerate(dataset, 1):
        qid = item.get("id", f"Q-{idx}")
        question = item["question"]
        expected_kws = item.get("expected_keywords", [])
        ground_truth = item.get("ground_truth", "")

        print(f"{BOLD}[{qid}] Câu hỏi: {question}{RESET}")

        payload = {"message": question}
        req_bytes = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            f"{backend_url}/api/chatbot/message",
            data=req_bytes,
            headers={"Content-Type": "application/json"},
            method="POST"
        )

        t0 = time.time()
        reply = ""
        model_used = "N/A"
        rag_score = 0.0
        status_code = 0

        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                status_code = resp.status
                body = json.loads(resp.read().decode("utf-8"))
                dt = (time.time() - t0) * 1000
                total_latency += dt

                data = body.get("data", {})
                reply = data.get("reply", "")
                model_used = data.get("modelUsed", "rule-fallback")
                rag_score = data.get("ragScore", 0.0)
        except Exception as e:
            dt = (time.time() - t0) * 1000
            reply = f"ERROR: {e}"

        # Đánh giá tỷ lệ từ khóa cốt lõi (Keyword Match Rate)
        matched_kws = [kw for kw in expected_kws if kw.lower() in reply.lower()]
        kw_ratio = len(matched_kws) / len(expected_kws) if expected_kws else 1.0

        # Tiêu chuẩn ĐẠT: Có câu trả lời (> 20 ký tự) và khớp >= 50% từ khóa cốt lõi
        is_pass = len(reply) > 20 and kw_ratio >= 0.5
        if is_pass:
            passed_count += 1

        status_str = f"{GREEN}PASS{RESET}" if is_pass else f"{RED}FAIL{RESET}"
        print(f"      • Trạng thái : [{status_str}] ({dt:.1f}ms) | Model: {CYAN}{model_used}{RESET} | RAG Score: {rag_score:.2f}")
        print(f"      • Độ phủ KWs : {len(matched_kws)}/{len(expected_kws)} ({int(kw_ratio*100)}%) -> Khớp: {matched_kws}")
        print(f"      • Trích đoạn : {reply[:90]}...\n")

        results.append({
            "id": qid,
            "question": question,
            "passed": is_pass,
            "latency_ms": round(dt, 2),
            "model_used": model_used,
            "rag_score": rag_score,
            "keyword_match_ratio": round(kw_ratio, 2)
        })

    avg_latency = total_latency / len(dataset) if dataset else 0
    pass_rate = (passed_count / len(dataset)) * 100 if dataset else 0

    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"{BOLD}{CYAN}                  BẢNG TỔNG KẾT ĐÁNH GIÁ GOLDEN DATASET                     {RESET}")
    print(f"{BOLD}{CYAN}============================================================================={RESET}")
    print(f"  • Tổng số câu kiểm định : {BOLD}{len(dataset)}{RESET}")
    print(f"  • Số câu trả lời ĐẠT    : {BOLD}{GREEN}{passed_count}{RESET}")
    print(f"  • Số câu CHƯA ĐẠT       : {BOLD}{RED if passed_count < len(dataset) else GREEN}{len(dataset) - passed_count}{RESET}")
    print(f"  • Tỷ lệ chính xác       : {BOLD}{GREEN if pass_rate >= 75 else YELLOW}{pass_rate:.1f}%{RESET}")
    print(f"  • Độ trễ trung bình     : {BOLD}{avg_latency:.1f} ms{RESET}")
    print(f"{CYAN}-----------------------------------------------------------------------------{RESET}\n")

    return 0 if pass_rate >= 60 else 1

if __name__ == "__main__":
    b_url = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
    sys.exit(run_golden_test(backend_url=b_url))
