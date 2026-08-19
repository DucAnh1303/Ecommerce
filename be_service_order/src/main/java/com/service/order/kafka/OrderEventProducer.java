package com.service.order.kafka;

import com.service.order.config.KafkaConfig;
import com.service.order.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Producer – publish các Saga events.
 *
 * Key của message = orderId.toString() → đảm bảo cùng orderId luôn đi vào
 * cùng 1 partition → đảm bảo thứ tự message trong 1 đơn hàng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * [STEP 1] Order được tạo → yêu cầu Product Service reserve stock
     */
    public void publishOrderCreated(OrderCreatedEvent event) {
        publish(KafkaConfig.TOPIC_ORDER_CREATED, event.getOrderId().toString(), event);
    }

    /**
     * [STEP 3a] Payment thành công → cập nhật Order COMPLETED
     */
    public void publishPaymentSuccess(PaymentSuccessEvent event) {
        publish(KafkaConfig.TOPIC_PAYMENT_SUCCESS, event.getOrderId().toString(), event);
    }

    /**
     * [STEP 3b] Payment thất bại → yêu cầu Product Service rollback stock
     */
    public void publishPaymentFailed(PaymentFailedEvent event) {
        publish(KafkaConfig.TOPIC_PAYMENT_FAILED, event.getOrderId().toString(), event);
    }

    // ─── Internal helper ─────────────────────────────────────────────────────

    private void publish(String topic, String key, Object payload) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, payload);
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("[Kafka] Published to '{}' | key={} | offset={}",
                        topic, key, result.getRecordMetadata().offset());
            } else {
                log.error("[Kafka] FAILED to publish to '{}' | key={} | error={}",
                        topic, key, ex.getMessage(), ex);
            }
        });
    }
}
