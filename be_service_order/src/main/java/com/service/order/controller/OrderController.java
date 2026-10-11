package com.service.order.controller;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.service.order.dto.CreateOrderRequest;
import com.service.order.dto.OrderResponse;
import com.service.order.dto.PaymentConfirmRequest;
import com.service.order.dto.PaymentRequest;
import com.service.order.service.OrderService;

import io.github.resilience4j.ratelimiter.RateLimiter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Order REST Controller.
 *
 * Tất cả request đi qua Gateway → Gateway đã xác thực JWT và inject: - ser-Id :
 * userId - ate Limiter bảo vệ endpoint createOrder: - 100 request/giây/instance
 * - Vượt quá → 429 Too Many Requests
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final RateLimiter orderCreateRateLimiter;

    /**
     * Tạo đơn hàng mới – khởi động Saga.
     *
     * POST /order/create Header: X-User-Id, X-User-Name (từ Gateway) Body:
     * CreateOrderRequest
     */
    @PostMapping("/create")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Name") String userName,
            @Valid @RequestBody CreateOrderRequest request) {

        log.info("[Order] Nhận yêu cầu tạo đơn từ userId={}", userId);

        // Bọc trong Rate Limiter – nếu quá limit → RequestNotPermitted exception
        // GlobalExceptionHandler sẽ bắt và trả về 429
        Supplier<ResponseEntity<OrderResponse>> decorated = RateLimiter.decorateSupplier(
                orderCreateRateLimiter,
                () -> {
                    OrderResponse response = orderService.createOrder(userId, userName, request);
                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
                    // 202 Accepted vì đây là async process (Saga chạy ngầm)
                }
        );

        return decorated.get();
    }

    /**
     * Lấy chi tiết đơn hàng theo ID.
     *
     * GET /order/{id}
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId) {
        log.info("[Order] userId={} xem orderId={}", userId, orderId);
        return ResponseEntity.ok(orderService.getOrderById(orderId));
    }

    /**
     * Lấy tất cả đơn hàng của user hiện tại.
     *
     * GET /api/v1/order/my-orders
     */
    @GetMapping("/my-orders")
    public ResponseEntity<List<OrderResponse>> getMyOrders(
            @RequestHeader("X-User-Id") Long userId) {
        log.info("[Order] userId={} lấy danh sách đơn hàng", userId);
        return ResponseEntity.ok(orderService.getOrdersByUser(userId));
    }

    /**
     * Khách hàng thực hiện thanh toán đơn hàng (sau khi tồn kho đã được giữ:
     * STOCK_RESERVED).
     *
     * POST /api/v1/order/{orderId}/pay
     */
    @PostMapping("/{orderId}/pay")
    public ResponseEntity<OrderResponse> payOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody PaymentRequest request) {
        log.info("[Order] userId={} thanh toán orderId={}, phương thức={}", userId, orderId, request.getPaymentMethod());
        return ResponseEntity.ok(orderService.payOrder(orderId, userId, request));
    }

    /**
     * Xác nhận thanh toán từ Webhook / Cổng thanh toán (VNPAY / Momo IPN) hoặc
     * Admin.
     *
     * POST /api/v1/order/{orderId}/confirm-payment
     */
    @PostMapping("/{orderId}/confirm-payment")
    public ResponseEntity<OrderResponse> confirmPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody PaymentConfirmRequest request) {
        log.info("[Order] Nhận xác nhận thanh toán cho orderId={}, success={}", orderId, request.getSuccess());
        return ResponseEntity.ok(orderService.confirmPayment(orderId, request));
    }

    /**
     * Khách hàng hủy đơn hàng (khi chưa hoàn tất thanh toán) -> kích hoạt Saga
     * rollback tồn kho.
     *
     * POST /api/v1/order/{orderId}/cancel
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long orderId,
            @RequestHeader("X-User-Id") Long userId) {
        log.info("[Order] userId={} yêu cầu hủy orderId={}", userId, orderId);
        return ResponseEntity.ok(orderService.cancelOrder(orderId, userId));
    }
}
