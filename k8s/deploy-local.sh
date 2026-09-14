#!/usr/bin/env bash
# ==============================================================================
# TicketRush — Script tự động triển khai hệ thống lên cụm Kubernetes cục bộ
# (Hỗ trợ Minikube, Docker Desktop K8s, K3d, Kind)
# ==============================================================================

set -e

echo "🚀 Bắt đầu triển khai TicketRush Platform lên Kubernetes..."

# 1. Kiểm tra kết nối cụm kubectl
if ! kubectl cluster-info > /dev/null 2>&1; then
    echo "❌ Lỗi: Không thể kết nối tới cụm Kubernetes qua kubectl!"
    echo "💡 Gợi ý: Hãy bật Kubernetes trong Docker Desktop Settings hoặc khởi động 'minikube start'."
    exit 1
fi

echo "✅ Đã kết nối cụm Kubernetes thành công."

# 2. Tạo Namespace
echo "📦 [1/6] Thiết lập Namespace ticketrush..."
kubectl apply -f k8s/00-namespace.yaml

# 3. Nạp ConfigMap & Secret
echo "🔑 [2/6] Khởi tạo ConfigMap và Secrets..."
kubectl apply -f k8s/01-configmap.yaml
kubectl apply -f k8s/02-secrets.yaml

# 4. Triển khai các thành phần Middleware & Database
echo "💾 [3/6] Triển khai MySQL, Redis, Qdrant Vector DB, Apache Kafka..."
kubectl apply -f k8s/03-mysql.yaml
kubectl apply -f k8s/04-redis.yaml
kubectl apply -f k8s/05-qdrant.yaml
kubectl apply -f k8s/06-kafka.yaml

# Đợi các dịch vụ cốt lõi sẵn sàng
echo "⏳ Đang đợi MySQL và Redis sẵn sàng..."
kubectl rollout status statefulset/mysql -n ticketrush --timeout=180s || true
kubectl rollout status deployment/redis -n ticketrush --timeout=120s || true

# 5. Triển khai Backend, Frontend và Ingress
echo "⚡ [4/6] Triển khai Spring Boot Backend và React Frontend..."
kubectl apply -f k8s/07-backend.yaml
kubectl apply -f k8s/08-frontend.yaml
kubectl apply -f k8s/09-ingress.yaml

# 6. Kích hoạt Horizontal Pod Autoscaler (HPA)
echo "📈 [5/6] Thiết lập Auto-scaling HPA (2..10 Pods)..."
kubectl apply -f k8s/10-hpa.yaml

echo "🎉 [6/6] Triển khai hoàn tất! Danh sách tài nguyên trong namespace 'ticketrush':"
kubectl get all,pvc,ingress,hpa -n ticketrush

echo ""
echo "📌 Ghi chú truy cập:"
echo "   - Cần thêm dòng sau vào /etc/hosts (hoặc C:\Windows\System32\drivers\etc\hosts):"
echo "     127.0.0.1 ticketrush.local"
echo "   - Truy cập giao diện: http://ticketrush.local"
echo "   - API Backend: http://ticketrush.local/api"
