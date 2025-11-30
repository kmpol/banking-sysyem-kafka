package io.malicki.bankingsystem.kafka.producer;

import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

import static io.malicki.bankingsystem.kafka.config.KafkaTopicsConfig.*;

@Service
@Slf4j
public class TransferEventProducer {
    
    private final KafkaTemplate<String, TransferEvent> kafkaTemplate;
    
    public TransferEventProducer(KafkaTemplate<String, TransferEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    public void sendToValidation(TransferEvent event) {
        String key = event.getFromAccountNumber();  // Partition by account number!

        log.info("📤 Sending to {} | Key: {} | TransferID: {}", 
                TRANSFER_VALIDATION_TOPIC, key, event.getTransferId());
        
        CompletableFuture<org.springframework.kafka.support.SendResult<String, TransferEvent>> future = 
            kafkaTemplate.send(TRANSFER_VALIDATION_TOPIC, key, event);
        
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("❌ Failed to send to {}: {}", TRANSFER_VALIDATION_TOPIC, ex.getMessage());
            } else {
                log.debug("✅ Sent to {} partition {} offset {}",
                        TRANSFER_VALIDATION_TOPIC,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
    
    public void sendToExecution(TransferEvent event) {
        String key = event.getFromAccountNumber();
        
        log.info("📤 Sending to {} | Key: {} | TransferID: {}", 
                TRANSFER_EXECUTION_TOPIC, key, event.getTransferId());
        
        kafkaTemplate.send(TRANSFER_EXECUTION_TOPIC, key, event);
    }
    
    public void sendToCompleted(TransferEvent event) {
        String key = event.getTransferId();  // Partition by transfer ID
        
        log.info("📤 Sending to {} | Key: {} | TransferID: {}", 
                TRANSFER_COMPLETED_TOPIC, key, event.getTransferId());
        
        kafkaTemplate.send(TRANSFER_COMPLETED_TOPIC, key, event);
    }
}