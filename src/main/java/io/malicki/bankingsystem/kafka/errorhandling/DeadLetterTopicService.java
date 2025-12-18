package io.malicki.bankingsystem.kafka.errorhandling;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class DeadLetterTopicService {
    
    private final KafkaTemplate<String, FailedMessage> dltKafkaTemplate;
    private final ObjectMapper objectMapper;
    private final AtomicLong dltMessageCount = new AtomicLong(0);
    
    public DeadLetterTopicService(
        KafkaTemplate<String, FailedMessage> dltKafkaTemplate,
        ObjectMapper objectMapper
    ) {
        this.dltKafkaTemplate = dltKafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    public void sendToDeadLetterTopic(
        ConsumerRecord<?, ?> record,
        Exception exception,
        String consumerGroupId,
        int attemptCount,
        ErrorCategory category
    ) {
        String dltTopicName = record.topic() + "-dlt";
        
        try {
            FailedMessage failedMessage = buildFailedMessage(
                record, 
                exception, 
                consumerGroupId, 
                attemptCount,
                category
            );
            
            dltKafkaTemplate.send(dltTopicName, failedMessage.getOriginalKey(), failedMessage)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("❌ Failed to send message to DLT {}: {}", 
                                dltTopicName, ex.getMessage(), ex);
                    } else {
                        long count = dltMessageCount.incrementAndGet();
                        log.warn("📮 Message sent to DLT: {} | Original Topic: {} | Offset: {} | Category: {} | Total DLT: {}", 
                               dltTopicName, 
                               record.topic(), 
                               record.offset(),
                               category.name(),
                               count);
                    }
                });
            
        } catch (Exception e) {
            log.error("💥 CRITICAL: Failed to send to DLT! Message lost: {}", 
                    record, e);
        }
    }
    
    private FailedMessage buildFailedMessage(
        ConsumerRecord<?, ?> record,
        Exception exception,
        String consumerGroupId,
        int attemptCount,
        ErrorCategory category
    ) {
        FailedMessage failedMessage = new FailedMessage();
        
        // Original message details
        failedMessage.setOriginalTopic(record.topic());
        failedMessage.setOriginalPartition(record.partition());
        failedMessage.setOriginalOffset(record.offset());
        failedMessage.setOriginalKey(record.key() != null ? record.key().toString() : null);
        
        // Serialize value to JSON string
        try {
            failedMessage.setOriginalValue(objectMapper.writeValueAsString(record.value()));
        } catch (Exception e) {
            failedMessage.setOriginalValue(record.value() != null ? record.value().toString() : null);
        }
        
        // Error details
        failedMessage.setExceptionType(exception.getClass().getName());
        failedMessage.setExceptionMessage(exception.getMessage());
        failedMessage.setStackTrace(getStackTraceAsString(exception));
        failedMessage.setAttemptCount(attemptCount);
        
        // Metadata
        failedMessage.setFailedAt(Instant.now());
        failedMessage.setConsumerGroupId(consumerGroupId);
        
        // Extract Kafka headers
        Map<String, String> headers = new HashMap<>();
        record.headers().forEach(header -> 
            headers.put(header.key(), new String(header.value()))
        );
        failedMessage.setHeaders(headers);
        
        // Error category
        failedMessage.setErrorCategory(category);
        failedMessage.setRetryable(category.isAutoRetryFromDlt());
        
        return failedMessage;
    }
    
    private String getStackTraceAsString(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
    
    public long getDltMessageCount() {
        return dltMessageCount.get();
    }
}