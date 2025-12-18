package io.malicki.bankingsystem.kafka.consumer;

import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import io.malicki.bankingsystem.kafka.errorhandling.ErrorHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class NotificationConsumer {

    private final ErrorHandler errorHandler;
    private final Map<Long, Integer> retryAttempts = new ConcurrentHashMap<>();

    public NotificationConsumer(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    @KafkaListener(
            topics = "transfer-completed",
            groupId = "banking-system",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, TransferEvent> record, Acknowledgment ack) {
        TransferEvent event = record.value();
        int currentAttempt = retryAttempts.getOrDefault(record.offset(), 0);

        log.info("📧 [NOTIFICATION] Processing transfer: {} | Attempt: {}",
                event.getTransferId(),
                currentAttempt + 1);

        try {
            // Send notification (mock implementation)
            sendSuccessNotification(event);

            log.info("✅ [NOTIFICATION] Success notification sent for transfer: {}",
                    event.getTransferId());

            ack.acknowledge();
            retryAttempts.remove(record.offset());

        } catch (Exception e) {
            log.error("❌ [NOTIFICATION] Error sending notification for {}: {}",
                    event.getTransferId(), e.getMessage());

            retryAttempts.put(record.offset(), currentAttempt + 1);

            errorHandler.handleError(
                    record,
                    e,
                    ack,
                    "banking-system",
                    currentAttempt
            );
        }
    }

    private void sendSuccessNotification(TransferEvent event) {
        // Mock notification - in production: send email, SMS, push notification, etc.
        log.info("📨 Sending notification: Transfer {} completed | Amount: {} | From: {} → To: {}",
                event.getTransferId(),
                event.getAmount(),
                event.getFromAccountNumber(),
                event.getToAccountNumber());

        // Simulate potential failure (for testing)
        // Uncomment to test error handling:
        // if (Math.random() < 0.3) {
        //     throw new RuntimeException("Notification service unavailable");
        // }
    }
}