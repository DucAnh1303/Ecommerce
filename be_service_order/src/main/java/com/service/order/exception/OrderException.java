package com.service.order.exception;

import lombok.Getter;

@Getter
public class OrderException extends RuntimeException {
    private final OrderErrorCode errorCode;

    public OrderException(OrderErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public OrderException(OrderErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
