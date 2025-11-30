package io.malicki.bankingsystem.domain.transfer;

import io.malicki.bankingsystem.api.dto.TransferRequest;
import io.malicki.bankingsystem.kafka.producer.TransferEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class TransferService {
    
    private final TransferRepository transferRepository;
    private final TransferEventProducer eventProducer;
    
    public TransferService(
        TransferRepository transferRepository,
        TransferEventProducer eventProducer
    ) {
        this.transferRepository = transferRepository;
        this.eventProducer = eventProducer;
    }
    
    @Transactional
    public Transfer createTransfer(TransferRequest request) {
        // Generate unique ID (idempotency key)
        String transferId = UUID.randomUUID().toString();
        
        log.info("Creating transf/|er: {} | From: {} → To: {} | Amount: {}",
                transferId,/// ////
                request.getFromAccountNumber(),
                request.getToAccountNumber(),
                request.getAmount());
        
        // Create transfer entity
        Transfer transfer = new Transfer();
        transfer.setTransferId(transferId);
        transfer.setFromAccountNumber(request.getFromAccountNumber());
        transfer.setToAccountNumber(request.getToAccountNumber());
        transfer.setAmount(request.getAmount());
        transfer.setDescription(request.getDescription());
        transfer.setStatus(TransferStatus.PENDING);
        
        // Save to database
        Transfer saved = transferRepository.save(transfer);
        
        log.info("✅ Transfer saved to DB: {}", transferId);
        
        // Send event to Kafka (validation topic)
        TransferEvent event = TransferEvent.from(saved);
        eventProducer.sendToValidation(event);
        
        log.info("✅ Transfer event sent to Kafka: {}", transferId);
        
        return saved;
    }
}