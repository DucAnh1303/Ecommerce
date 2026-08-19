package com.service.product.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * Cấu hình MessageSource đa ngôn ngữ.
 * <p>
 * Ngôn ngữ được xác định qua header {@code Accept-Language} trong request.<br>
 * Mặc định sử dụng tiếng Anh (en) nếu không có header hoặc ngôn ngữ không được
 * hỗ trợ.
 * </p>
 *
 * <pre>
 * Accept-Language: vi   →  messages_vi.properties
 * Accept-Language: en   →  messages_en.properties
 * (không có header)     →  messages_en.properties  (default)
 * </pre>
 */
@Configuration
public class MessageConfig {

    /**
     * MessageSource đọc file từ {@code classpath:i18n/messages_*.properties}.
     */
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("i18n/messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(true); // fallback: trả về key nếu không tìm thấy message
        return source;
    }

    /**
     * Resolve locale từ header {@code Accept-Language}.
     * Hỗ trợ: en, vi. Mặc định: en.
     */
    @Bean
    public AcceptHeaderLocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(
                Locale.ENGLISH, Locale.of("vi"))
        );
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }
}
