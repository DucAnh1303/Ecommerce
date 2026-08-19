package web.com.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Enum định nghĩa toàn bộ error code của ứng dụng.
 * Mỗi code map đến một message key trong file i18n messages_*.properties.
 */
@Getter
public enum ApiErrorCode {

    // ─── AUTH ─────────────────────────────────────────────────────────────────
    USER_NOT_FOUND          (HttpStatus.NOT_FOUND,            "error.auth.user_not_found"),
    INVALID_PASSWORD        (HttpStatus.UNAUTHORIZED,         "error.auth.invalid_password"),
    INVALID_TOKEN           (HttpStatus.UNAUTHORIZED,         "error.auth.invalid_token"),
    INVALID_REFRESH_TOKEN   (HttpStatus.UNAUTHORIZED,         "error.auth.invalid_refresh_token"),
    TOKEN_BLACKLISTED       (HttpStatus.UNAUTHORIZED,         "error.auth.token_blacklisted"),
    UNAUTHORIZED            (HttpStatus.UNAUTHORIZED,         "error.auth.unauthorized"),
    ACCESS_DENIED           (HttpStatus.FORBIDDEN,            "error.auth.access_denied"),

    // ─── VALIDATION ────────────────────────────────────────────────────────────
    INVALID_REQUEST         (HttpStatus.BAD_REQUEST,          "error.validation.invalid_request"),

    // ─── GENERAL ───────────────────────────────────────────────────────────────
    INTERNAL_SERVER_ERROR   (HttpStatus.INTERNAL_SERVER_ERROR,"error.general.internal_server"),
    NOT_FOUND               (HttpStatus.NOT_FOUND,            "error.general.not_found"),
    BAD_REQUEST             (HttpStatus.BAD_REQUEST,          "error.general.bad_request"),
    SERVICE_UNAVAILABLE     (HttpStatus.SERVICE_UNAVAILABLE,  "error.general.service_unavailable");

    // ──────────────────────────────────────────────────────────────────────────

    private final HttpStatus httpStatus;
    private final String messageKey;

    ApiErrorCode(HttpStatus httpStatus, String messageKey) {
        this.httpStatus = httpStatus;
        this.messageKey = messageKey;
    }
}
