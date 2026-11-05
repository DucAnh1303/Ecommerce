package com.service.notice.notice.websocket;

import com.service.notice.notice.dto.WsError;
import com.service.notice.notice.security.JwtTokenVerifier;
import com.service.notice.notice.security.StompPrincipal;
import com.service.notice.notice.security.TokenBlacklistChecker;
import com.service.notice.notice.topic.BusinessType;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * Chặn mọi frame STOMP client gửi lên (clientInboundChannel).
 *
 * <ul>
 *   <li><b>CONNECT</b>: bắt buộc JWT hợp lệ (header {@code Authorization: Bearer xxx} hoặc query
 *       {@code access_token}) → gắn {@link StompPrincipal}. Sai → ERROR frame + đóng kết nối.</li>
 *   <li><b>SUBSCRIBE</b>: chỉ cho phép destination nằm trong {@link BusinessType} và đúng role.
 *       Sai → bỏ frame, gửi lỗi về {@code /user/queue/errors} (không đóng kết nối).</li>
 *   <li><b>SEND</b>: chỉ cho phép gửi tới prefix {@code /app}.</li>
 *   <li>Mọi frame sau CONNECT: nếu token đã hết hạn → từ chối, client phải reconnect với token mới.</li>
 * </ul>
 */
@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtTokenVerifier tokenVerifier;
    private final TokenBlacklistChecker blacklistChecker;
    private final SimpMessagingTemplate messagingTemplate;

    public StompAuthChannelInterceptor(JwtTokenVerifier tokenVerifier,
                                       TokenBlacklistChecker blacklistChecker,
                                       @Lazy SimpMessagingTemplate messagingTemplate) {
        this.tokenVerifier = tokenVerifier;
        this.blacklistChecker = blacklistChecker;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message; // heartbeat frame
        }
        StompCommand command = accessor.getCommand();
        return switch (command) {
            case CONNECT, STOMP -> handleConnect(message, accessor);
            case SUBSCRIBE -> handleSubscribe(message, accessor);
            case SEND -> handleSend(message, accessor);
            default -> message;
        };
    }

    // ─── CONNECT ────────────────────────────────────────────────────────────────

    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null) {
            throw new MessageDeliveryException("Missing access token");
        }
        try {
            JwtTokenVerifier.AuthUser user = tokenVerifier.verify(token);
            if (blacklistChecker.isRevoked(user.jti())) {
                throw new MessageDeliveryException("Token has been revoked");
            }
            StompPrincipal principal = StompPrincipal.from(user);
            accessor.setUser(principal);
            log.info("[WS] CONNECT userId={} session={} roles={}",
                    principal.userId(), accessor.getSessionId(), principal.roles());
            return message;
        } catch (JwtException e) {
            log.warn("[WS] CONNECT bị từ chối session={}: {}", accessor.getSessionId(), e.getMessage());
            throw new MessageDeliveryException("Invalid access token");
        }
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String header = Optional.ofNullable(accessor.getFirstNativeHeader("Authorization"))
                .orElse(accessor.getFirstNativeHeader("authorization"));
        if (header != null && header.startsWith(BEARER)) {
            return header.substring(BEARER.length()).trim();
        }
        Map<String, Object> attrs = accessor.getSessionAttributes();
        Object fromQuery = attrs != null ? attrs.get(AuthHandshakeInterceptor.ATTR_TOKEN) : null;
        return fromQuery != null ? fromQuery.toString() : null;
    }

    // ─── SUBSCRIBE ──────────────────────────────────────────────────────────────

    private Message<?> handleSubscribe(Message<?> message, StompHeaderAccessor accessor) {
        StompPrincipal principal = requireActivePrincipal(accessor);
        String destination = accessor.getDestination();

        // SUBSCRIBE /app/** = request-reply 1 lần qua @SubscribeMapping (vd: /app/channels)
        if (destination != null && destination.startsWith("/app/")) {
            return message;
        }

        Optional<BusinessType> business = BusinessType.fromSubscribeDestination(destination);
        if (business.isEmpty()) {
            return reject(principal, "SUBSCRIBE_DENIED", "Destination không được hỗ trợ: " + destination);
        }
        if (!principal.hasAnyRole(business.get().getRequiredRoles())) {
            return reject(principal, "SUBSCRIBE_FORBIDDEN",
                    "Không đủ quyền đăng ký " + business.get() + " (" + destination + ")");
        }
        log.debug("[WS] SUBSCRIBE userId={} business={} dest={}", principal.userId(), business.get(), destination);
        return message;
    }

    // ─── SEND ───────────────────────────────────────────────────────────────────

    private Message<?> handleSend(Message<?> message, StompHeaderAccessor accessor) {
        StompPrincipal principal = requireActivePrincipal(accessor);
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/app/")) {
            return reject(principal, "SEND_DENIED", "Chỉ được gửi tới /app/**");
        }
        return message;
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private StompPrincipal requireActivePrincipal(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (!(user instanceof StompPrincipal principal)) {
            throw new MessageDeliveryException("Unauthenticated");
        }
        if (principal.isExpired()) {
            // Throw → Spring gửi ERROR frame & đóng kết nối → client tự reconnect bằng token mới
            throw new MessageDeliveryException("Access token expired, please reconnect");
        }
        return principal;
    }

    private Message<?> reject(StompPrincipal principal, String code, String reason) {
        log.warn("[WS] {} userId={} – {}", code, principal.userId(), reason);
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", WsError.of(code, reason));
        return null; // drop frame
    }
}
