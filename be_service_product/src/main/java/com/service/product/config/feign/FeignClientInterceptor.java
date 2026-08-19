package com.service.product.config.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Feign RequestInterceptor tự động chuyển tiếp Authorization Header (JWT Token)
 * từ Request hiện tại sang các Service khác qua Feign.
 */
@Slf4j
@Configuration
public class FeignClientInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String authorization = request.getHeader(AUTHORIZATION_HEADER);
            if (authorization != null && !authorization.isBlank()) {
                template.header(AUTHORIZATION_HEADER, authorization);
                log.debug("[FeignInterceptor] Đã đính kèm Authorization Token vào Feign request: {}", template.url());
            }
        }
    }
}
