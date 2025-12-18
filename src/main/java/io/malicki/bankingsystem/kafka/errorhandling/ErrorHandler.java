package io.malicki.bankingsystem.kafka.errorhandling;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ErrorHandler {
    
    private final DeadLetterTopicService dltService;
    private final ErrorClassifier errorClassifier;
    
    public ErrorHandler(
        DeadLetterTopicService dltService,
        ErrorClassifier errorClassifier
    ) {
        this.dltService = dltService;
        this.errorClassifier = errorClassifier;
    }
    
    public void handleError(
        ConsumerRecord<?, ?> record,
        Exception exception,
        Acknowledgment ack,
        String consumerGroupId,
        int currentAttempt
    ) {
        ErrorCategory category = errorClassifier.classify(exception);
        
        log.error("❌ Error processing message | Topic: {} | Partition: {} | Offset: {} | Attempt: {} | Category: {} | Error: {}", 
                record.topic(), 
                record.partition(), 
                record.offset(),
                currentAttempt + 1,
                category.name(),
                exception.getMessage());
        
        // Check if we should retry
        if (errorClassifier.shouldRetry(exception, currentAttempt)) {
            long retryDelay = errorClassifier.getRetryDelay(exception, currentAttempt);
            
            log.info("🔄 Will retry | Attempt: {}/{} | Delay: {}ms | Category: {}", 
                   currentAttempt + 1, 
                   category.getMaxRetries(),
                   retryDelay,
                   category.name());
            
            // Sleep with exponential backoff
            if (retryDelay > 0) {
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("⚠️ Retry sleep interrupted");
                }
            }
            
            // Don't ACK - let Kafka redeliver
            // Consumer will process again
            return;
        }
        
        // Max retries exceeded OR non-retryable error
        log.warn("⚠️ Sending to DLT | Attempts: {} | Category: {} | Reason: {}", 
               currentAttempt + 1,
               category.name(),
               category.getDescription());
        
        // Send to DLT with category info
        dltService.sendToDeadLetterTopic(
            record, 
            exception, 
            consumerGroupId, 
            currentAttempt + 1,
            category
        );
        
        // ACK to move forward (don't block partition)
        ack.acknowledge();
        
        log.info("✅ Message sent to DLT and acknowledged | Topic: {} | Offset: {} | Partition unblocked", 
               record.topic(), 
               record.offset());
    }
}