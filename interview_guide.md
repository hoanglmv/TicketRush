# 🎯 CẨM NANG PHỎNG VẤN CHUYÊN SÂU DỰ ÁN TICKETRUSH
> **Dành cho Ứng viên: Vị trí Backend / Fullstack / Software Engineer**

---

## 📌 PHẦN 1: MỞ MÀN ẤN TƯỢNG (ELEVATOR PITCH - 60 GIÂY)

Khi người phỏng vấn nói: *"Em hãy giới thiệu tổng quan về dự án tâm đắc nhất của mình?"*, đây là câu trả lời chuẩn chỉnh:

> *"Dự án tâm đắc nhất của em là **TicketRush** – một nền tảng bán vé đại nhạc hội và thể thao trực tuyến được thiết kế theo hướng **chịu tải cao (High-Concurrency & Distributed Architecture)**, mô phỏng bài toán săn vé nóng của các nền tảng như Ticketbox hay Ticketmaster.*
> 
> *Điểm thách thức kỹ thuật lớn nhất em giải quyết trong dự án này là **bài toán Race Condition khi hàng chục ngàn người cùng tranh nhau một số lượng ghế giới hạn**. Em đã xây dựng một cơ chế phòng thủ 3 tầng:*
> 1. *Hàng đợi ảo **Virtual Queue bằng Redis** để điều tiết lưu lượng vào hệ thống theo từng đợt (Batch).*
> 2. *Cơ chế **Row-Level Pessimistic Locking (`PESSIMISTIC_WRITE`)** trên MySQL kết hợp **Hold-Timer 10 phút trên Redis** và **WebSocket STOMP** để đồng bộ trạng thái ghế theo thời gian thực, triệt tiêu 100% rủi ro bán trùng ghế.*
> 3. *Áp dụng kiến trúc **hướng sự kiện (Event-Driven) với Apache Kafka** để xử lý bất đồng bộ các tác vụ thanh toán, xuất vé QR và gửi thông báo.*
> 
> *Ngoài ra, em còn tích hợp một **AI Concierge ứng dụng kiến trúc RAG** trên **Vector Database Qdrant** để giải đáp các thông tin và quy định sự kiện cho khán giả với bảng giám sát hiệu năng thời gian thực cho Admin."*

---

## 📌 PHẦN 2: BỘ 10 CÂU HỎI PHỎNG VẤN HÓC BÚA & CÂU TRẢ LỜI MẪU

---

### ❓ Câu hỏi 1: "Em hãy giải thích chi tiết cách hệ thống xử lý tranh chấp khi 10.000 người cùng bấm mua 1 ghế tại cùng 1 giây?"

#### 💡 Câu trả lời của bạn:
*"Để xử lý triệt để bài toán này mà không làm nghẽn Database, em áp dụng cơ chế **Phòng vệ theo tầng (Layered Defense)**:*

1. **Tầng 1 - Điều tiết lưu lượng (Virtual Queue):**
   * Nếu sự kiện mở bán vé nóng, người dùng không được vào thẳng trang chọn ghế mà được xếp vào **Hàng đợi ảo trên Redis** (mô hình FIFO/Token Bucket).
   * Hệ thống chỉ cấp `Queue Token` (JWT có thời hạn) cho từng đợt 50-100 người vào hệ thống. Request nào không có token sẽ bị `QueueAccessDeniedException` chặn ngay ở tầng Filter, bảo vệ Database khỏi việc bị hàng chục ngàn request dội vào cùng lúc.

2. **Tầng 2 - Khóa hàng dữ liệu tại Database (Row-Level Pessimistic Lock):**
   * Khi người dùng chọn ghế, em sử dụng `@Lock(LockModeType.PESSIMISTIC_WRITE)` trong `SeatRepository`:
     ```sql
     SELECT s FROM Seat s WHERE s.id = :id FOR UPDATE
     ```
   * Khi Transaction của User A đang giữ lock, bất kỳ User B nào gửi request tranh ghế đó sẽ phải chờ. User A kiểm tra thấy `status == AVAILABLE`, đổi sang `HELD`, gán `lockedAt = NOW()` và commit transaction.
   * Đến lượt User B đọc ra thấy ghế đã là `HELD`, hệ thống lập tức quăng `SeatAlreadyTakenException` và báo ngay cho User B chọn ghế khác.

3. **Tầng 3 - Đồng bộ thời gian thực qua WebSocket:**
   * Ngay khi User A giữ ghế thành công, một tin nhắn STOMP được broadcast tới kênh `/topic/event/{eventId}/seats`.
   * Màn hình của tất cả người dùng khác đang xem sơ đồ ghế sẽ tự động chuyển chiếc ghế đó sang màu Vàng (Đang giữ) mà không cần họ phải F5 tải lại trang."*

