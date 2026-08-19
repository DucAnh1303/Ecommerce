package com.service.product.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.product.kafka.event.OrderCreatedEvent;
import com.service.product.kafka.event.PaymentFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer cho Product Service.
 *
 * Lắng nghe:
 *  - order.created      → reserve stock cho đơn hàng
 *  - order.payment.failed → rollback stock khi payment thất bại
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductKafkaConsumer {

    private final StockService stockService;
    private final ObjectMapper objectMapper;

    private static final String GROUP_PRODUCT = "product-service-group";

    /**
     * [STEP 2] Nhận order.created → kiểm tra và reserve tồn kho.
     * Sau đó publish stock.reserved hoặc stock.failed.
     */
    @KafkaListener(
            topics = "order.created",
            groupId = GROUP_PRODUCT,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCreated(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            OrderCreatedEvent event = parseEvent(record.value(), OrderCreatedEvent.class);
            log.info("[Product-Kafka] Nhận order.created – orderId={}", event.getOrderId());
            stockService.reserveStock(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Product-Kafka] Lỗi xử lý order.created: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * [STEP ROLLBACK] Nhận order.payment.failed → rollback tồn kho đã reserve.
     */
    @KafkaListener(
            topics = "order.payment.failed",
            groupId = GROUP_PRODUCT,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentFailed(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            PaymentFailedEvent event = parseEvent(record.value(), PaymentFailedEvent.class);
            log.warn("[Product-Kafka] Nhận order.payment.failed – orderId={}, rollback stock...", event.getOrderId());
            stockService.rollbackStock(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Product-Kafka] Lỗi rollback stock: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T parseEvent(Object value, Class<T> clazz) {
        if (value == null) return null;
        if (clazz.isInstance(value)) {
            return (T) value;
        }
        if (value instanceof String str) {
            try {
                return objectMapper.readValue(str, clazz);
            } catch (Exception e) {
                log.error("[Kafka-Parse] Lỗi parse JSON string sang {}: {}", clazz.getSimpleName(), e.getMessage());
                throw new RuntimeException(e);
            }
        }
        if (value instanceof byte[] bytes) {
            try {
                return objectMapper.readValue(bytes, clazz);
            } catch (Exception e) {
                log.error("[Kafka-Parse] Lỗi parse byte[] sang {}: {}", clazz.getSimpleName(), e.getMessage());
                throw new RuntimeException(e);
            }
        }
        try {
            return objectMapper.convertValue(value, clazz);
        } catch (Exception e) {
            log.error("[Kafka-Parse] Lỗi convertValue sang {}: {}", clazz.getSimpleName(), e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
