package com.service.product.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler cho toàn bộ ứng dụng.
 * <p>
 * Xử lý thứ tự ưu tiên:
 * <ol>
 *   <li>{@link ErrorResponse} – lỗi nghiệp vụ có mã lỗi cụ thể</li>
 *   <li>{@link MethodArgumentNotValidException} – lỗi @Valid trên request body</li>
 *   <li>{@link AuthenticationException} – Spring Security chưa xác thực</li>
 *   <li>{@link AccessDeniedException} – Spring Security không có quyền</li>
 *   <li>{@link Exception} – fallback mọi lỗi không mong đợi</li>
 * </ol>
 * </p>
 */
@Log4j2
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;


    @ExceptionHandler(GenerateException.class)
    public ResponseEntity<ErrorResponse> handleApiException(GenerateException ex,
                                                            Locale locale,
                                                            HttpServletRequest request) {
        GenerateErrorCode code = ex.getErrorCode();
        String message = resolveMessage(code.getMessageKey(), locale);

        log.warn("[{}] {} – {}", code.name(), request.getRequestURI(), message);

        return ResponseEntity
                .status(code.getHttpStatus())
                .body(buildError(code.getHttpStatus(), code.name(), message));
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          Locale locale,
                                                          HttpServletRequest request) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (first, second) -> first
                ));

        String message = resolveMessage(GenerateErrorCode.INVALID_REQUEST.getMessageKey(), locale);
        log.warn("[VALIDATION] {} – fields: {}", request.getRequestURI(), fields);

        ErrorResponse body = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(GenerateErrorCode.INVALID_REQUEST.name())
                .message(message)
                .fields(fields)
                .build();

        return ResponseEntity.badRequest().body(body);
    }


    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex,
                                                                       Locale locale,
                                                                       HttpServletRequest request) {
        String message = resolveMessage(GenerateErrorCode.UNAUTHORIZED.getMessageKey(), locale);
        log.warn("[UNAUTHORIZED] {} – {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(buildError(HttpStatus.UNAUTHORIZED, GenerateErrorCode.UNAUTHORIZED.name(), message));
    }

    // ─── Spring Security – không có quyền ────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex,
                                                                     Locale locale,
                                                                     HttpServletRequest request) {
        String message = resolveMessage(GenerateErrorCode.ACCESS_DENIED.getMessageKey(), locale);
        log.warn("[ACCESS_DENIED] {} – {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(buildError(HttpStatus.FORBIDDEN, GenerateErrorCode.ACCESS_DENIED.name(), message));
    }

    // ─── Fallback – mọi lỗi còn lại ─────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex,
                                                       Locale locale,
                                                       HttpServletRequest request) {
        String message = resolveMessage(GenerateErrorCode.INTERNAL_SERVER_ERROR.getMessageKey(), locale);
        log.error("[INTERNAL] {} – {}", request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(HttpStatus.INTERNAL_SERVER_ERROR,
                        GenerateErrorCode.INTERNAL_SERVER_ERROR.name(), message));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String resolveMessage(String key, Locale locale) {
        return messageSource.getMessage(key, null, key, locale);
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
