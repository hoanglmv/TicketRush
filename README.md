<div align="center">
  <h1>🎟️ TicketRush</h1>
  <p><strong>Nền Tảng Đặt Vé Sự Kiện Trực Tuyến Tốc Độ Cao</strong></p>
  
  [![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen.svg?logo=springboot)](https://spring.io/projects/spring-boot)
  [![React](https://img.shields.io/badge/React-18-blue.svg?logo=react)](https://reactjs.org/)
  [![Redis](https://img.shields.io/badge/Redis-7-red.svg?logo=redis)](https://redis.io/)
  [![Docker](https://img.shields.io/badge/Docker-Ready-2496ED.svg?logo=docker)](https://www.docker.com/)
  [![TypeScript](https://img.shields.io/badge/TypeScript-5.0-3178C6.svg?logo=typescript)](https://www.typescriptlang.org/)
</div>

---

## 🌟 Giới Thiệu (Overview)

**TicketRush** là một hệ thống đặt vé sự kiện (hòa nhạc, thể thao, workshop) được thiết kế đặc biệt để chịu tải cao trong các đợt mở bán vé (Flash Sale). Hệ thống giải quyết bài toán chống "Overbooking" (đặt lố vé) và duy trì sự công bằng cho người mua thông qua cơ chế **Virtual Queue (Hàng đợi ảo)** kết hợp cùng bản đồ chọn ghế **Real-time (Thời gian thực)**.

Đồ án được phát triển theo mô hình Client-Server (RESTful API), ứng dụng các công nghệ hiện đại nhằm mô phỏng sát nhất hệ thống phân phối vé thực tế.

---

## ✨ Tính Năng Nổi Bật (Core Features)

- **🚦 Hàng Đợi Ảo (Virtual Queue):** Tích hợp Redis ZSET phân bổ luồng người dùng truy cập lúc cao điểm, đảm bảo nguyên tắc FIFO (First In - First Out) chống nghẽn Server.
- **🗺️ Bản Đồ Ghế Real-time:** Ứng dụng WebSocket (STOMP) để đồng bộ trạng thái ghế ngồi ngay lập tức đến toàn bộ người dùng đang online.
- **🔒 Giữ Chỗ & Khóa Ghế (Seat Locking):** Cơ chế "Pessimistic Locking" trên Database và TTL trên Redis ngăn chặn triệt để tình trạng 2 người mua cùng 1 ghế.
- **🌍 Đa Ngôn Ngữ (i18n):** Hỗ trợ chuyển đổi ngôn ngữ (Tiếng Anh / Tiếng Việt) mượt mà trên toàn hệ thống không cần tải lại trang.
- **🎫 Vé Điện Tử Thông Minh (Smart Ticket):** Tự động sinh mã QR Base64 mã hóa thông tin bảo mật, hỗ trợ check-in dễ dàng tại cổng sự kiện.
- **📊 Admin Dashboard:** Thống kê trực quan số liệu kinh doanh, doanh thu, nhân khẩu học người dùng thông qua biểu đồ Recharts.

---

## 🛠️ Công Nghệ Sử Dụng (Tech Stack)

| Hạng mục | Công nghệ |
| :--- | :--- |
| **Backend API** | Java 21, Spring Boot 3, Spring Data JPA, Spring Security (JWT) |
| **Frontend UI** | React 18, Vite, TypeScript, Tailwind CSS, Recharts |
| **Cơ Sở Dữ Liệu** | MySQL 8 |
| **In-Memory Cache**| Redis 7 (Quản lý Queue & Token) |
| **Realtime Comm** | WebSockets (SockJS + STOMP) |
| **Cloud Storage** | Cloudinary (Lưu trữ hình ảnh/banner) |
| **DevOps** | Docker, Docker Compose |

---

## 🚀 Hướng Dẫn Cài Đặt (Quick Start)

Dự án được "đóng gói" hoàn toàn bằng Docker. Bạn **KHÔNG CẦN** cài đặt Java, Node.js, MySQL hay Redis trên máy tính. Yêu cầu duy nhất là đã cài đặt [Docker Desktop](https://www.docker.com/products/docker-desktop/).

### 1. Khởi chạy hệ thống

Mở Terminal (hoặc PowerShell) tại thư mục gốc của dự án và chạy lệnh:

```bash
docker compose up -d --build
```

### 2. Truy cập ứng dụng

Sau khi các container chạy thành công (mất khoảng 1-2 phút cho lần build đầu tiên), truy cập qua trình duyệt:

- **Trang chủ (Frontend):** `http://localhost:5173`
- **Backend API (Swagger/Base):** `http://localhost:8080/api`

### 3. Tài Khoản Mặc Định

| Phân quyền | Tên đăng nhập | Mật khẩu | Ghi chú |
| :--- | :--- | :--- | :--- |
| **Quản trị viên** | `admin` | `admin123` | Quản lý sự kiện, xem thống kê |
| **Khách hàng** | *(Tự đăng ký)* | *(Tự tạo)* | Truy cập trang Đăng ký để trải nghiệm luồng mua vé |

---

## 📁 Cấu Trúc Dự Án (Project Structure)

Dự án tuân thủ nghiêm ngặt mô hình 3 lớp (Controller - Service - Repository):

```text
TicketRush/
├── docker-compose.yml          # Cấu hình tự động hóa triển khai
├── build.gradle                # Quản lý thư viện Backend
├── backend/main/java/com/ticketrush/
│   ├── controller/             # Tầng giao tiếp REST API (Nhận Request, trả Response)
│   ├── service/                # Tầng nghiệp vụ cốt lõi (Xử lý Queue, Đặt vé, Cấp Token)
│   ├── repository/             # Tầng tương tác CSDL (Thực thi JPA, Lock DB)
│   ├── dto/                    # Data Transfer Objects (Chuẩn hóa gói tin)
│   ├── security/               # Cấu hình phân quyền & JWT
│   └── config/                 # Cấu hình hệ thống (WebSocket, Redis)
└── frontend/                   # Mã nguồn ReactJS
    ├── src/pages/              # Các trang giao diện (Home, Checkout, Admin)
    ├── src/api/                # Cấu hình gọi API (Axios)
    └── src/i18n/               # Cấu hình đa ngôn ngữ
```

---

## 🔧 Các Lệnh Docker Hữu Ích

```bash
# Xem trạng thái các services đang chạy
docker compose ps

# Xem log hệ thống (Hữu ích khi debug lỗi API)
docker compose logs -f backend

# Tắt toàn bộ hệ thống
docker compose down

# Xóa SẠCH dữ liệu database và reset lại từ đầu
docker compose down -v
docker compose up -d --build
```

## 🚨 Xử Lý Lỗi Thường Gặp (Troubleshooting)

1. **Lỗi `Port 3307/6380 already in use`:**
   Dự án sử dụng port `3307` cho MySQL và `6380` cho Redis để tránh xung đột với phần mềm local. Nếu vẫn bị trùng, hãy mở CMD quyền Admin:
   `netstat -ano | findstr :3307` ➜ `taskkill /PID <Mã PID> /F`

2. **Frontend báo "Network Error" hoặc không gọi được API:**
   Kiểm tra xem Backend đã khởi động xong chưa. Spring Boot cần vài chục giây để khởi tạo toàn bộ Bean và kết nối Database. Chạy `docker compose logs -f backend` để xem tiến trình.

---
*Dự án được xây dựng với mục đích học thuật và nghiên cứu ứng dụng công nghệ Real-time & Caching trong hệ thống thương mại điện tử.*