---

### ❓ Câu hỏi 2: "Tại sao em chọn Pessimistic Lock (Khóa bi quan) mà không dùng Optimistic Lock (Khóa lạc quan) với trường `@Version`?"

#### 💡 Câu trả lời của bạn:
*"Đây là một quyết định đánh đổi kỹ thuật (Trade-off) dựa trên **mức độ tranh chấp (Contention Rate)**:*

* **Optimistic Lock** (dùng trường `@Version`): Rất tốt khi tỷ lệ đọc nhiều, tỷ lệ ghi đè thấp (Low contention). Nhưng trong kịch bản mở bán vé concert HOT, 1.000 người cùng tranh 1 ghế VIP (High contention). Nếu dùng Optimistic Lock, 999 người sẽ bị dính `OptimisticLockException` và phải retry liên tục. Việc retry 999 lần sẽ gây lãng phí tài nguyên CPU và I/O khủng khiếp mà vẫn chỉ có 1 người thành công.
* **Pessimistic Lock** (`SELECT ... FOR UPDATE`): Phù hợp hoàn hảo cho kịch bản High Contention này. Nó khóa cứng bản ghi trong thời gian cực ngắn (vài mili-giây để cập nhật cờ `HELD`). Người đến sau đọc được dữ liệu mới nhất ngay lập tức và fail-fast (thất bại sớm) mà không cần retry vô ích."*

---

### ❓ Câu hỏi 3: "Nếu người dùng giữ ghế 10 phút nhưng tắt máy, mất mạng hoặc cố tình không thanh toán thì xử lý thế nào?"

#### 💡 Câu trả lời của bạn:
*"Hệ thống của em có cơ chế **Tự phục hồi (Self-Healing Background Scheduler)**:*

* Khi ghế chuyển sang trạng thái `HELD`, cột `locked_at` được lưu thời điểm khóa.
* Em viết một `SeatReleaseScheduler` chạy ngầm định kỳ bằng `@Scheduled(fixedRate = 60000)` (mỗi 1 phút):
  ```java
  LocalDateTime cutoff = LocalDateTime.now().minusMinutes(seatLockTimeoutMinutes);
  List<Seat> expiredSeats = seatRepository.findByStatusAndLockedAtBefore(SeatStatus.HELD, cutoff);
  ```
* Bất kỳ ghế nào ở trạng thái `HELD` quá 10 phút mà chưa hoàn tất thanh toán sẽ tự động được trả về trạng thái `AVAILABLE`, đồng thời bắn event qua WebSocket để mở lại màu Xanh cho những người khác đang chờ mua."*

---

### ❓ Câu hỏi 4: "Tại sao lại dùng Apache Kafka trong dự án này? Có thể thay bằng gọi trực tiếp REST API hoặc RabbitMQ được không?"

#### 💡 Câu trả lời của bạn:
*"Em chọn Apache Kafka vì 3 lý do kiến trúc quan trọng:*

1. **Decoupling & Asynchronous Processing (Bất đồng bộ hóa):**
   * Sau khi người dùng thanh toán, nếu gọi tuần tự: Trừ tiền $\rightarrow$ Sinh vé QR $\rightarrow$ Gửi email $\rightarrow$ Cập nhật thống kê, request sẽ mất từ 3-5 giây. Nếu dịch vụ gửi email bị nghẽn mạng thì cả giao dịch thanh toán bị treo theo.
   * Với Kafka, ngay khi thanh toán thành công, em chỉ cần bắn một `BookingEvent` vào Kafka Topic `ticket-booking-events` trong 2ms rồi phản hồi thành công ngay cho khách hàng.
2. **Khả năng chịu lỗi và lưu vết (Log Persistence & Replayability):**
   * Khác với REST API (nếu server nhận bị sập thì mất dữ liệu) hay Redis PubSub (không lưu trữ tin nhắn), Kafka lưu toàn bộ sự kiện trên đĩa cứng (Commit Log).
   * Giả sử dịch vụ gửi email hoặc hệ thống phân tích bị crash trong 30 phút, khi khởi động lại, các Consumer của Kafka vẫn đọc tiếp từ offset cũ để gửi lại email đầy đủ mà không bị thất thoát bất kỳ chiếc vé nào.
3. **Mở rộng quy mô người tiêu thụ (Consumer Groups Scaling):**
   * Nếu lượng đặt vé tăng đột biến, em chỉ việc tăng số lượng partition và scale thêm instance cho `NotificationConsumer` hoặc `PaymentEventConsumer` mà không cần sửa code backend chính."*

---

