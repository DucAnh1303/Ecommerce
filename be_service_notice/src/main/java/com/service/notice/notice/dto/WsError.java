package com.service.notice.notice.dto;

import java.time.Instant;

/** Payload lỗi gửi về /user/queue/errors. */
public record WsError(String code, String message, Instant timestamp) {
    public static WsError of(String code, String message) {
        return new WsError(code, message, Instant.now());
    }
}
