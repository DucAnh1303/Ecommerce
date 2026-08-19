package com.service.product.exception;

import lombok.Getter;

/**
 * Custom runtime exception sử dụng xuyên suốt ứng dụng.
 * Ném ra với một {@link GenerateErrorCode} để GlobalExceptionHandler tự động
 * resolve HTTP status và message đa ngôn ngữ.
 */
@Getter
public class GenerateException extends RuntimeException {

    private final GenerateErrorCode errorCode;

    public GenerateException(GenerateErrorCode errorCode) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
    }

    public GenerateException(GenerateErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
