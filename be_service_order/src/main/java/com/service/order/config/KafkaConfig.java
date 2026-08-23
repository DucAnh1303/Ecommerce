package com.service.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Cấu hình Kafka cho Order Service.
 *
 * Topics:
 *  - order.created        : Order Service → Product Service (reserve stock)
 *  - order.stock.reserved : Product Service → Order Service (stock OK)
 *  - order.stock.failed   : Product Service → Order Service (hết hàng)
 *  - order.payment.success: Order Service → Order Service (thanh toán OK)
 *  - order.payment.failed : Order Service → Product Service + Order Service (rollback)
 *  - order.completed      : Order Service → Notification (đơn hoàn tất)
 *  - order.dlq            : Dead Letter Queue (message fail 3 lần)
 *
 * Consumer:
 *  - concurrency = 3: mỗi instance xử lý 3 partition song song
 *  - maxPollRecords = 50: batch xử lý 50 message/poll
 *  - FixedBackOff (3 lần retry, cách 2s): trước khi đẩy vào DLQ
 */
@Configuration
public class KafkaConfig {

    // ─── Topic names ──────────────────────────────────────────────────────────
    public static final String TOPIC_ORDER_CREATED          = "order.created";
    public static final String TOPIC_STOCK_RESERVED         = "order.stock.reserved";
    public static final String TOPIC_STOCK_FAILED           = "order.stock.failed";
    public static final String TOPIC_PAYMENT_SUCCESS        = "order.payment.success";
    public static final String TOPIC_PAYMENT_FAILED         = "order.payment.failed";
    public static final String TOPIC_ORDER_COMPLETED        = "order.completed";
    public static final String TOPIC_DLQ                    = "order.dlq";

    public static final String GROUP_ORDER_SERVICE          = "order-service-group";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ─── Topic Beans (auto-create với 3 partitions) ───────────────────────────

    @Bean public NewTopic topicOrderCreated()      { return buildTopic(TOPIC_ORDER_CREATED); }
    @Bean public NewTopic topicStockReserved()     { return buildTopic(TOPIC_STOCK_RESERVED); }
    @Bean public NewTopic topicStockFailed()       { return buildTopic(TOPIC_STOCK_FAILED); }
    @Bean public NewTopic topicPaymentSuccess()    { return buildTopic(TOPIC_PAYMENT_SUCCESS); }
    @Bean public NewTopic topicPaymentFailed()     { return buildTopic(TOPIC_PAYMENT_FAILED); }
    @Bean public NewTopic topicOrderCompleted()    { return buildTopic(TOPIC_ORDER_COMPLETED); }
    @Bean public NewTopic topicDlq()               { return buildTopic(TOPIC_DLQ); }

    private NewTopic buildTopic(String name) {
        return TopicBuilder.name(name)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ─── Producer ─────────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        // Đảm bảo không mất message khi broker fail
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ─── Consumer ─────────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ORDER_SERVICE);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);        // batch 50 records/poll
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);   // manual ack để đảm bảo at-least-once
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "java.util.Map");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            KafkaTemplate<String, Object> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // 3 thread/instance = xử lý song song 3 partition
        factory.setConcurrency(3);
        // Manual ack
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // ─── Error Handler + DLQ ─────────────────────────────────────────────
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(TOPIC_DLQ, 0));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer,
                new FixedBackOff(2000L, 3L));
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    @Bean
    public com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}
