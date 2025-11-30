package io.malicki.bankingsystem.kafka.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.malicki.bankingsystem.domain.outbox.OutboxEvent;
import io.malicki.bankingsystem.domain.outbox.OutboxRepository;
import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class OutboxService {
    
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    public OutboxService(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }
    
    @Transactional
    public void saveOutboxEvent(
        String aggregateId,
        String eventType,
        String destinationTopic,
        String routingKey,
        TransferEvent event
    ) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setEventType(eventType);
            outboxEvent.setDestinationTopic(destinationTopic);
            outboxEvent.setRoutingKey(routingKey);
            outboxEvent.setPayload(payload);
            outboxEvent.setProcessed(false);
            
            outboxRepository.save(outboxEvent);
            
            log.debug("📝 Saved outbox event: {} → {}", aggregateId, destinationTopic);
            
        } catch (Exception e) {
            log.error("Failed to save outbox event: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }
}