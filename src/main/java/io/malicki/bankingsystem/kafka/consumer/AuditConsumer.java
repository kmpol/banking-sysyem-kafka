package io.malicki.bankingsystem.kafka.consumer;

import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AuditConsumer {
    
    @KafkaListener(
        topics = {
            "transfer-validation",
            "transfer-execution",
            "transfer-completed"
        },
        groupId = "audit-group",  // Different group
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void auditEvent(ConsumerRecord<String, TransferEvent> record) {
        TransferEvent event = record.value();
        
        log.info("📋 [AUDIT] Topic: {} | TransferID: {} | Status: {} | Partition: {} | Offset: {}",
                record.topic(),
                event.getTransferId(),
                event.getStatus(),
                record.partition(),
                record.offset());
        
        // TODO: Save to audit database/log file
        // This consumer has its OWN offsets independent of banking-system group.
    }
}