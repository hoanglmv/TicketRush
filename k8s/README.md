# Hướng Dẫn Vận Hành Kubernetes (K8s) & Apache Kafka Cho TicketRush

Bộ tài liệu và manifests này cung cấp cấu hình chuẩn cấp doanh nghiệp (Enterprise Production-Ready) cho toàn bộ hệ thống bán vé **TicketRush**.

---

## 1. Danh Mục Manifests (`k8s/`)

| Thứ Tự | Tên File | Chức Năng | Ghi Chú |
| :--- | :--- | :--- | :--- |
| 00 | `00-namespace.yaml` | Khởi tạo namespace riêng biệt `ticketrush` | Cách ly tài nguyên hoàn toàn |
| 01 | `01-configmap.yaml` | Cấu hình tham số môi trường tập trung | URLs kết nối MySQL, Redis, Qdrant, Kafka |
| 02 | `02-secrets.yaml` | Quản lý mật khẩu và API Key bí mật | JWT Secret, MySQL password, OpenRouter Key |
| 03 | `03-mysql.yaml` | StatefulSet + PVC 5Gi + Service cho MySQL 8.0 | Lưu trữ cơ sở dữ liệu bền vững |
| 04 | `04-redis.yaml` | Deployment + Service cho Redis 7 Alpine | Phục vụ Virtual Queue & Real-time Caching |
| 05 | `05-qdrant.yaml` | Deployment + PVC 5Gi + Service cho Qdrant | Lưu trữ 1536-dim vector embeddings của RAG |
| 06 | `06-kafka.yaml` | Deployment + PVC 10Gi + Service cho Apache Kafka | Chế độ KRaft hiện đại (không cần ZooKeeper) |
| 07 | `07-backend.yaml` | Deployment (2 Replicas) + Service Spring Boot | Có probes liveness/readiness, resource limits |
| 08 | `08-frontend.yaml` | Deployment (2 Replicas) + Service React Frontend | Cân bằng tải giao diện người dùng |
| 09 | `09-ingress.yaml` | Ingress Controller định tuyến tên miền | Hỗ trợ WebSocket connection upgrade (`/ws`) |
| 10 | `10-hpa.yaml` | Horizontal Pod Autoscaler (HPA) | Tự động scale backend từ 2 lên 10 Pods khi tải cao |

---

## 2. Các Lệnh Vận Hành Nhanh

### 2.1 Triển khai 1-Click
```bash
./k8s/deploy-local.sh
```

Hoặc áp dụng thủ công toàn bộ thư mục:
```bash
kubectl apply -f k8s/
```

### 2.2 Kiểm tra trạng thái toàn bộ hệ thống
```bash
# Xem tình trạng tất cả Pods, Services, HPA
kubectl get all -n ticketrush

# Xem chi tiết auto-scaling HPA
kubectl get hpa -n ticketrush

# Xem danh sách PersistentVolumeClaims
kubectl get pvc -n ticketrush
```

### 2.3 Xem Logs của từng thành phần
```bash
# Xem log Backend
kubectl logs -f -l app=ticketrush-backend -n ticketrush

# Xem log Kafka Event Streaming
kubectl logs -f deployment/kafka -n ticketrush

# Xem log Qdrant Vector DB
kubectl logs -f deployment/qdrant -n ticketrush
```

### 2.4 Mô phỏng kiểm thử tải (Stress Test) để kích hoạt Auto-scaling HPA
Khi concert mở bán vé, lưu lượng tăng vọt. Bạn có thể kiểm chứng tính năng tự co giãn của Pods bằng lệnh:
```bash
# Chạy Pod giả lập tải gửi 500 requests/s liên tục
kubectl run -i --tty load-generator --rm --image=busybox -n ticketrush --restart=Never -- /bin/sh -c "while true; do wget -q -O- http://ticketrush-backend:8080/api/events; done"

# Quan sát số lượng Pods tự động tăng từ 2 lên 3, 5, 8, 10:
kubectl get hpa ticketrush-backend-hpa -n ticketrush --watch
```

### 2.5 Dọn dẹp / Gỡ bỏ hệ thống
```bash
kubectl delete -f k8s/
```
