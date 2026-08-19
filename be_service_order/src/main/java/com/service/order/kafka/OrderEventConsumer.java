package com.service.order.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.order.config.KafkaConfig;
import com.service.order.event.PaymentFailedEvent;
import com.service.order.event.PaymentSuccessEvent;
import com.service.order.event.StockFailedEvent;
import com.service.order.event.StockReservedEvent;
import com.service.order.service.OrderService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Kafka Consumer – lắng nghe các Saga events và điều phối luồng Order.
 *
 * Consumer Group: order-service-group Concurrency: 3 threads (xử lý 3 partition
 * song song)
 *
 * Luồng Saga: [2a] stock.reserved → tiến hành payment (simulate) [2b]
 * stock.failed → đánh dấu Order FAILED [3a] payment.success → đánh dấu Order
 * COMPLETED [3b] payment.failed → đánh dấu Order PAYMENT_FAILED
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    /**
     * [STEP 2a] Nhận kết quả reserve stock thành công từ Product Service.
     * → Cập nhật đơn hàng sang STOCK_RESERVED, sẵn sàng chờ thanh toán.
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_STOCK_RESERVED,
            groupId = KafkaConfig.GROUP_ORDER_SERVICE,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onStockReserved(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            StockReservedEvent event = parseEvent(record.value(), StockReservedEvent.class);
            log.info("[Saga][STEP-2a] Stock reserved cho orderId={}, sẵn sàng chờ khách hàng thanh toán", event.getOrderId());
            orderService.handleStockReserved(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Saga][STEP-2a] Error xử lý stock.reserved: {}", e.getMessage(), e);
            // Không ack → Kafka retry 3 lần → DLQ
            throw new RuntimeException(e);
        }
    }

    /**
     * [STEP 2b] Nhận kết quả reserve stock thất bại từ Product Service. → Đánh
     * dấu Order STOCK_FAILED, notify user.
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_STOCK_FAILED,
            groupId = KafkaConfig.GROUP_ORDER_SERVICE,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onStockFailed(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            StockFailedEvent event = parseEvent(record.value(), StockFailedEvent.class);
            log.warn("[Saga][STEP-2b] Stock thất bại cho orderId={} – {}", event.getOrderId(), event.getReason());
            orderService.handleStockFailed(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Saga][STEP-2b] Error xử lý stock.failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * [STEP 3a] Nhận kết quả thanh toán thành công. → Đánh dấu Order COMPLETED.
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_PAYMENT_SUCCESS,
            groupId = KafkaConfig.GROUP_ORDER_SERVICE,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentSuccess(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            PaymentSuccessEvent event = parseEvent(record.value(), PaymentSuccessEvent.class);
            log.info("[Saga][STEP-3a] Payment thành công cho orderId={}", event.getOrderId());
            orderService.handlePaymentSuccess(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Saga][STEP-3a] Error xử lý payment.success: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * [STEP 3b] Nhận kết quả thanh toán thất bại. → Đánh dấu Order
     * PAYMENT_FAILED (Product Service sẽ rollback stock qua event riêng).
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_PAYMENT_FAILED,
            groupId = KafkaConfig.GROUP_ORDER_SERVICE,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentFailed(ConsumerRecord<String, Object> record, Acknowledgment ack) {
        try {
            PaymentFailedEvent event = parseEvent(record.value(), PaymentFailedEvent.class);
            log.warn("[Saga][STEP-3b] Payment thất bại cho orderId={} – {}", event.getOrderId(), event.getReason());
            orderService.handlePaymentFailed(event);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Saga][STEP-3b] Error xử lý payment.failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Parse message từ Kafka thành kiểu event cụ thể (hỗ trợ String, Map, byte[], Object).
     */
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
