package web.com.auth.exception;

import lombok.Getter;

/**
 * Custom runtime exception sử dụng xuyên suốt ứng dụng.
 * Ném ra với một {@link ApiErrorCode} để GlobalExceptionHandler tự động
 * resolve HTTP status và message đa ngôn ngữ.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public ApiException(ApiErrorCode errorCode) {
        super(errorCode.getMessageKey());
        this.errorCode = errorCode;
    }

    public ApiException(ApiErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
