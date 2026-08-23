package com.service.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.service.order.dto.CreateOrderRequest;
import com.service.order.dto.OrderResponse;
import com.service.order.dto.PaymentConfirmRequest;
import com.service.order.dto.PaymentRequest;
import com.service.order.entity.OrderEntity;
import com.service.order.entity.OrderItemEntity;
import com.service.order.entity.OrderStatus;
import com.service.order.event.OrderCreatedEvent;
import com.service.order.event.PaymentFailedEvent;
import com.service.order.event.PaymentSuccessEvent;
import com.service.order.event.StockFailedEvent;
import com.service.order.event.StockReservedEvent;
import com.service.order.exception.OrderErrorCode;
import com.service.order.exception.OrderException;
import com.service.order.kafka.OrderEventProducer;
import com.service.order.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OrderService – Điều phối toàn bộ luồng SAGA cho đơn hàng và thanh toán.
 *
 * Luồng Saga chặt chẽ:
 *  [STEP 1]  createOrder()         → Lưu PENDING → Publish order.created
 *  [STEP 2a] handleStockReserved() ← Nhận stock.reserved từ Product Service → STOCK_RESERVED (Chờ thanh toán)
 *  [STEP 2b] handleStockFailed()   ← Nhận stock.failed từ Product Service   → STOCK_FAILED (Hết hàng)
 *  [STEP 3]  payOrder()            → Bắt buộc đơn ở STOCK_RESERVED
 *                                    • COD: Hoàn tất ngay → COMPLETED + Publish payment.success
 *                                    • Online (VNPAY, MOMO, CARD, QR...):
 *                                      - Nếu mockAutoConfirm=true → COMPLETED ngay + Publish payment.success
 *                                      - Mặc định: Chuyển PAYMENT_PENDING + Trả về mockPaymentInfo (QR/URL confirm)
 *  [STEP 4]  confirmPayment()      ← Webhook IPN / Admin xác nhận
 *                                    • success=true: COMPLETED + Publish payment.success
 *                                    • success=false: PAYMENT_FAILED + Publish payment.failed (Product rollback tồn kho)
 *  [STEP 5]  cancelOrder()         → CANCELLED + Publish payment.failed (Product rollback tồn kho)
 *  [CONSUMER] handlePaymentSuccess() / handlePaymentFailed() ← Xử lý đồng bộ event từ Kafka
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventProducer eventProducer;

    // ═══════════════════════════════════════════════════════════════════════════
    // [STEP 1] Tạo đơn hàng – Khởi tạo Saga
    // ═══════════════════════════════════════════════════════════════════════════
    @Transactional
    public OrderResponse createOrder(Long userId, String userName, CreateOrderRequest request) {
        log.info("[Saga][STEP-1] Bắt đầu tạo đơn hàng cho userId={}", userId);

        // Tính tổng tiền đơn hàng
        long totalAmount = request.getItems().stream()
                .mapToLong(item -> item.getUnitPrice() * item.getQuantity())
                .sum();

        // Tạo Order entity ở trạng thái PENDING
        OrderEntity order = OrderEntity.builder()
                .userId(userId)
                .userName(userName)
                .totalAmount(totalAmount)
                .shippingAddress(request.getShippingAddress())
                .note(request.getNote())
                .status(OrderStatus.PENDING)
                .build();

        // Tạo OrderItems
        List<OrderItemEntity> items = request.getItems().stream()
                .map(item -> OrderItemEntity.builder()
                .order(order)
                .productId(item.getProductId())
                .productName(item.getProductName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getUnitPrice() * item.getQuantity())
                .build()).toList();
        order.getItems().addAll(items);

        // Lưu vào DB
        OrderEntity saved = orderRepository.save(order);
        log.info("[Saga][STEP-1] Đã lưu orderId={} – status=PENDING", saved.getId());

        // Publish event qua Kafka để Product Service kiểm tra và giữ tồn kho
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(saved.getId())
                .userId(userId)
                .userName(userName)
                .totalAmount(totalAmount)
                .shippingAddress(request.getShippingAddress())
                .items(request.getItems().stream()
                        .map(item -> OrderCreatedEvent.OrderItemEvent.builder()
                        .productId(item.getProductId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .build())
                        .collect(Collectors.toList()))
                .build();

        eventProducer.publishOrderCreated(event);
        log.info("[Saga][STEP-1] Đã publish event 'order.created' cho orderId={}", saved.getId());

        return OrderResponse.from(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // [STEP 2a] Xử lý khi Product Service đã reserve tồn kho thành công
    // ═══════════════════════════════════════════════════════════════════════════
    @Transactional
    public void handleStockReserved(StockReservedEvent event) {
        OrderEntity order = findOrderOrThrow(event.getOrderId());
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.STOCK_RESERVED);
            orderRepository.save(order);
            log.info("[Saga][STEP-2a] orderId={} → STOCK_RESERVED. Đã giữ tồn kho thành công, sẵn sàng chờ thanh toán.", order.getId());
        } else {
            log.info("[Saga][STEP-2a] orderId={} đang ở trạng thái {}, giữ nguyên.", order.getId(), order.getStatus());
        }
    }

    /**
     * Xác nhận giữ tồn kho thành công cho đơn hàng qua API trực tiếp (Cập nhật sang STOCK_RESERVED).
     */
    @Transactional
    public OrderResponse markStockReserved(Long orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.STOCK_RESERVED);
            OrderEntity saved = orderRepository.save(order);
            log.info("[Order] orderId={} → STOCK_RESERVED thành công qua API.", orderId);
            return OrderResponse.from(saved);
        }
        return OrderResponse.from(order);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // [STEP 2b] Stock thất bại (Hết hàng) → Đánh dấu Order STOCK_FAILED
    // ═══════════════════════════════════════════════════════════════════════════
    @Transactional
    public void handleStockFailed(StockFailedEvent event) {
        OrderEntity order = findOrderOrThrow(event.getOrderId());
        order.setStatus(OrderStatus.STOCK_FAILED);
        order.setFailReason(event.getReason());
        orderRepository.save(order);
        log.warn("[Saga][STEP-2b] orderId={} → STOCK_FAILED. Lý do: {}", order.getId(), event.getReason());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // [STEP 3] Khách hàng thực hiện Thanh toán đơn hàng
    // ═══════════════════════════════════════════════════════════════════════════
    @Transactional
    public OrderResponse payOrder(Long orderId, Long userId, PaymentRequest request) {
        OrderEntity order = findOrderOrThrow(orderId);

        // 1. Kiểm tra quyền sở hữu đơn hàng
        if (!order.getUserId().equals(userId)) {
            throw new OrderException(OrderErrorCode.ACCESS_DENIED, "Bạn không có quyền thanh toán đơn hàng này");
        }

        // 2. Nếu đơn hàng đã hoàn tất trước đó
        if (order.getStatus() == OrderStatus.COMPLETED) {
            log.info("[Payment] orderId={} đã thanh toán hoàn tất trước đó.", orderId);
            return OrderResponse.from(order);
        }

        // 3. Nếu đơn hàng đang ở trạng thái chờ xác nhận cổng thanh toán
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            log.info("[Payment] orderId={} đang ở trạng thái PAYMENT_PENDING, chờ xác nhận từ cổng thanh toán.", orderId);
            OrderResponse response = OrderResponse.from(order);
            attachMockPaymentInfo(response, order);
            return response;
        }

        // 4. Kiểm tra chặt chẽ trạng thái: Bắt buộc phải là STOCK_RESERVED
        if (order.getStatus() == OrderStatus.PENDING) {
            throw new OrderException(OrderErrorCode.ORDER_INVALID_STATUS,
                    "Đơn hàng đang chờ giữ tồn kho từ Product Service. Vui lòng thử lại sau giây lát.");
        }
        if (order.getStatus() != OrderStatus.STOCK_RESERVED) {
            throw new OrderException(OrderErrorCode.ORDER_INVALID_STATUS,
                    String.format("Không thể thanh toán đơn hàng ở trạng thái '%s'.", order.getStatus()));
        }

        String method = request.getPaymentMethod() != null ? request.getPaymentMethod().toUpperCase() : "COD";
        log.info("[Payment] Bắt đầu xử lý thanh toán cho orderId={}, phương thức={}", orderId, method);

        if ("COD".equalsIgnoreCase(method)) {
            // Thanh toán khi nhận hàng -> Đơn hàng hoàn tất đặt
            order.setPaymentMethod("COD");
            order.setPaidAmount(0L);
            order.setStatus(OrderStatus.COMPLETED);
            OrderEntity saved = orderRepository.save(order);

            PaymentSuccessEvent successEvent = PaymentSuccessEvent.builder()
                    .orderId(saved.getId())
                    .userId(saved.getUserId())
                    .totalAmount(saved.getTotalAmount())
                    .paidAmount(0L)
                    .paymentMethod("COD")
                    .transactionId("COD-" + saved.getId())
                    .paidAt(LocalDateTime.now())
                    .build();
            eventProducer.publishPaymentSuccess(successEvent);
            log.info("[Payment] Đơn hàng orderId={} chọn COD → COMPLETED thành công", saved.getId());

            return OrderResponse.from(saved);
        } else if (Boolean.TRUE.equals(request.getMockAutoConfirm())) {
            // Mock thanh toán tự động duyệt ngay trong 1 bước (cho môi trường test nhanh)
            String transactionId = "TXN-MOCK-" + method + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            LocalDateTime now = LocalDateTime.now();

            order.setPaymentMethod(method);
            order.setTransactionId(transactionId);
            order.setPaidAmount(order.getTotalAmount());
            order.setPaidAt(now);
            order.setStatus(OrderStatus.COMPLETED);
            OrderEntity saved = orderRepository.save(order);

            PaymentSuccessEvent successEvent = PaymentSuccessEvent.builder()
                    .orderId(saved.getId())
                    .userId(saved.getUserId())
                    .totalAmount(saved.getTotalAmount())
                    .paidAmount(saved.getTotalAmount())
                    .paymentMethod(method)
                    .transactionId(transactionId)
                    .paidAt(now)
                    .build();
            eventProducer.publishPaymentSuccess(successEvent);
            log.info("[Payment-Mock] Đơn hàng orderId={} Auto-Confirm thành công → COMPLETED (txnId={})", saved.getId(), transactionId);

            return OrderResponse.from(saved);
        } else {
            // Thanh toán Online (VNPAY, MOMO, CARD, QR_PAY...) → Chuyển sang PAYMENT_PENDING chờ xác nhận
            String transactionId = "TXN-" + method + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            order.setPaymentMethod(method);
            order.setTransactionId(transactionId);
            order.setStatus(OrderStatus.PAYMENT_PENDING);
            OrderEntity saved = orderRepository.save(order);

            log.info("[Payment] orderId={} → PAYMENT_PENDING (txnId={}). Đang chờ cổng thanh toán {} phản hồi.",
                    saved.getId(), transactionId, method);

            OrderResponse response = OrderResponse.from(saved);
            attachMockPaymentInfo(response, saved);
            return response;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // [STEP 4] Xác nhận thanh toán từ Webhook / Cổng thanh toán / Admin
    // ═══════════════════════════════════════════════════════════════════════════
    @Transactional
    public OrderResponse confirmPayment(Long orderId, PaymentConfirmRequest request) {
        OrderEntity order = findOrderOrThrow(orderId);

        // Ràng buộc trạng thái: Chỉ xác nhận khi đơn đang ở PAYMENT_PENDING hoặc STOCK_RESERVED
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING && order.getStatus() != OrderStatus.STOCK_RESERVED) {
            throw new OrderException(OrderErrorCode.ORDER_INVALID_STATUS,
                    String.format("Không thể xác nhận thanh toán cho đơn hàng ở trạng thái '%s'.", order.getStatus()));
        }

        if (Boolean.TRUE.equals(request.getSuccess())) {
            String txnId = request.getTransactionId() != null ? request.getTransactionId() : order.getTransactionId();
            if (txnId == null) {
                txnId = "TXN-CONFIRM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }
            LocalDateTime now = LocalDateTime.now();
            Long paidAmount = request.getPaidAmount() != null ? request.getPaidAmount() : order.getTotalAmount();

            order.setTransactionId(txnId);
            order.setPaidAmount(paidAmount);
            if (request.getPaymentMethod() != null) {
                order.setPaymentMethod(request.getPaymentMethod());
            }
            order.setPaidAt(now);
            order.setStatus(OrderStatus.COMPLETED);
            order.setFailReason(null);
            OrderEntity saved = orderRepository.save(order);

            PaymentSuccessEvent successEvent = PaymentSuccessEvent.builder()
                    .orderId(saved.getId())
                    .userId(saved.getUserId())
                    .totalAmount(saved.getTotalAmount())
                    .paidAmount(paidAmount)
                    .paymentMethod(saved.getPaymentMethod())
                    .transactionId(txnId)
                    .paidAt(now)
                    .build();
            eventProducer.publishPaymentSuccess(successEvent);
            log.info("[Saga][Confirm] Xác nhận thanh toán THÀNH CÔNG cho orderId={} – txnId={}, số tiền={}", orderId, txnId, paidAmount);

            return OrderResponse.from(saved);
        } else {
            String reason = request.getFailReason() != null ? request.getFailReason() : "Cổng thanh toán báo giao dịch thất bại / Bị từ chối";
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setFailReason(reason);
            OrderEntity saved = orderRepository.save(order);

            // Kích hoạt Saga rollback tồn kho trong Product Service
            publishRollbackEvent(saved, reason);
            log.warn("[Saga][Confirm] Xác nhận thanh toán THẤT BÀI cho orderId={} – đã kích hoạt Saga rollback tồn kho. Lý do: {}", orderId, reason);

            return OrderResponse.from(saved);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // [STEP 5] Hủy đơn hàng và tự động giải phóng tồn kho
    // ═══════════════════════════════════════════════════════════════════════════
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long userId) {
        OrderEntity order = findOrderOrThrow(orderId);

        if (!order.getUserId().equals(userId)) {
            throw new OrderException(OrderErrorCode.ACCESS_DENIED, "Bạn không có quyền hủy đơn hàng này");
        }

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new OrderException(OrderErrorCode.ORDER_ALREADY_COMPLETED, "Đơn hàng đã thanh toán hoàn tất, không thể hủy qua API này");
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            return OrderResponse.from(order);
        }

        OrderStatus previousStatus = order.getStatus();

        order.setStatus(OrderStatus.CANCELLED);
        order.setFailReason("Khách hàng chủ động hủy đơn");
        OrderEntity saved = orderRepository.save(order);

        // Kích hoạt Saga rollback hoàn trả tồn kho nếu đơn đã từng giữ kho hoặc đang chờ thanh toán
        if (previousStatus != OrderStatus.STOCK_FAILED) {
            publishRollbackEvent(saved, "Khách hàng hủy đơn hàng");
            log.warn("[Saga][Cancel] Đã hủy orderId={} và kích hoạt Saga rollback tồn kho (trạng thái trước: {})", orderId, previousStatus);
        }

        return OrderResponse.from(saved);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // [KAFKA CONSUMER HANDLERS]
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Đồng bộ khi nhận event payment.success từ Kafka
     */
    @Transactional
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        OrderEntity order = findOrderOrThrow(event.getOrderId());
        if (order.getStatus() != OrderStatus.COMPLETED) {
            order.setStatus(OrderStatus.COMPLETED);
            order.setTransactionId(event.getTransactionId());
            if (event.getPaymentMethod() != null) {
                order.setPaymentMethod(event.getPaymentMethod());
            }
            order.setPaidAmount(event.getPaidAmount());
            order.setPaidAt(event.getPaidAt() != null ? event.getPaidAt() : LocalDateTime.now());
            orderRepository.save(order);
            log.info("[Saga][STEP-3a] Kafka Consumer: orderId={} → COMPLETED (txnId={})", event.getOrderId(), event.getTransactionId());
        }
    }

    /**
     * Đồng bộ khi nhận event payment.failed từ Kafka
     */
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event) {
        OrderEntity order = findOrderOrThrow(event.getOrderId());
        if (order.getStatus() != OrderStatus.PAYMENT_FAILED && order.getStatus() != OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setFailReason(event.getReason());
            orderRepository.save(order);
            log.warn("[Saga][STEP-3b] Kafka Consumer: orderId={} → PAYMENT_FAILED. Lý do: {}", event.getOrderId(), event.getReason());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Query APIs
    // ═══════════════════════════════════════════════════════════════════════════
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long orderId) {
        OrderEntity order = findOrderOrThrow(orderId);
        OrderResponse response = OrderResponse.from(order);
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            attachMockPaymentInfo(response, order);
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersByUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreateTimeDesc(userId)
                .stream()
                .map(order -> {
                    OrderResponse res = OrderResponse.from(order);
                    if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
                        attachMockPaymentInfo(res, order);
                    }
                    return res;
                })
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════
    private OrderEntity findOrderOrThrow(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.ORDER_NOT_FOUND, "Không tìm thấy đơn hàng ID: " + orderId));
    }

    private void publishRollbackEvent(OrderEntity order, String reason) {
        List<PaymentFailedEvent.RollbackItem> rollbackItems = order.getItems().stream()
                .map(i -> PaymentFailedEvent.RollbackItem.builder()
                .productId(i.getProductId())
                .quantity(i.getQuantity())
                .build())
                .collect(Collectors.toList());

        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .orderId(order.getId())
                .userId(order.getUserId())
                .reason(reason)
                .itemsToRollback(rollbackItems)
                .build();

        eventProducer.publishPaymentFailed(event);
        log.info("[Saga][Rollback] Đã publish event 'order.payment.failed' để rollback tồn kho cho orderId={}", order.getId());
    }

    private void attachMockPaymentInfo(OrderResponse response, OrderEntity order) {
        String method = order.getPaymentMethod() != null ? order.getPaymentMethod() : "ONLINE";
        String txnId = order.getTransactionId() != null ? order.getTransactionId() : "TXN-TEST";
        response.setMockPaymentInfo(OrderResponse.MockPaymentInfo.builder()
                .paymentUrl("http://localhost:8080/api/v1/order/" + order.getId() + "/confirm-payment")
                .qrCodeData(String.format("00020101021238540010A0000007270124000697042201100001234567895303704540%d5802VN5911SHOPDA6007HA_NOI62200816%s6304",
                        order.getTotalAmount(), txnId))
                .deepLink("shopda://payment/" + txnId)
                .testInstruction("Mô phỏng thanh toán Test: Gọi POST /api/v1/order/" + order.getId()
                        + "/confirm-payment với body: {\"transactionId\": \"" + txnId + "\", \"success\": true, \"paidAmount\": "
                        + order.getTotalAmount() + "} để xác nhận hoàn tất giao dịch.")
                .build());
    }
}
