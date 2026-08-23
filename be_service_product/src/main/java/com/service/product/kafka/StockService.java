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
    private final com.service.product.config.feign.OrderFeignClient orderFeignClient;

    public static final String TOPIC_STOCK_RESERVED = "order.stock.reserved";
    public static final String TOPIC_STOCK_FAILED = "order.stock.failed";

    /**
     * [SAGA KAFKA] Kiểm tra và đảm bảo trạng thái/số lượng sản phẩm cho toàn bộ items trong đơn hàng.
     * Nếu bất kỳ sản phẩm nào thiếu hàng → Publish stock.failed.
     * Nếu hợp lệ → Publish stock.reserved. (Không trừ trực tiếp tồn kho ở bước này).
     */
    @Transactional(readOnly = true)
    public void reserveStock(OrderCreatedEvent event) {
        log.info("[Stock-Kafka] Bắt đầu kiểm tra trạng thái và số lượng sản phẩm cho orderId={}", event.getOrderId());

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

        log.info("[Stock-Kafka] Đã kiểm tra trạng thái và số lượng sản phẩm hợp lệ cho orderId={}", event.getOrderId());

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
     * [REST API] Xác nhận trạng thái và số lượng tồn kho sản phẩm cho đơn hàng (Không trừ trực tiếp tồn kho).
     */
    @Transactional(readOnly = true)
    public ReserveStockResponse reserveStockApi(ReserveStockRequest request) {
        log.info("[Stock-API] Nhận yêu cầu xác nhận trạng thái sản phẩm cho orderId={}", request.getOrderId());
        List<ReserveStockResponse.ReservedItemInfo> reservedInfoList = new ArrayList<>();

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

            reservedInfoList.add(ReserveStockResponse.ReservedItemInfo.builder()
                    .productId(product.getId())
                    .productName(product.getProName())
                    .quantity(item.getQuantity())
                    .remainingStock(product.getProStock())
                    .build());
        }

        log.info("[Stock-API] Đã xác nhận trạng thái sản phẩm và tồn kho hợp lệ cho orderId={}", request.getOrderId());

        // 1. Cập nhật trạng thái sang Order Service qua Feign HTTP
        try {
            orderFeignClient.markStockReserved(null, request.getOrderId());
            log.info("[Stock-API] Đã đồng bộ trạng thái orderId={} sang STOCK_RESERVED qua Feign Client", request.getOrderId());
        } catch (Exception e) {
            log.warn("[Stock-API] Feign sync sang Order Service: {}. Tiếp tục publish Kafka event.", e.getMessage());
        }

        // 2. Publish event order.stock.reserved để toàn bộ hệ thống đồng bộ
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
                .message("Đã xác nhận trạng thái sản phẩm và số lượng tồn kho hợp lệ. Đơn hàng sẵn sàng để thanh toán.")
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
        StockFailedEvent failedEvent = StockFailedEvent.builder()
                .orderId(orderId)
                .userId(userId)
                .reason(reason)
                .build();
        kafkaTemplate.send(TOPIC_STOCK_FAILED, orderId != null ? orderId.toString() : "unknown", failedEvent);
    }
}
