# 🧪 TICKETRUSH — AUTOMATED TEST SUITE FRAMEWORK

Thư mục `tests/` chứa toàn bộ các bài kiểm thử đa tầng của dự án **TicketRush**, từ tầng kiểm tra sự sống (Smoke Test), quy tắc nghiệp vụ (Unit Test), áp lực đồng thời (Concurrency Test), kiểm định chất lượng AI (Golden Dataset Test) đến hành trình người dùng hoàn chỉnh (E2E Test).

---

## 📂 Cấu Trúc Thư Mục Kiểm Thử

```
tests/
├── README.md                                 # Hướng dẫn chi tiết sử dụng bộ test
├── run_all_tests.py                          # Master Runner điều phối mọi bài test
├── run_all_tests.sh                          # Shell script thực thi tiện lợi
│
├── smoke/                                    # 1. SMOKE TEST (Sanity & Readiness)
│   └── test_smoke.py                         # Kiểm tra 6 ports, Core APIs, Frontend
│
├── unit/                                     # 2. UNIT TEST (Domain Business Rules)
│   └── test_business_rules.py                # Kiểm tra công thức, ngày giờ, mã ghế
│
├── concurrency/                              # 3. CONCURRENCY TEST (Race Condition)
│   └── test_concurrency_race_condition.py    # Mô phỏng 50 user tranh 1 ghế (Pessimistic Lock)
│
├── golden/                                   # 4. GOLDEN DATASET (RAG Quality Test)
│   ├── golden_dataset.json                   # Bộ dữ liệu câu hỏi - câu trả lời chuẩn (Ground Truth)
│   └── test_golden_rag.py                    # Chấm điểm độ phủ từ khóa, RAG Score & Latency
│
├── e2e/                                      # 5. END-TO-END USER JOURNEY (E2E)
│   └── test_e2e_booking_flow.py              # Luồng: Register -> Login -> Browse -> Ticket
│
└── load/                                     # 6. HIGH-CONCURRENCY LOAD TEST (Stress Test)
    └── test_load_flash_sale.py               # Mô phỏng 30-100 VUs tranh vé, đo TPS & P95/P99 latency
```

---

## 🚀 Hướng Dẫn Chạy Kiểm Thử

### Cách 1: Chạy toàn bộ tất cả các bài kiểm thử
```bash
python3 tests/run_all_tests.py
# hoặc:
./tests/run_all_tests.sh
```

### Cách 2: Chạy riêng từng nhóm bài kiểm thử

#### 1. Kiểm tra độ sẵn sàng toàn hệ thống (Smoke Test)
```bash
python3 tests/run_all_tests.py --suite smoke
# hoặc:
python3 tests/smoke/test_smoke.py
```

#### 2. Kiểm thử tranh chấp đồng thời & Race Condition (Concurrency Test)
```bash
python3 tests/run_all_tests.py --suite concurrency
# hoặc:
python3 tests/concurrency/test_concurrency_race_condition.py
```

#### 3. Đánh giá chất lượng RAG AI theo Golden Dataset (Golden Test)
```bash
python3 tests/run_all_tests.py --suite golden
# hoặc:
python3 tests/golden/test_golden_rag.py
```

#### 4. Kiểm thử hành trình người dùng hoàn chỉnh (E2E Test)
```bash
python3 tests/run_all_tests.py --suite e2e
# hoặc:
python3 tests/e2e/test_e2e_booking_flow.py
```

#### 5. Kiểm thử các quy tắc nghiệp vụ độc lập (Unit Test)
```bash
python3 tests/run_all_tests.py --suite unit
# hoặc:
python3 tests/unit/test_business_rules.py
```

#### 6. Kiểm thử chịu tải cao Flash Sale (Load & Stress Test)
```bash
python3 tests/run_all_tests.py --suite load
# hoặc chạy tùy biến 50 Virtual Users:
python3 tests/load/test_load_flash_sale.py --vus 50 --event-id 1
```

---

## 🎯 Chi Tiết Các Nhóm Kiểm Thử

| Nhóm Test | Mục Tiêu Kỹ Thuật | Tiêu Chuẩn Đạt (Success Criteria) |
| :--- | :--- | :--- |
| **Smoke Test** | Kiểm tra 6 cổng (Backend 8080, Frontend 5173, MySQL 3307, Redis 6380, Qdrant 6333, Kafka 9092) và các API chính. | 100% cổng mở, HTTP 200 cho APIs & Frontend. |
| **Unit Test** | Kiểm tra logic thời hạn giữ chỗ (10 phút), quy tắc ngày mở bán, tính toán chia đợt hàng đợi ảo (batch sizing), format mã ghế. | 100% assertions passed. |
| **Concurrency Test** | Dùng 50 threads đồng thời tranh mua cùng 1 ghế tại cùng 1 mili-giây. | **Duy nhất 1 người thành công**, 49 người thất bại, **0 bán trùng vé**. |
| **Golden Test** | Đánh giá câu trả lời của AI Concierge so với 8 câu hỏi Ground Truth đã được ban tổ chức sự kiện kiểm định. | Độ phủ từ khóa $\ge 50\%$, độ trễ $< 3000ms$, có trích dẫn nguồn RAG. |
| **E2E Test** | Đi qua 7 bước: Đăng ký $\rightarrow$ Đăng nhập lấy JWT $\rightarrow$ Xem Profile $\rightarrow$ Lọc sự kiện $\rightarrow$ Sơ đồ khu vực $\rightarrow$ Chatbot $\rightarrow$ Xem vé cá nhân. | Hoàn thành đủ 7/7 bước không lỗi HTTP. |
| **Load Test** | Mô phỏng đợt mở bán vé nóng (Flash Sale) với 30-100 VUs đồng thời đổ vào Queue & Bookings. | Đo lường TPS, P95/P99 latency, tỷ lệ lỗi 5xx $< 5\%$. |