### ❓ Câu hỏi 5: "Em đã tối ưu truy vấn Database (Indexing) trong dự án như thế nào?"

#### 💡 Câu trả lời của bạn:
*"Em đã phân tích các câu truy vấn thực tế (`Query Access Patterns`) và nhận thấy nếu không có Index, các bảng lớn sẽ bị **Full Table Scan**, làm sập MySQL khi tải cao. Em đã tối ưu như sau:

1. **Bảng `events`:**
   * **Composite Index `(status, event_date)`:** Tối ưu câu truy vấn trang chủ lọc các sự kiện đang mở bán (`ON_SALE`/`PUBLISHED`) sắp diễn ra.
   * **Index `category`:** Tối ưu thanh lọc danh mục (Âm nhạc, Thể thao, Kịch...).
   * **Index `city`:** Tối ưu tìm kiếm theo địa phương (Hà Nội, TP.HCM...).
   * **Index `is_hot`:** Tối ưu slider banner sự kiện nổi bật.
2. **Bảng `seats`:**
   * **Composite Index `(zone_id, status)`:** Đây là index quan trọng nhất. Mỗi khi mở màn hình chọn ghế, hệ thống phải đếm số lượng ghế trống theo từng khu vực (`countByEventIdAndStatus`). Composite Index giúp MySQL chỉ cần quét Index Tree trong RAM mà không cần đọc từng dòng dữ liệu từ đĩa."*

---

### ❓ Câu hỏi 6: "Kiến trúc RAG (Retrieval-Augmented Generation) cho Chatbot của em hoạt động thế nào? Tại sao lại cần Qdrant Vector DB?"

#### 💡 Câu trả lời của bạn:
*"Các mô hình LLM thông thường (như ChatGPT hay Gemini) không hề biết về sơ đồ gửi xe, quy định mang đồ cấm, hay ca sĩ khách mời bí mật của các concert nội địa như 'Anh Trai Say Hi' hay 'Kịch IDECAF' tại Việt Nam.

Em xây dựng luồng **RAG Pipeline** như sau:
1. **Dữ liệu nguồn:** Toàn bộ thông tin chi tiết sự kiện (Timeline, Lineup, Bãi xe, Quy định vé) được thu thập và làm sạch thành các đoạn tài liệu Markdown/JSON.
2. **Embedding:** Đoạn tài liệu được chuyển thành các vector số học nhiều chiều (Dense Vectors) bằng Embedding Model và lưu trữ tại **Qdrant Vector Database**.
3. **Truy vấn ngữ nghĩa (Semantic Search):**
   * Khi người dùng hỏi: *'Tôi có được mang máy ảnh tele vào concert không?'*
   * Câu hỏi được vector hóa $\rightarrow$ Qdrant tính toán **Khoảng cách Cosine (Cosine Similarity)** để tìm ra đoạn quy định an ninh khớp nhất (ngưỡng tương đồng $\ge 0.35$).
4. **Prompt Augmentation:** Hệ thống ghép đoạn quy định tìm được vào Prompt và gửi tới OpenRouter (Gemini 2.0 Flash) để sinh câu trả lời chính xác 100%, không bị ảo giác (Hallucination).
5. **Giám sát & Đánh giá (Ragas Evaluation & Audit Logs):**
   * Admin Dashboard có bảng **Live Monitor** đo trực tiếp: Độ trễ phản hồi (ms), RAG Hit Rate (%), và ghi nhận đánh giá Thumbs Up/Down từ người dùng."*

---

### ❓ Câu hỏi 7: "Hệ thống bảo mật và phân quyền của em hoạt động ra sao?"

#### 💡 Câu trả lời của bạn:
*"Em xây dựng mô hình bảo mật **Stateless Security** dựa trên **Spring Security 6 + JWT (JSON Web Token)**:
* **Stateless:** Server không lưu Session trong RAM, cho phép scale ngang (Horizontal Scaling) nhiều backend instance mà người dùng không bị văng đăng nhập.
* **Filter Chain:** `JwtAuthenticationFilter` chặn mọi request, giải mã token, kiểm tra chữ ký và nạp thông tin quyền hạn (`GrantedAuthority`) vào `SecurityContextHolder`.
* **Phân quyền dựa trên Role (RBAC):**
  * Public: `/api/auth/**`, `/api/events/**`, `/ws/**`
  * Admin: `/api/admin/**` (yêu cầu `ROLE_ADMIN`)
  * User Authenticated: Đặt vé, xem danh sách vé của tôi (`/api/bookings/**`, `/api/tickets/**`).
* **Mật khẩu:** Được băm một chiều bằng thuật toán `BCryptPasswordEncoder` với salt ngẫu nhiên chống tấn công Rainbow Table."*

---

