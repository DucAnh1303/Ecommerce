package web.com.auth.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Chuẩn response trả về khi có lỗi.
 * <pre>
 * {
 *   "timestamp": "2025-06-20T15:00:00Z",
 *   "status": 401,
 *   "error": "INVALID_TOKEN",
 *   "message": "Token không hợp lệ hoặc đã hết hạn",
 *   "fields": { "field": "message" }   // chỉ có khi là validation error
 * }
 * </pre>
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final Instant timestamp;
    private final int status;
    private final String error;
    private final String message;

    /** Chứa danh sách lỗi field khi là validation error (nullable). */
    private final Map<String, String> fields;
}
