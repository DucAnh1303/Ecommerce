package com.service.product.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ghi đè các header X-User-* bằng dữ liệu lấy từ JWT đã verify.
 * Đảm bảo controller đọc {@code @RequestHeader("X-User-Id")} luôn đúng với token,
 * kể cả khi có ai đó gọi thẳng vào service (bỏ qua Gateway) và tự gửi header giả.
 */
public class UserHeaderRequestWrapper extends HttpServletRequestWrapper {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_NAME = "X-User-Name";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    /** key = tên header lower-case; value = null nghĩa là header bị xoá. */
    private final Map<String, String> overrides = new LinkedHashMap<>();
    private final Map<String, String> originalNames = new LinkedHashMap<>();

    private UserHeaderRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    /** Request có token hợp lệ → header lấy từ claims. */
    public static HttpServletRequest authenticated(HttpServletRequest request, Long userId, String userName, List<String> roles) {
        UserHeaderRequestWrapper wrapper = new UserHeaderRequestWrapper(request);
        wrapper.put(HEADER_USER_ID, userId != null ? userId.toString() : null);
        wrapper.put(HEADER_USER_NAME, userName);
        wrapper.put(HEADER_USER_ROLES, String.join(",", roles));
        return wrapper;
    }

    /** Request không có / sai token → xoá mọi X-User-* client tự gửi. */
    public static HttpServletRequest anonymous(HttpServletRequest request) {
        UserHeaderRequestWrapper wrapper = new UserHeaderRequestWrapper(request);
        wrapper.put(HEADER_USER_ID, null);
        wrapper.put(HEADER_USER_NAME, null);
        wrapper.put(HEADER_USER_ROLES, null);
        return wrapper;
    }

    private void put(String name, String value) {
        String key = name.toLowerCase(Locale.ROOT);
        overrides.put(key, value);
        originalNames.put(key, name);
    }

    @Override
    public String getHeader(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        return overrides.containsKey(key) ? overrides.get(key) : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (overrides.containsKey(key)) {
            String value = overrides.get(key);
            return value == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(value));
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> original = super.getHeaderNames();
        while (original != null && original.hasMoreElements()) {
            String name = original.nextElement();
            if (!overrides.containsKey(name.toLowerCase(Locale.ROOT))) {
                names.add(name);
            }
        }
        overrides.forEach((key, value) -> {
            if (value != null) {
                names.add(originalNames.get(key));
            }
        });
        return Collections.enumeration(names);
    }
}
