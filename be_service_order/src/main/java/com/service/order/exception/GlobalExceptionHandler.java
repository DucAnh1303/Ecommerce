package com.service.order.exception;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OrderException.class)
    public ResponseEntity<ErrorResponse> handleOrderException(OrderException ex, HttpServletRequest req) {
        OrderErrorCode code = ex.getErrorCode();
        log.warn("[{}] {} – {}", code.name(), req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(buildError(code.getHttpStatus(), code.name(), ex.getMessage()));
    }

    /**
     * Xử lý khi Rate Limiter từ chối request.
     * Trả về 429 Too Many Requests với message tiếng Việt.
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RequestNotPermitted ex, HttpServletRequest req) {
        log.warn("[RATE_LIMIT] {} bị từ chối – {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(buildError(HttpStatus.TOO_MANY_REQUESTS,
                        OrderErrorCode.RATE_LIMIT_EXCEEDED.name(),
                        OrderErrorCode.RATE_LIMIT_EXCEEDED.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (first, second) -> first
                ));
        log.warn("[VALIDATION] {} – {}", req.getRequestURI(), fields);
        return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                        .timestamp(Instant.now())
                        .status(HttpStatus.BAD_REQUEST.value())
                        .error(OrderErrorCode.INVALID_REQUEST.name())
                        .message(OrderErrorCode.INVALID_REQUEST.getMessage())
                        .fields(fields)
                        .build()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest req) {
        log.error("[INTERNAL] {} – {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                        OrderErrorCode.INTERNAL_SERVER_ERROR.name(),
                        OrderErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    private ErrorResponse buildError(HttpStatus status, String error, String message) {
        return ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(status.value())
                .error(error)
                .message(message)
                .build();
    }
}
