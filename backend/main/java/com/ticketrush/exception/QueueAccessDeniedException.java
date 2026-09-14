package com.ticketrush.exception;

/** 
 * Lớp định nghĩa Exception (lỗi ngoại lệ) tùy chỉnh.
 * Dùng để ném và xử lý các lỗi nghiệp vụ riêng của ứng dụng.
 */
public class QueueAccessDeniedException extends RuntimeException {
    public QueueAccessDeniedException() {
        super("You do not have access to book seats. Please wait in the queue.");
    }

    public QueueAccessDeniedException(String message) {
        super(message);
    }
}