### ❓ Câu hỏi 8: "Nếu dịch vụ bên thứ ba (như Mail server hoặc Cloudinary) bị lỗi thì hệ thống xử lý thế nào?"

#### 💡 Câu trả lời của bạn:
*"Em áp dụng nguyên lý **Graceful Degradation (Suy thoái mềm)** và **Fail-Safe**:
* **Gửi email:** Không chạy đồng bộ trong thread chính của người dùng. Email được xử lý bất đồng bộ qua Kafka. Nếu Gmail SMTP lỗi, Kafka giữ tin nhắn lại và có thể retry theo cơ chế Exponential Backoff mà không làm gián đoạn việc nhận vé của khách hàng. Khách hàng vẫn thấy vé hiển thị ngay lập tức trong mục 'Vé của tôi' trên web.
* **Chatbot:** Nếu OpenRouter LLM bị mất kết nối hoặc hết quota, ChatbotService tự động chuyển sang **Rule-based Fallback Engine** để trả lời các câu hỏi thường gặp cơ bản thay vì báo lỗi sập chat."*

---

### ❓ Câu hỏi 9: "Em đóng gói và quản lý hạ tầng dự án này như thế nào?"

#### 💡 Câu trả lời của bạn:
*"Toàn bộ dự án được **Container hóa 100% bằng Docker và Docker Compose**:
* Gồm 6 container độc lập kết nối chung một mạng ảo `ticketrush-net`: `frontend` (Vite dev/nginx), `backend` (Java 21 JAR), `mysql:8.0`, `redis:7-alpine`, `kafka (KRaft)` và `qdrant`.
* Sử dụng Docker Healthcheck (`mysqladmin ping`, `redis-cli ping`) để đảm bảo các service cơ sở dữ liệu khỏe mạnh trước khi Backend khởi động (`depends_on: service_healthy`).
* Ngoài ra, em đã viết sẵn bộ cấu hình **Kubernetes manifests (`k8s/`)** bao gồm: Deployments, Services, ConfigMaps, Secrets, PersistentVolumeClaims để sẵn sàng đưa lên các cụm K8s (EKS/GKE) khi mở rộng quy mô lớn."*

---

### ❓ Câu hỏi 10: "Nếu được tiếp tục phát triển dự án này lên cấp độ Production thực tế của doanh nghiệp, em sẽ cải tiến điều gì?"

#### 💡 Câu trả lời của bạn:
*(Câu hỏi này để chứng minh bạn có tầm nhìn của một Senior Engineer)*
1. *"**Triển khai Caching đa tầng:** Áp dụng `@Cacheable` trên Redis cho các API đọc danh sách sự kiện và chi tiết sự kiện để giảm 90% truy vấn vào MySQL.
2. **Tích hợp Cổng thanh toán thực tế:** Tích hợp Webhook của VNPay, MoMo hoặc ZaloPay với mô hình **Idempotency Key** để đảm bảo một giao dịch không bao giờ bị trừ tiền hai lần dù mạng chập chờn.
3. **Database Read/Write Splitting:** Áp dụng mô hình MySQL Master-Slave (Master chuyên ghi đặt vé, Replica chuyên đọc tìm kiếm sự kiện).
4. **Distributed Tracing & APM:** Tích hợp Prometheus & Grafana hoặc OpenTelemetry để giám sát trực quan các nút nghẽn (bottlenecks) của từng request khi chịu tải hàng triệu người dùng."*

---

## 📌 PHẦN 3: BẢNG TRA CỨU CÔNG NGHỆ NHANH

| Công nghệ | Vai trò trong TicketRush | Từ khóa giải thích cho NTD |
| :--- | :--- | :--- |
| **Java 21 / Spring Boot 3** | Backend Core API | Virtual Threads, High Performance, Enterprise-grade. |
| **MySQL 8.0** | Relational DB | ACID Transactions, Pessimistic Locking (`FOR UPDATE`), Composite Indexes. |
| **Redis 7** | Cache & Memory Store | Virtual Queue FIFO, Distributed Lock, Seat Hold TTL (10 mins). |
| **Apache Kafka (KRaft)** | Event Broker | Event-Driven Architecture, Asynchronous, Decoupled, Fault-tolerant. |
| **Qdrant Vector DB** | Vector Search Engine | Semantic Search, Cosine Distance, RAG Context Retrieval. |
| **WebSocket / STOMP** | Realtime Communication | Push-based updates, Seat state synchronization, Zero-polling. |
| **React 18 / TypeScript / Vite** | Frontend Framework | Single Page Application, Type Safety, Fast HMR, Code-splitting. |
| **Docker / Kubernetes** | Infrastructure | Containerization, Microservices orchestration, Cloud-native. |
