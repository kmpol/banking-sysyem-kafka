package io.malicki.bankingsystem.kafka.consumer;

import io.malicki.bankingsystem.domain.account.Account;
import io.malicki.bankingsystem.domain.account.AccountRepository;
import io.malicki.bankingsystem.domain.transfer.Transfer;
import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import io.malicki.bankingsystem.domain.transfer.TransferRepository;
import io.malicki.bankingsystem.domain.transfer.TransferStatus;
import io.malicki.bankingsystem.exception.AccountNotFoundException;
import io.malicki.bankingsystem.exception.InsufficientFundsException;
import io.malicki.bankingsystem.kafka.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.malicki.bankingsystem.kafka.config.KafkaTopicsConfig.TRANSFER_EXECUTION_TOPIC;
import static io.malicki.bankingsystem.kafka.config.KafkaTopicsConfig.TRANSFER_VALIDATION_TOPIC;

@Service
@Slf4j
public class ValidationConsumer {
    
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final OutboxService outboxService;
    
    public ValidationConsumer(
        TransferRepository transferRepository,
        AccountRepository accountRepository,
        OutboxService outboxService
    ) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.outboxService = outboxService;
    }
    
    @KafkaListener(
        topics = TRANSFER_VALIDATION_TOPIC,
        groupId = "banking-system",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void validateTransfer(
        ConsumerRecord<String, TransferEvent> record,
        Acknowledgment ack
    ) {
        TransferEvent event = record.value();
        String transferId = event.getTransferId();
        
        log.info("🔍 [VALIDATION] Processing transfer: {} | Partition: {} | Offset: {}", 
                transferId, record.partition(), record.offset());
        
        try {
            // 1. Find transfer in DB
            Transfer transfer = transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found: " + transferId));
            
            // 2. Idempotency check
            if (transfer.getStatus() == TransferStatus.VALIDATED) {
                log.info("⚠️  Transfer {} already VALIDATED, re-sending to outbox", transferId);
                
                // Re-save to outbox (idempotent - will be sent again)
                TransferEvent validatedEvent = TransferEvent.from(transfer);
                outboxService.saveOutboxEvent(
                    transfer.getTransferId(),
                    "TransferValidated",
                    "transfer-execution",
                    transfer.getFromAccountNumber(),
                    validatedEvent
                );
                
                ack.acknowledge();
                return;
            }
            
            if (transfer.getStatus() == TransferStatus.COMPLETED || 
                transfer.getStatus() == TransferStatus.FAILED) {
                log.info("⚠️  Transfer {} already {}, skipping", transferId, transfer.getStatus());
                ack.acknowledge();
                return;
            }
            
            // 3. Update status to VALIDATING
            transfer.setStatus(TransferStatus.VALIDATING);
            transferRepository.save(transfer);
            
            // 4. BUSINESS VALIDATIONS
            validateBusinessRules(transfer);
            
            // 5. Mark as VALIDATED
            transfer.setStatus(TransferStatus.VALIDATED);
            transferRepository.save(transfer);
            
            // 6. SAVE TO OUTBOX (same transaction)
            TransferEvent validatedEvent = TransferEvent.from(transfer);
            outboxService.saveOutboxEvent(
                transfer.getTransferId(),
                "TransferValidated",
                TRANSFER_EXECUTION_TOPIC,
                transfer.getFromAccountNumber(),  // routing key
                validatedEvent
            );
            
            log.info("✅ [VALIDATION] Transfer validated + saved to outbox: {}", transferId);
            
            // 7. Commit offset (safe now - event in outbox!)
            ack.acknowledge();
        } catch (AccountNotFoundException | InsufficientFundsException e) {
            // Business error - don't retry, mark as FAILED
            log.error("❌ [VALIDATION] Business error: {}", e.getMessage());
            handleBusinessError(transferId, e, ack);
            
        } catch (Exception e) {
            // Technical error - don't ack, will retry
            log.error("❌ [VALIDATION] Technical error: {}", e.getMessage(), e);
            // Don't ack - Kafka will retry
            // But if retries exceeded, should go to DLT (configure in error handler)
        }
    }
    
    private void validateBusinessRules(Transfer transfer) {
        log.debug("Validating business rules for transfer: {}", transfer.getTransferId());
        
        // 1. Check FROM account exists and is active
        Account fromAccount = accountRepository.findByAccountNumber(transfer.getFromAccountNumber())
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + transfer.getFromAccountNumber()
            ));
        
        if (!fromAccount.isActive()) {
            throw new IllegalStateException("Account is not active: " + fromAccount.getAccountNumber());
        }
        
        // 2. Check TO account exists and is active
        Account toAccount = accountRepository.findByAccountNumber(transfer.getToAccountNumber())
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + transfer.getToAccountNumber()
            ));
        
        if (!toAccount.isActive()) {
            throw new IllegalStateException("Account is not active: " + toAccount.getAccountNumber());
        }
        
        // 3. Check same account
        if (fromAccount.getAccountNumber().equals(toAccount.getAccountNumber())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        
        // 4. Check sufficient funds
        if (fromAccount.getBalance().compareTo(transfer.getAmount()) < 0) {
            throw new InsufficientFundsException(
                "Insufficient funds in account " + fromAccount.getAccountNumber() +
                ". Available: " + fromAccount.getBalance() +
                ", Required: " + transfer.getAmount()
            );
        }
        
        log.debug("✅ Business rules validated successfully");
    }
    
    @Transactional
    private void handleBusinessError(String transferId, Exception e, Acknowledgment ack) {
        try {
            Transfer transfer = transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found"));
            
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason(e.getMessage());
            transferRepository.save(transfer);
            
            log.warn("Transfer {} marked as FAILED: {}", transferId, e.getMessage());
            
        } catch (Exception ex) {
            log.error("Failed to handle business error: {}", ex.getMessage());
        } finally {
            ack.acknowledge();  // Always commit - don't retry business errors
        }
    }
}