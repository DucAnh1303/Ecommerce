package com.service.notice.notice.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.service.notice.notice.dto.NotificationMessage;
import com.service.notice.notice.dto.NotificationMessage.Severity;
import com.service.notice.notice.service.NotificationDispatcher;
import com.service.notice.notice.topic.BusinessType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consume Saga events của ORDER/PRODUCT service → chuyển thành thông báo realtime.
 *
 * <p>Consumer group riêng ({@code notice-service-group}) nên KHÔNG tranh message với order-service.
 * Value đọc dạng JSON string → parse {@link JsonNode}, không phụ thuộc class event của service khác.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventNotificationListener {

    public static final String TOPIC_ORDER_CREATED = "order.created";
    public static final String TOPIC_STOCK_RESERVED = "order.stock.reserved";
    public static final String TOPIC_STOCK_FAILED = "order.stock.failed";
    public static final String TOPIC_PAYMENT_SUCCESS = "order.payment.success";
    public static final String TOPIC_PAYMENT_FAILED = "order.payment.failed";

    private final NotificationDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_ORDER_CREATED)
    public void onOrderCreated(ConsumerRecord<String, String> record) {
        handle(record, BusinessType.ORDER, "ORDER_CREATED", Severity.INFO,
                "Đặt hàng thành công", e -> "Đơn hàng #" + e.path("orderId").asText() + " đã được tạo, đang kiểm tra tồn kho.");
    }

    @KafkaListener(topics = TOPIC_STOCK_RESERVED)
    public void onStockReserved(ConsumerRecord<String, String> record) {
        handle(record, BusinessType.ORDER, "STOCK_RESERVED", Severity.SUCCESS,
                "Đã giữ hàng", e -> "Đơn hàng #" + e.path("orderId").asText() + " đã được giữ hàng, vui lòng thanh toán.");
    }

    @KafkaListener(topics = TOPIC_STOCK_FAILED)
    public void onStockFailed(ConsumerRecord<String, String> record) {
        handle(record, BusinessType.ORDER, "STOCK_FAILED", Severity.ERROR,
                "Hết hàng", e -> "Đơn hàng #" + e.path("orderId").asText() + " thất bại: " + e.path("reason").asText(""));
    }

    @KafkaListener(topics = TOPIC_PAYMENT_SUCCESS)
    public void onPaymentSuccess(ConsumerRecord<String, String> record) {
        handle(record, BusinessType.PAYMENT, "PAYMENT_SUCCESS", Severity.SUCCESS,
                "Thanh toán thành công", e -> "Đơn hàng #" + e.path("orderId").asText() + " đã thanh toán "
                        + e.path("paidAmount").asText(e.path("totalAmount").asText("")) + "đ.");
    }

    @KafkaListener(topics = TOPIC_PAYMENT_FAILED)
    public void onPaymentFailed(ConsumerRecord<String, String> record) {
        handle(record, BusinessType.PAYMENT, "PAYMENT_FAILED", Severity.ERROR,
                "Thanh toán thất bại", e -> "Đơn hàng #" + e.path("orderId").asText() + ": " + e.path("reason").asText(""));
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private void handle(ConsumerRecord<String, String> record, BusinessType business, String type,
                        Severity severity, String title, java.util.function.Function<JsonNode, String> contentFn) {
        try {
            JsonNode event = parse(record.value());
            if (event == null || !event.hasNonNull("userId")) {
                log.warn("[Kafka] {} bỏ qua message thiếu userId: {}", record.topic(), record.value());
                return;
            }
            Long userId = event.get("userId").asLong();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.convertValue(event, LinkedHashMap.class);

            NotificationMessage message = NotificationMessage.builder()
                    .business(business).type(type).severity(severity)
                    .title(title).content(contentFn.apply(event)).data(data)
                    .build();

            // 1. Gửi riêng cho chủ đơn hàng
            dispatcher.sendToUser(userId, message);

            // 2. Đẩy lên dashboard admin
            dispatcher.broadcast(NotificationMessage.builder()
                    .id(message.getId())
                    .business(BusinessType.ADMIN_ORDER).type(type).severity(severity)
                    .title(title).content(message.getContent()).data(data).userId(userId)
                    .build());
        } catch (Exception e) {
            // Thông báo realtime không critical → log và bỏ qua, không block partition
            log.error("[Kafka] Lỗi xử lý {} offset={}: {}", record.topic(), record.offset(), e.getMessage(), e);
        }
    }

    private JsonNode parse(String value) throws Exception {
        if (value == null) {
            return null;
        }
        JsonNode node = objectMapper.readTree(value);
        // JsonSerializer có thể bị double-encode thành string JSON
        return node.isTextual() ? objectMapper.readTree(node.asText()) : node;
    }
}
