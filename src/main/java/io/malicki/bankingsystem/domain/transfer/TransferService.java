package io.malicki.bankingsystem.domain.transfer;

import io.malicki.bankingsystem.api.dto.TransferRequest;
import io.malicki.bankingsystem.kafka.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static io.malicki.bankingsystem.kafka.config.KafkaTopicsConfig.TRANSFER_VALIDATION_TOPIC;

@Service
@Slf4j
public class TransferService {

    private final TransferRepository transferRepository;
    private final OutboxService outboxService;

    public TransferService(
            TransferRepository transferRepository,
            OutboxService outboxService
    ) {
        this.transferRepository = transferRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public Transfer createTransfer(TransferRequest request) {
        // Generate unique ID (idempotency key)
        String transferId = UUID.randomUUID().toString();

        log.info("Creating transfer: {} | From: {} → To: {} | Amount: {}",
                transferId,
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

        // ⭐ Save to OUTBOX (in same transaction!) ⭐
        TransferEvent event = TransferEvent.from(saved);
        outboxService.saveOutboxEvent(
                saved.getTransferId(),
                "TransferCreated",
                TRANSFER_VALIDATION_TOPIC,
                saved.getFromAccountNumber(),
                event
        );

        log.info("✅ Transfer event saved to outbox: {}", transferId);
        log.info("📋 Event will be sent to Kafka by OutboxProcessor");

        // OutboxProcessor will send it to Kafka within 3 seconds
        return saved;
    }
}