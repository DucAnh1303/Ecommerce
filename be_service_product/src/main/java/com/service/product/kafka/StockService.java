package com.service.product.kafka;

import java.util.ArrayList;
import java.util.List;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.service.product.kafka.event.OrderCreatedEvent;
import com.service.product.kafka.event.PaymentFailedEvent;
import com.service.product.kafka.event.StockFailedEvent;
import com.service.product.kafka.event.StockReservedEvent;
import com.service.product.module.dto.stock.CheckStockRequest;
import com.service.product.module.dto.stock.CheckStockResponse;
import com.service.product.module.dto.stock.ReserveStockRequest;
import com.service.product.module.dto.stock.ReserveStockResponse;
import com.service.product.module.dto.stock.RollbackStockRequest;
import com.service.product.module.entity.ProductEntity;
import com.service.product.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * StockService – Xử lý reserve và rollback tồn kho trong Saga.
 *
 * Reserve Stock (khi nhận event order.created): 1. Kiểm tra từng sản phẩm đủ
 * tồn kho không 2a. Tất cả đủ → Trừ tồn kho (proStock - qty) → Publish
 * order.stock.reserved 2b. Thiếu bất kỳ 1 sản phẩm → Publish order.stock.failed
 *
 * Rollback Stock (khi nhận event order.payment.failed): 1. Cộng lại tồn kho đã
 * trừ trước đó (proStock + qty) 2. Lưu DB và log kết quả
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public static final String TOPIC_STOCK_RESERVED = "order.stock.reserved";
    public static final String TOPIC_STOCK_FAILED = "order.stock.failed";

    /**
     * [SAGA KAFKA] Kiểm tra tồn kho, trừ số lượng tồn kho trong DB và đẩy kết quả về Order Service qua Kafka.
     * - Nếu thiếu hàng → Publish stock.failed (STOCK_FAILED).
     * - Nếu đủ hàng → Trừ tồn kho trong DB + Publish stock.reserved (STOCK_RESERVED).
     */
    @Transactional
    public void reserveStock(OrderCreatedEvent event) {
        log.info("[Stock-Kafka] Bắt đầu kiểm tra và cập nhật tồn kho cho orderId={}", event.getOrderId());

        if (event.getItems() == null || event.getItems().isEmpty()) {
            publishStockFailed(event.getOrderId(), event.getUserId(), "Đơn hàng không có sản phẩm");
            return;
        }

        // Bước 1: Kiểm tra tính hợp lệ và số lượng tồn kho của toàn bộ sản phẩm
        for (OrderCreatedEvent.OrderItemEvent item : event.getItems()) {
            ProductEntity product = productRepository.findById(item.getProductId()).orElse(null);
            if (product == null) {
                publishStockFailed(event.getOrderId(), event.getUserId(), "Sản phẩm không tồn tại: productId=" + item.getProductId());
                return;
            }
            if (product.getProStock() < item.getQuantity()) {
                publishStockFailed(event.getOrderId(), event.getUserId(), String.format(
                        "Sản phẩm '%s' không đủ tồn kho (yêu cầu: %d, hiện có: %d)",
                        product.getProName(), item.getQuantity(), product.getProStock()
                ));
                return;
            }
            log.info("[Stock-Kafka] Sản phẩm productId={} hợp lệ (yêu cầu: {}, hiện có: {})",
                    product.getId(), item.getQuantity(), product.getProStock());
        }

        // Bước 2: Khi tất cả đều đủ hàng → Cập nhật trừ tồn kho trong DB
        for (OrderCreatedEvent.OrderItemEvent item : event.getItems()) {
            ProductEntity product = productRepository.findById(item.getProductId()).orElseThrow();
            int oldStock = product.getProStock();
            int newStock = oldStock - item.getQuantity();
            product.setProStock(newStock);
            productRepository.save(product);
            log.info("[Stock-Kafka] Đã trừ {} unit cho productId={} (Tồn cũ: {}, Tồn mới: {})",
                    item.getQuantity(), product.getId(), oldStock, newStock);
        }

        log.info("[Stock-Kafka] Đã cập nhật trừ tồn kho thành công cho orderId={}", event.getOrderId());

        // Bước 3: Đẩy kết quả về cho Order Service hoàn toàn bất đồng bộ qua Kafka Pub/Sub
        StockReservedEvent reservedEvent = StockReservedEvent.builder()
                .orderId(event.getOrderId())
                .userId(event.getUserId())
                .totalAmount(event.getTotalAmount())
                .build();
        kafkaTemplate.send(TOPIC_STOCK_RESERVED, event.getOrderId().toString(), reservedEvent)
                .whenComplete((res, ex) -> {
                    if (ex == null) {
                        log.info("[Stock-Kafka] Đã publish event 'order.stock.reserved' cho orderId={}", event.getOrderId());
                    } else {
                        log.error("[Stock-Kafka] Lỗi publish 'order.stock.reserved' cho orderId={}: {}", event.getOrderId(), ex.getMessage());
                    }
                });
    }

    /**
     * [SAGA KAFKA] Rollback tồn kho khi payment thất bại hoặc hủy đơn. Cộng lại
     * số lượng đã trừ trước đó.
     */
    @Transactional
    public void rollbackStock(PaymentFailedEvent event) {
        log.warn("[Stock-Kafka] Rollback stock cho orderId={}, lý do: {}", event.getOrderId(), event.getReason());
        if (event.getItemsToRollback() == null) {
            return;
        }

        for (PaymentFailedEvent.RollbackItem item : event.getItemsToRollback()) {
            if (item == null || item.getProductId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                continue;
            }
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                int oldStock = product.getProStock() > 0 ? product.getProStock() : 0;
                int newStock = oldStock + item.getQuantity();
                product.setProStock(newStock);
                productRepository.save(product);
                log.info("[Stock-Kafka] Đã hoàn trả {} unit cho productId={} (Tồn cũ: {}, Tồn mới: {})",
                        item.getQuantity(), item.getProductId(), oldStock, newStock);
            });
        }
        log.warn("[Stock-Kafka] Rollback hoàn tất cho orderId={}", event.getOrderId());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // [REST API] Hỗ trợ gọi kiểm tra / giữ tồn kho trực tiếp nếu cần
    // ═══════════════════════════════════════════════════════════════════════════
    /**
     * [REST API] Kiểm tra và cập nhật trừ tồn kho sản phẩm cho đơn hàng qua API.
     * Đẩy kết quả về cho Order Service qua cả Feign HTTP và Kafka Pub/Sub.
     */
    @Transactional
    public ReserveStockResponse reserveStockApi(ReserveStockRequest request) {
        log.info("[Stock-API] Nhận yêu cầu kiểm tra và giữ tồn kho cho orderId={}", request.getOrderId());

        if (request.getItems() == null || request.getItems().isEmpty()) {
            String error = "Đơn hàng không có sản phẩm";
            publishStockFailed(request.getOrderId(), request.getUserId(), error);
            return ReserveStockResponse.builder()
                    .orderId(request.getOrderId())
                    .success(false)
                    .message(error)
                    .build();
        }

        // Bước 1: Kiểm tra tính hợp lệ và tồn kho của toàn bộ sản phẩm trước
        for (ReserveStockRequest.StockItemDto item : request.getItems()) {
            ProductEntity product = productRepository.findById(item.getProductId()).orElse(null);
            if (product == null) {
                String error = "Sản phẩm không tồn tại: productId=" + item.getProductId();
                publishStockFailed(request.getOrderId(), request.getUserId(), error);
                return ReserveStockResponse.builder()
                        .orderId(request.getOrderId())
                        .success(false)
                        .message(error)
                        .build();
            }

            if (product.getProStock() < item.getQuantity()) {
                String error = String.format("Sản phẩm '%s' (ID: %d) không đủ tồn kho (cần: %d, còn lại: %d)",
                        product.getProName(), item.getProductId(), item.getQuantity(), product.getProStock());
                publishStockFailed(request.getOrderId(), request.getUserId(), error);
                return ReserveStockResponse.builder()
                        .orderId(request.getOrderId())
                        .success(false)
                        .message(error)
                        .build();
            }
        }

        // Bước 2: Khi tất cả đều đủ hàng → Cập nhật trừ tồn kho trong DB
        List<ReserveStockResponse.ReservedItemInfo> reservedInfoList = new ArrayList<>();
        for (ReserveStockRequest.StockItemDto item : request.getItems()) {
            ProductEntity product = productRepository.findById(item.getProductId()).orElseThrow();
            int oldStock = product.getProStock();
            int newStock = oldStock - item.getQuantity();
            product.setProStock(newStock);
            productRepository.save(product);

            reservedInfoList.add(ReserveStockResponse.ReservedItemInfo.builder()
                    .productId(product.getId())
                    .productName(product.getProName())
                    .quantity(item.getQuantity())
                    .remainingStock(newStock)
                    .build());
            log.info("[Stock-API] Đã trừ {} unit cho productId={} (Tồn cũ: {}, Tồn mới: {})",
                    item.getQuantity(), product.getId(), oldStock, newStock);
        }

        log.info("[Stock-API] Đã cập nhật trừ tồn kho thành công cho orderId={}", request.getOrderId());

        // Bước 3: Đẩy trạng thái sang Order Service qua Kafka Pub/Sub
        StockReservedEvent reservedEvent = StockReservedEvent.builder()
                .orderId(request.getOrderId())
                .userId(request.getUserId())
                .totalAmount(request.getTotalAmount())
                .build();
        kafkaTemplate.send(TOPIC_STOCK_RESERVED, request.getOrderId().toString(), reservedEvent)
                .whenComplete((res, ex) -> {
                    if (ex == null) {
                        log.info("[Stock-API] Đã publish thành công event 'order.stock.reserved' cho orderId={}", request.getOrderId());
                    } else {
                        log.error("[Stock-API] LỖI publish event 'order.stock.reserved' cho orderId={}: {}", request.getOrderId(), ex.getMessage());
                    }
                });

        return ReserveStockResponse.builder()
                .orderId(request.getOrderId())
                .success(true)
                .message("Đã cập nhật trừ tồn kho thành công. Đơn hàng chuyển sang trạng thái STOCK_RESERVED, sẵn sàng thanh toán.")
                .items(reservedInfoList)
                .build();
    }

    /**
     * [REST API] Hoàn trả tồn kho cho đơn hàng qua API.
     */
    @Transactional
    public ReserveStockResponse rollbackStockApi(RollbackStockRequest request) {
        log.warn("[Stock-API] Nhận yêu cầu rollback tồn kho cho orderId={}, lý do: {}", request.getOrderId(), request.getReason());
        List<ReserveStockResponse.ReservedItemInfo> rollbackInfoList = new ArrayList<>();

        for (RollbackStockRequest.RollbackItemDto item : request.getItems()) {
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                int oldStock = product.getProStock();
                product.setProStock(oldStock + item.getQuantity());
                productRepository.save(product);
                rollbackInfoList.add(ReserveStockResponse.ReservedItemInfo.builder()
                        .productId(product.getId())
                        .productName(product.getProName())
                        .quantity(item.getQuantity())
                        .remainingStock(product.getProStock())
                        .build()
                );
                log.info("[Stock-API] Đã hoàn trả {} unit cho productId={} (Tồn cũ: {}, Tồn mới: {})",
                        item.getQuantity(), item.getProductId(), oldStock, product.getProStock());
            });
        }

        return ReserveStockResponse.builder()
                .orderId(request.getOrderId())
                .success(true)
                .message("Đã hoàn trả tồn kho thành công cho đơn hàng: " + request.getOrderId())
                .items(rollbackInfoList)
                .build();
    }

    /**
     * [REST API] Kiểm tra tồn kho trước khi đặt hàng (chỉ đọc, không trừ kho).
     */
    @Transactional(readOnly = true)
    public CheckStockResponse checkStockApi(CheckStockRequest request) {
        List<CheckStockResponse.ItemAvailability> list = new ArrayList<>();
        boolean allAvailable = true;

        for (ReserveStockRequest.StockItemDto item : request.getItems()) {
            ProductEntity product = productRepository.findById(item.getProductId()).orElse(null);
            if (product == null) {
                allAvailable = false;
                list.add(CheckStockResponse.ItemAvailability.builder()
                        .productId(item.getProductId())
                        .requestedQuantity(item.getQuantity())
                        .currentStock(0)
                        .isAvailable(false)
                        .note("Sản phẩm không tồn tại")
                        .build());
            } else {
                boolean available = product.getProStock() >= item.getQuantity();
                if (!available) {
                    allAvailable = false;
                }
                list.add(CheckStockResponse.ItemAvailability.builder()
                        .productId(product.getId())
                        .productName(product.getProName())
                        .requestedQuantity(item.getQuantity())
                        .currentStock(product.getProStock())
                        .isAvailable(available)
                        .note(available ? "Đủ hàng" : "Hết hàng hoặc không đủ số lượng yêu cầu")
                        .build());
            }
        }

        return CheckStockResponse.builder()
                .allAvailable(allAvailable)
                .items(list)
                .build();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private void publishStockFailed(Long orderId, Long userId, String reason) {
        log.warn("[Stock] Reserve FAILED cho orderId={}: {}", orderId, reason);

        // Publish event qua Kafka Pub/Sub để Order Service đồng bộ trạng thái đơn hàng
        StockFailedEvent failedEvent = StockFailedEvent.builder()
                .orderId(orderId)
                .userId(userId)
                .reason(reason)
                .build();
        kafkaTemplate.send(TOPIC_STOCK_FAILED, orderId != null ? orderId.toString() : "unknown", failedEvent);
    }
}
