package io.malicki.bankingsystem.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    public static final String TRANSFER_VALIDATION_TOPIC = "transfer-validation";
    public static final String TRANSFER_EXECUTION_TOPIC = "transfer-execution";
    public static final String TRANSFER_COMPLETED_TOPIC = "transfer-completed";
    public static final String TRANSFER_VALIDATION_DLT_TOPIC = "transfer-validation-dlt";
    public static final String TRANSFER_EXECUTION_DLT_TOPIC = "transfer-execution-dlt";

    private static final String RETENTION_30_DAYS = "2592000000";
    
    @Bean
    public NewTopic transferValidationTopic() {
        return TopicBuilder.name(TRANSFER_VALIDATION_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }
    
    @Bean
    public NewTopic transferExecutionTopic() {
        return TopicBuilder.name(TRANSFER_EXECUTION_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }
    
    @Bean
    public NewTopic transferCompletedTopic() {
        return TopicBuilder.name(TRANSFER_COMPLETED_TOPIC)
            .partitions(3)
            .replicas(1)
            .build();
    }
    
    @Bean
    public NewTopic transferValidationDltTopic() {
        return TopicBuilder.name(TRANSFER_VALIDATION_DLT_TOPIC)
            .partitions(3)
            .replicas(1)
            .config("retention.ms", RETENTION_30_DAYS)
            .build();
    }
    
    @Bean
    public NewTopic transferExecutionDltTopic() {
        return TopicBuilder.name(TRANSFER_EXECUTION_DLT_TOPIC)
            .partitions(3)
            .replicas(1)
            .config("retention.ms", RETENTION_30_DAYS)
            .build();
    }
}