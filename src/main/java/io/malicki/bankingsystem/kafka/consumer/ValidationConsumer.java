package io.malicki.bankingsystem.kafka.consumer;

import io.malicki.bankingsystem.domain.account.Account;
import io.malicki.bankingsystem.domain.account.AccountRepository;
import io.malicki.bankingsystem.domain.transfer.Transfer;
import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import io.malicki.bankingsystem.domain.transfer.TransferRepository;
import io.malicki.bankingsystem.domain.transfer.TransferStatus;
import io.malicki.bankingsystem.exception.AccountNotFoundException;
import io.malicki.bankingsystem.exception.InsufficientFundsException;
import io.malicki.bankingsystem.exception.InvalidAccountException;
import io.malicki.bankingsystem.kafka.errorhandling.ErrorHandler;
import io.malicki.bankingsystem.kafka.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ValidationConsumer {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final OutboxService outboxService;
    private final ErrorHandler errorHandler;

    // Track retry attempts per offset
    private final Map<Long, Integer> retryAttempts = new ConcurrentHashMap<>();

    public ValidationConsumer(
            TransferRepository transferRepository,
            AccountRepository accountRepository,
            OutboxService outboxService,
            ErrorHandler errorHandler
    ) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.outboxService = outboxService;
        this.errorHandler = errorHandler;
    }

    @KafkaListener(
            topics = "transfer-validation",
            groupId = "banking-system",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(ConsumerRecord<String, TransferEvent> record, Acknowledgment ack) {
        TransferEvent event = record.value();
        String transferId = event.getTransferId();

        // Get current attempt count for this offset
        int currentAttempt = retryAttempts.getOrDefault(record.offset(), 0);

        log.info("🔍 [VALIDATION] Processing transfer: {} | Partition: {} | Offset: {} | Attempt: {}",
                transferId,
                record.partition(),
                record.offset(),
                currentAttempt + 1);

        try {
            // Find transfer
            Transfer transfer = transferRepository.findByTransferId(transferId)
                    .orElseThrow(() -> new RuntimeException("Transfer not found: " + transferId));

            // Idempotency check
            if (transfer.getStatus() == TransferStatus.VALIDATED ||
                    transfer.getStatus() == TransferStatus.EXECUTING ||
                    transfer.getStatus() == TransferStatus.COMPLETED) {

                log.info("⚠️  Transfer {} already {}, re-sending to outbox",
                        transferId, transfer.getStatus());

                TransferEvent validatedEvent = TransferEvent.from(transfer);
                outboxService.saveOutboxEvent(
                        transfer.getTransferId(),
                        "TransferValidated",
                        "transfer-execution",
                        transfer.getFromAccountNumber(),
                        validatedEvent
                );

                ack.acknowledge();
                retryAttempts.remove(record.offset()); // Clean up
                return;
            }

            // Update status
            transfer.setStatus(TransferStatus.VALIDATING);
            transferRepository.save(transfer);

            // Validate business rules
            log.debug("Validating business rules for transfer: {}", transferId);
            validateBusinessRules(transfer);

            // Mark as validated
            transfer.setStatus(TransferStatus.VALIDATED);
            transferRepository.save(transfer);

            // Save to outbox
            TransferEvent validatedEvent = TransferEvent.from(transfer);
            outboxService.saveOutboxEvent(
                    transfer.getTransferId(),
                    "TransferValidated",
                    "transfer-execution",
                    transfer.getFromAccountNumber(),
                    validatedEvent
            );

            log.info("✅ [VALIDATION] Transfer validated + saved to outbox: {}", transferId);

            // Acknowledge and clean up retry tracking
            ack.acknowledge();
            retryAttempts.remove(record.offset());

        } catch (Exception e) {
            log.error("❌ [VALIDATION] Error processing transfer {}: {}",
                    transferId, e.getMessage());

            // Increment retry attempt
            retryAttempts.put(record.offset(), currentAttempt + 1);

            // Delegate to ErrorHandler
            errorHandler.handleError(
                    record,
                    e,
                    ack,
                    "banking-system",
                    currentAttempt
            );
        }
    }

    private void validateBusinessRules(Transfer transfer) {
        // 1. Check if accounts exist
        Account fromAccount = accountRepository.findByAccountNumber(transfer.getFromAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(transfer.getFromAccountNumber()));

        Account toAccount = accountRepository.findByAccountNumber(transfer.getToAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(transfer.getToAccountNumber()));

        // 2. Check if accounts are active
        if (!fromAccount.isActive()) {
            throw new InvalidAccountException(
                    fromAccount.getAccountNumber(),
                    "Account is not active"
            );
        }

        if (!toAccount.isActive()) {
            throw new InvalidAccountException(
                    toAccount.getAccountNumber(),
                    "Account is not active"
            );
        }

        // 3. Check if sufficient funds
        if (fromAccount.getBalance().compareTo(transfer.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    fromAccount.getAccountNumber(),
                    fromAccount.getBalance(),
                    transfer.getAmount()
            );
        }

        // 4. Check if not same account
        if (fromAccount.getAccountNumber().equals(toAccount.getAccountNumber())) {
            throw new InvalidAccountException(
                    fromAccount.getAccountNumber(),
                    "Cannot transfer to the same account"
            );
        }

        // 5. Check if amount is positive
        if (transfer.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Transfer amount must be positive: " + transfer.getAmount()
            );
        }

        log.debug("✅ Business rules validated for transfer: {}", transfer.getTransferId());
    }
}