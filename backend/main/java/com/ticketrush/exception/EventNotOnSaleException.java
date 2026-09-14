package com.ticketrush.exception;

/** 
 * Lớp định nghĩa Exception (lỗi ngoại lệ) tùy chỉnh.
 * Dùng để ném và xử lý các lỗi nghiệp vụ riêng của ứng dụng.
 */
public class EventNotOnSaleException extends RuntimeException {
    public EventNotOnSaleException() {
        super("This event is not currently on sale");
    }

    public EventNotOnSaleException(String message) {
        super(message);
    }
}
