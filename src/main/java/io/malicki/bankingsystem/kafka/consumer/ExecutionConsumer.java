package io.malicki.bankingsystem.kafka.consumer;

import io.malicki.bankingsystem.domain.account.Account;
import io.malicki.bankingsystem.domain.account.AccountRepository;
import io.malicki.bankingsystem.domain.transfer.Transfer;
import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import io.malicki.bankingsystem.domain.transfer.TransferRepository;
import io.malicki.bankingsystem.domain.transfer.TransferStatus;
import io.malicki.bankingsystem.exception.AccountNotFoundException;
import io.malicki.bankingsystem.kafka.errorhandling.ErrorHandler;
import io.malicki.bankingsystem.kafka.outbox.OutboxService;
import jakarta.persistence.LockModeType;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ExecutionConsumer {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final OutboxService outboxService;
    private final ErrorHandler errorHandler;

    // Track retry attempts per offset
    private final Map<Long, Integer> retryAttempts = new ConcurrentHashMap<>();

    public ExecutionConsumer(
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
            topics = "transfer-execution",
            groupId = "banking-system",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(ConsumerRecord<String, TransferEvent> record, Acknowledgment ack) {
        TransferEvent event = record.value();
        String transferId = event.getTransferId();

        // Get current attempt count
        int currentAttempt = retryAttempts.getOrDefault(record.offset(), 0);

        log.info("💰 [EXECUTION] Processing transfer: {} | Partition: {} | Offset: {} | Attempt: {}",
                transferId,
                record.partition(),
                record.offset(),
                currentAttempt + 1);

        try {
            // Find transfer
            Transfer transfer = transferRepository.findByTransferId(transferId)
                    .orElseThrow(() -> new RuntimeException("Transfer not found: " + transferId));

            // Idempotency check
            if (transfer.getStatus() == TransferStatus.COMPLETED) {
                log.info("⚠️  Transfer {} already COMPLETED, re-sending to outbox", transferId);

                TransferEvent completedEvent = TransferEvent.from(transfer);
                outboxService.saveOutboxEvent(
                        transfer.getTransferId(),
                        "TransferCompleted",
                        "transfer-completed",
                        transfer.getFromAccountNumber(),
                        completedEvent
                );

                ack.acknowledge();
                retryAttempts.remove(record.offset());
                return;
            }

            // Update status
            transfer.setStatus(TransferStatus.EXECUTING);
            transferRepository.save(transfer);

            // Execute transfer with pessimistic locking
            executeTransfer(transfer);

            // Mark as completed
            transfer.setStatus(TransferStatus.COMPLETED);
            transfer.setProcessedAt(Instant.now());
            transferRepository.save(transfer);

            // Save to outbox
            TransferEvent completedEvent = TransferEvent.from(transfer);
            outboxService.saveOutboxEvent(
                    transfer.getTransferId(),
                    "TransferCompleted",
                    "transfer-completed",
                    transfer.getFromAccountNumber(),
                    completedEvent
            );

            log.info("✅ [EXECUTION] Transfer completed + saved to outbox: {}", transferId);

            // Acknowledge and clean up
            ack.acknowledge();
            retryAttempts.remove(record.offset());

        } catch (Exception e) {
            log.error("❌ [EXECUTION] Error processing transfer {}: {}",
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

    private void executeTransfer(Transfer transfer) {
        log.debug("Executing transfer: {}", transfer.getTransferId());

        // Lock accounts (pessimistic locking to prevent concurrent modifications)
        Account fromAccount = accountRepository.findByAccountNumberWithLock(transfer.getFromAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(transfer.getFromAccountNumber()));

        Account toAccount = accountRepository.findByAccountNumberWithLock(transfer.getToAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(transfer.getToAccountNumber()));

        // Perform transfer
        fromAccount.withdraw(transfer.getAmount());
        toAccount.deposit(transfer.getAmount());

        // Save updated balances
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        log.debug("✅ Transfer executed: {} | From: {} ({}) → To: {} ({})",
                transfer.getTransferId(),
                fromAccount.getAccountNumber(),
                fromAccount.getBalance(),
                toAccount.getAccountNumber(),
                toAccount.getBalance());
    }
}