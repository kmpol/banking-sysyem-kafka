package io.malicki.bankingsystem.kafka.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.malicki.bankingsystem.domain.outbox.OutboxEvent;
import io.malicki.bankingsystem.domain.outbox.OutboxRepository;
import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class OutboxProcessor {
    
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, TransferEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public OutboxProcessor(
        OutboxRepository outboxRepository,
        KafkaTemplate<String, TransferEvent> kafkaTemplate,
        ObjectMapper objectMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Scheduled(fixedDelay = 3000)  // every 3 sec
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> pendingEvents = outboxRepository
            .findTop100ByProcessedFalseOrderByCreatedAtAsc();
        
        if (pendingEvents.isEmpty()) {
            return;
        }
        
        log.debug("📤 Processing {} outbox events", pendingEvents.size());
        
        for (OutboxEvent event : pendingEvents) {
            try {
                // Deserialize payload
                TransferEvent transferEvent = objectMapper.readValue(
                    event.getPayload(), 
                    TransferEvent.class
                );
                
                // Send to Kafka (synchronous - wait for confirmation)
                kafkaTemplate.send(
                    event.getDestinationTopic(),
                    event.getRoutingKey(),
                    transferEvent
                ).get();  // Blocks until Kafka confirms
                
                // Mark as processed
                event.setProcessed(true);
                event.setProcessedAt(Instant.now());
                outboxRepository.save(event);
                
                log.debug("✅ Outbox event sent: {} → {} (retry: {})", 
                        event.getAggregateId(), 
                        event.getDestinationTopic(),
                        event.getRetryCount());
                
            } catch (Exception e) {
                log.error("❌ Failed to process outbox event {}: {}", 
                        event.getId(), e.getMessage());
                
                // Increment retry count
                event.setRetryCount(event.getRetryCount() + 1);
                outboxRepository.save(event);
                
            }
        }
    }
    
    // Monitor pending events
    @Scheduled(fixedDelay = 60000)  // every minute
    public void monitorOutbox() {
        long pending = outboxRepository.countByProcessedFalse();
        
        if (pending > 0) {
            log.info("📊 Outbox status: {} pending events", pending);
        }
        
        if (pending > 100) {
            log.warn("⚠️  HIGH OUTBOX LAG: {} pending events!", pending);
            // TODO: Send alert (PagerDuty, Slack, etc.)
        }
    }
}