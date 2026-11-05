package com.service.notice.notice.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Danh mục các nghiệp vụ (business) có thể đăng ký nhận thông báo.
 *
 * <p>Mỗi business khai báo:
 * <ul>
 *   <li>{@code scope} – USER: kênh riêng từng user ({@code /user/queue/...}), Spring tự đảm bảo
 *       chỉ đúng user nhận; TOPIC: kênh dùng chung ({@code /topic/...}).</li>
 *   <li>{@code destination} – đích client SUBSCRIBE. Với TOPIC có thể chứa biến {@code {id}}.</li>
 *   <li>{@code requiredRoles} – rỗng = mọi user đã đăng nhập; ngược lại phải có ít nhất 1 role.</li>
 * </ul>
 *
 * <p>Thêm nghiệp vụ mới: chỉ cần thêm 1 hằng số ở đây → interceptor tự whitelist & phân quyền.
 */
@Getter
@RequiredArgsConstructor
public enum BusinessType {

    // ─── Kênh hệ thống (2 chiều) – client nên SUBSCRIBE ngay sau CONNECT ──────────

    /** Phản hồi cho các lệnh client gửi lên /app/** (pong, kết quả đăng ký business...). */
    REPLY(Scope.USER, "/queue/reply", Set.of(),
            "Kênh phản hồi request/response 2 chiều"),

    /** Lỗi xử lý (subscribe bị từ chối, payload sai...). */
    ERRORS(Scope.USER, "/queue/errors", Set.of(),
            "Kênh nhận lỗi từ server"),

    // ─── Kênh nghiệp vụ ─────────────────────────────────────────────────────────

    /** Tổng hợp mọi thông báo cá nhân (inbox). */
    NOTIFICATION(Scope.USER, "/queue/notifications", Set.of(),
            "Hộp thư thông báo cá nhân – nhận mọi thông báo gửi riêng cho user"),

    /** Trạng thái đơn hàng của chính user (tạo đơn, giữ kho, hết hàng...). */
    ORDER(Scope.USER, "/queue/order", Set.of(),
            "Cập nhật trạng thái đơn hàng của tôi"),

    /** Kết quả thanh toán của chính user. */
    PAYMENT(Scope.USER, "/queue/payment", Set.of(),
            "Kết quả thanh toán đơn hàng của tôi"),

    /** Biến động 1 sản phẩm cụ thể (giá / tồn kho) – ai cũng theo dõi được. */
    PRODUCT(Scope.TOPIC, "/topic/product/{id}", Set.of(),
            "Theo dõi biến động giá / tồn kho của 1 sản phẩm"),

    /** Thông báo hệ thống gửi tới toàn bộ user. */
    SYSTEM(Scope.TOPIC, "/topic/system", Set.of(),
            "Thông báo hệ thống (bảo trì, khuyến mãi...)"),

    /** Dashboard admin – mọi biến động đơn hàng toàn hệ thống. */
    ADMIN_ORDER(Scope.TOPIC, "/topic/admin/orders", Set.of("ADMIN"),
            "Realtime toàn bộ đơn hàng (chỉ ADMIN)");

    public enum Scope { USER, TOPIC }

    /** Prefix của user destination (Spring rewrite /user/queue/x → /queue/x-user{session}). */
    public static final String USER_PREFIX = "/user";

    private final Scope scope;
    private final String destination;
    private final Set<String> requiredRoles;
    private final String description;

    /** Đích client cần SUBSCRIBE (đối với USER scope sẽ có prefix /user). */
    public String subscribeDestination() {
        return scope == Scope.USER ? USER_PREFIX + destination : destination;
    }

    /** Đích cụ thể cho TOPIC có tham số, ví dụ /topic/product/15. */
    public String resolve(Object id) {
        return destination.replace("{id}", String.valueOf(id));
    }

    public boolean isParameterized() {
        return destination.contains("{id}");
    }

    /**
     * Tìm business tương ứng với destination client SUBSCRIBE.
     * Chỉ chấp nhận destination khớp chính xác (hoặc khớp pattern {id} với id là chữ/số).
     */
    public static Optional<BusinessType> fromSubscribeDestination(String dest) {
        if (dest == null) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(b -> b.matches(dest)).findFirst();
    }

    private boolean matches(String dest) {
        String expected = subscribeDestination();
        if (!isParameterized()) {
            return expected.equals(dest);
        }
        String prefix = expected.substring(0, expected.indexOf("{id}"));
        if (!dest.startsWith(prefix)) {
            return false;
        }
        String id = dest.substring(prefix.length());
        return id.matches("[A-Za-z0-9_-]{1,64}");
    }
}
