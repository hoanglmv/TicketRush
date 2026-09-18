# Ticketbox Data Crawler Agent & Auto-Ingestion Tool

> **Lưu ý**: Đây là công cụ Agent phụ trợ nội bộ (nhỏ gọn, độc lập), được sử dụng để tự động thu thập và bổ sung dữ liệu sự kiện phong phú từ nền tảng **Ticketbox** (ticketbox.vn) vào hệ thống **TicketRush**.

---

## 1. Tổng Quan Kiến Trúc

```
+-------------------+        +---------------------------+        +--------------------------------+
|                   |        |                           |        |   TicketRush Live System       |
|  Ticketbox.vn     | =====> |  TicketboxCrawlerAgent    | =====> |  - REST API (/api/admin/events)|
|  (Web / Searches) |        |  - Extract & Normalize    |        |  - MySQL Database (Tables)     |
|                   |        |  - Price / Seat Generator |        |  - RAG Knowledge Base (AI Bot) |
+-------------------+        +---------------------------+        +--------------------------------+
                                          |
                                          +---------------------> [output/crawled_ticketbox_events.sql]
                                          +---------------------> [output/crawled_ticketbox_events.json]
```

## 2. Các Tính Năng Cốt Lõi

1. **Thu thập dữ liệu Live & Curated**:
   - Gửi yêu cầu phân tích trang chủ và tìm kiếm từ `ticketbox.vn`.
   - Trang bị sẵn bộ dữ liệu ca nhạc & sự kiện thực tế hàng đầu Việt Nam: *Anh Trai Say Hi, Anh Trai Vượt Ngàn Chông Gai, Hà Anh Tuấn, Vũ., Đen Vâu, IDECAF, Thiên Đăng, GENfest, Giải bóng rổ VBA...*
2. **Chuẩn hóa sơ đồ khán đài (Zones & Seats)**:
   - Tự động phân chia hạng vé thực tế: SVIP, VIP, Fanzone GA (đứng), Khán đài A, Khán đài B...
   - Tự động gán màu sắc trực quan (Hex Colors) và giá vé chuẩn VNĐ (từ 220.000đ đến 5.000.000đ).
   - Tự động tạo ma trận ghế ngồi (Seats: A01, A02, B01...) cho từng khu vực.
3. **Đa kênh nạp dữ liệu (Multi-Target Ingestion)**:
   - **Qua REST API**: Đăng nhập bằng tài khoản Quản trị (`admin` / `admin123`), gọi API `/api/admin/events` và `/api/admin/events/{id}/zones`. Tự động kiểm tra trùng lặp sự kiện.
   - **Qua file SQL**: Tự động sinh file `crawled_ticketbox_events.sql` để import trực tiếp vào MySQL nếu cần.
   - **Tích hợp RAG AI Chatbot**: Tự động sinh tài liệu ngữ nghĩa `ticketbox_crawled_events.json` trong `backend/resources/knowledge/` để AI Chatbot có thể trả lời ngay về giá vé, địa điểm, quy định của các sự kiện mới.

---

## 3. Hướng Dẫn Sử Dụng

### Cách 1: Chạy nhanh bằng script (Khuyên dùng)

```bash
./tools/ticketbox_crawler/run_crawler.sh
```

### Cách 2: Chạy trực tiếp bằng Python với các tùy chọn linh hoạt

```bash
# Nạp trực tiếp vào hệ thống đang chạy (API + SQL)
python3 tools/ticketbox_crawler/crawler.py --target both

# Chỉ tạo file SQL và JSON (không gọi API)
python3 tools/ticketbox_crawler/crawler.py --target sql

# Tùy biến địa chỉ backend
python3 tools/ticketbox_crawler/crawler.py --backend-url http://localhost:8080 --target api
```

### Các tham số dòng lệnh:
- `--source`: Nguồn dữ liệu (`all`, `live`, `curated` - mặc định `all`).
- `--target`: Hình thức thực thi:
  - `enrich`: Làm giàu thông tin (mô tả đa tầng, gallery 3-5 ảnh) cho toàn bộ sự kiện hiện có (mặc định).
  - `both`: Tạo sự kiện mới qua REST API và xuất file SQL.
  - `api`: Chỉ nạp qua REST API.
  - `sql`: Chỉ xuất file SQL `crawled_ticketbox_events.sql`.
  - `json`: Chỉ xuất file JSON `crawled_ticketbox_events.json`.
- `--backend-url`: Địa chỉ backend TicketRush (mặc định `http://localhost:8080`).
- `--admin-user`: Tài khoản quản trị (mặc định `admin`).
- `--admin-pass`: Mật khẩu quản trị (mặc định `admin123`).
- `--output-dir`: Thư mục lưu file kết quả (mặc định `tools/ticketbox_crawler/output`).

---

## 4. Kiểm Tra Dữ Liệu Sau Khi Chạy

1. Mở trang web: [http://localhost:5173](http://localhost:5173) -> Xem danh sách sự kiện mới trên trang chủ.
2. Mở Chatbot hỏi đáp: *"Giá vé Anh Trai Say Hi bao nhiêu?"* hoặc *"Show của Vũ tổ chức ở đâu?"*
