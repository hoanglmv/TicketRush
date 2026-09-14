package com.ticketrush.exception;

/** 
 * Lớp định nghĩa Exception (lỗi ngoại lệ) tùy chỉnh.
 * Dùng để ném và xử lý các lỗi nghiệp vụ riêng của ứng dụng.
 */
public class InvalidOperationException extends RuntimeException {
    public InvalidOperationException(String message) {
        super(message);
    }
}
