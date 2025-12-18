package io.malicki.bankingsystem.kafka.consumer;

import io.malicki.bankingsystem.kafka.errorhandling.ErrorCategory;
import io.malicki.bankingsystem.kafka.errorhandling.FailedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class DltMonitorConsumer {
    
    // Statistics
    private final Map<String, AtomicLong> dltCountByTopic = new ConcurrentHashMap<>();
    private final Map<ErrorCategory, AtomicLong> dltCountByCategory = new ConcurrentHashMap<>();
    
    @KafkaListener(
        topics = {"transfer-validation-dlt", "transfer-execution-dlt", "transfer-completed-dlt"},
        groupId = "dlt-monitor",
        containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void consumeDlt(FailedMessage failedMessage, Acknowledgment ack) {
        
        log.warn("📮 [DLT MONITOR] Received failed message:");
        log.warn("   Original Topic: {}", failedMessage.getOriginalTopic());
        log.warn("   Original Offset: {}", failedMessage.getOriginalOffset());
        log.warn("   Error Category: {}", failedMessage.getErrorCategory());
        log.warn("   Exception: {}", failedMessage.getExceptionType());
        log.warn("   Message: {}", failedMessage.getExceptionMessage());
        log.warn("   Attempt Count: {}", failedMessage.getAttemptCount());
        log.warn("   Failed At: {}", failedMessage.getFailedAt());
        log.warn("   Retryable: {}", failedMessage.isRetryable());
        log.warn("   Original Value: {}", failedMessage.getOriginalValue());
        
        // Update statistics
        dltCountByTopic.computeIfAbsent(
            failedMessage.getOriginalTopic(), 
            k -> new AtomicLong(0)
        ).incrementAndGet();
        
        dltCountByCategory.computeIfAbsent(
            failedMessage.getErrorCategory(), 
            k -> new AtomicLong(0)
        ).incrementAndGet();
        
        // Log summary
        log.info("📊 [DLT STATS] Total by topic: {}", dltCountByTopic);
        log.info("📊 [DLT STATS] Total by category: {}", dltCountByCategory);
        
        // TODO: In production:
        // - Save to database for analysis
        // - Send alert if threshold exceeded
        // - Auto-retry for technical errors (if system healthy)
        
        ack.acknowledge();
    }
    
    public Map<String, AtomicLong> getDltCountByTopic() {
        return new ConcurrentHashMap<>(dltCountByTopic);
    }
    
    public Map<ErrorCategory, AtomicLong> getDltCountByCategory() {
        return new ConcurrentHashMap<>(dltCountByCategory);
    }
}