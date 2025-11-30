package io.malicki.bankingsystem.kafka.consumer;

import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import io.malicki.bankingsystem.domain.transfer.TransferStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationConsumer {
    
    @KafkaListener(
        topics = "transfer-completed",
        groupId = "banking-system",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void sendNotification(
        ConsumerRecord<String, TransferEvent> record,
        Acknowledgment ack
    ) {
        TransferEvent event = record.value();
        
        log.info("📧 [NOTIFICATION] Sending notification for transfer: {} | Status: {}", 
                event.getTransferId(), event.getStatus());
        
        try {
            if (event.getStatus() == TransferStatus.COMPLETED) {
                sendSuccessNotification(event);
            } else if (event.getStatus() == TransferStatus.FAILED) {
                sendFailureNotification(event);
            }
            
            ack.acknowledge();
            
        } catch (Exception e) {
            log.error("❌ [NOTIFICATION] Error: {}", e.getMessage(), e);
            // Don't retry notifications - just log and continue
            ack.acknowledge();
        }
    }
    
    private void sendSuccessNotification(TransferEvent event) {
        // TODO: Real implementation (Email, SMS, Push notification)
        log.info("✅ SUCCESS notification sent for transfer: {}", event.getTransferId());
        log.info("   From: {} → To: {} | Amount: {}", 
                event.getFromAccountNumber(),
                event.getToAccountNumber(),
                event.getAmount());
    }
    
    private void sendFailureNotification(TransferEvent event) {
        // TODO: Real implementation
        log.warn("❌ FAILURE notification sent for transfer: {}", event.getTransferId());
    }
}