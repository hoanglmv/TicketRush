package com.ticketrush.exception;

/** 
 * Lớp định nghĩa Exception (lỗi ngoại lệ) tùy chỉnh.
 * Dùng để ném và xử lý các lỗi nghiệp vụ riêng của ứng dụng.
 */
public class SeatAlreadyTakenException extends RuntimeException {
    public SeatAlreadyTakenException() {
        super("This seat is already taken by another user");
    }

    public SeatAlreadyTakenException(String message) {
        super(message);
    }
}
