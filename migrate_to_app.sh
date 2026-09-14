#!/usr/bin/env bash
# ==============================================================================
# Script chuyển đổi cấu trúc TicketRush sang siêu phẳng (Ultra-Flat backend/app)
# Bỏ hoàn toàn: backend/main/java/com/ticketrush -> chỉ còn: backend/app
# ==============================================================================

set -e

echo "🚀 Bắt đầu chuyển đổi cấu trúc sang siêu phẳng backend/app/..."

# 1. Tạo các thư mục đích
mkdir -p backend/app backend/resources backend/test/app

# 2. Di chuyển toàn bộ code Java sang backend/app/
if [ -d "backend/main/java/com/ticketrush" ]; then
    echo "📦 Di chuyển mã nguồn Java sang backend/app/..."
    git mv backend/main/java/com/ticketrush/* backend/app/
fi

# 3. Di chuyển toàn bộ resources sang backend/resources/
if [ -d "backend/main/resources" ]; then
    echo "📄 Di chuyển resources sang backend/resources/..."
    git mv backend/main/resources/* backend/resources/
fi

# 4. Di chuyển tests
if [ -d "backend/test/java/com/ticketrush" ]; then
    echo "🧪 Di chuyển tests sang backend/test/app/..."
    git mv backend/test/java/com/ticketrush/* backend/test/app/
    rm -rf backend/test/java backend/test/resources
fi

# 5. Dọn dẹp thư mục rác cũ backend/main
rm -rf backend/main

# 6. Cập nhật package declaration và import statements trong toàn bộ file Java
echo "📝 Đang cập nhật package và imports từ 'com.ticketrush' sang 'app'..."
find backend/app backend/test/app -name "*.java" -type f -exec sed -i 's/package com\.ticketrush\./package app\./g' {} +
find backend/app backend/test/app -name "*.java" -type f -exec sed -i 's/package com\.ticketrush;/package app;/g' {} +
find backend/app backend/test/app -name "*.java" -type f -exec sed -i 's/import com\.ticketrush\./import app\./g' {} +

echo "🎉 Chuyển đổi hoàn tất thành công! Cấu trúc siêu phẳng mới:"
ls -la backend/app
