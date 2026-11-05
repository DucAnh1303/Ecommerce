package com.service.notice.notice.security;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Principal gắn vào mỗi WebSocket session sau khi CONNECT thành công.
 *
 * <p>{@link #getName()} trả về <b>userId</b> → dùng làm khóa cho user destination,
 * nên server chỉ cần {@code convertAndSendToUser(userId, "/queue/order", payload)}.
 */
public record StompPrincipal(Long userId, String userName, Set<String> roles, String jti, long expiresAt)
        implements Principal {

    public static StompPrincipal from(JwtTokenVerifier.AuthUser user) {
        return new StompPrincipal(user.userId(), user.userName(), normalize(user.roles()), user.jti(), user.expiresAt());
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    public boolean hasAnyRole(Set<String> required) {
        return required.isEmpty() || required.stream().anyMatch(roles::contains);
    }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    /** Chuẩn hoá role: bỏ prefix ROLE_ và upper-case. */
    private static Set<String> normalize(List<String> roles) {
        return roles.stream()
                .map(r -> r.toUpperCase().startsWith("ROLE_") ? r.substring(5) : r)
                .map(String::toUpperCase)
                .collect(Collectors.toUnmodifiableSet());
    }
}
