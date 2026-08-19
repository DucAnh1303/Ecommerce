package com.service.order.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum OrderErrorCode {
    // ─── Order ────────────────────────────────────────────────────────────────
    ORDER_NOT_FOUND         (HttpStatus.NOT_FOUND,            "Order không tồn tại"),
    ORDER_ALREADY_COMPLETED (HttpStatus.BAD_REQUEST,          "Đơn hàng đã hoàn tất, không thể hủy"),
    ORDER_INVALID_STATUS    (HttpStatus.BAD_REQUEST,          "Trạng thái đơn hàng không hợp lệ"),

    // ─── Rate Limit ────────────────────────────────────────────────────────────
    RATE_LIMIT_EXCEEDED     (HttpStatus.TOO_MANY_REQUESTS,    "Quá nhiều yêu cầu, vui lòng thử lại sau"),

    // ─── Validation ────────────────────────────────────────────────────────────
    INVALID_REQUEST         (HttpStatus.BAD_REQUEST,          "Dữ liệu đầu vào không hợp lệ"),

    // ─── General ───────────────────────────────────────────────────────────────
    INTERNAL_SERVER_ERROR   (HttpStatus.INTERNAL_SERVER_ERROR,"Lỗi hệ thống nội bộ"),
    UNAUTHORIZED            (HttpStatus.UNAUTHORIZED,         "Chưa xác thực"),
    ACCESS_DENIED           (HttpStatus.FORBIDDEN,            "Không có quyền truy cập");

    private final HttpStatus httpStatus;
    private final String message;

    OrderErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }
}
