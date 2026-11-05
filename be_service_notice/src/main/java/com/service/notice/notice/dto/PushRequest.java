package com.service.notice.notice.dto;

import com.service.notice.notice.topic.BusinessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Body cho REST nội bộ đẩy thông báo (service khác gọi sang).
 *
 * @param userId     bắt buộc với business scope USER
 * @param resourceId bắt buộc với business có tham số (PRODUCT)
 */
public record PushRequest(
        @NotNull BusinessType business,
        @NotBlank String type,
        NotificationMessage.Severity severity,
        @NotBlank String title,
        String content,
        Map<String, Object> data,
        Long userId,
        String resourceId) {
}
