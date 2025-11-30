package io.malicki.bankingsystem.kafka.consumer;

import io.malicki.bankingsystem.domain.account.Account;
import io.malicki.bankingsystem.domain.account.AccountRepository;
import io.malicki.bankingsystem.domain.transfer.Transfer;
import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import io.malicki.bankingsystem.domain.transfer.TransferRepository;
import io.malicki.bankingsystem.domain.transfer.TransferStatus;
import io.malicki.bankingsystem.kafka.outbox.OutboxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static io.malicki.bankingsystem.kafka.config.KafkaTopicsConfig.TRANSFER_COMPLETED_TOPIC;
import static io.malicki.bankingsystem.kafka.config.KafkaTopicsConfig.TRANSFER_EXECUTION_TOPIC;

@Service
@Slf4j
public class ExecutionConsumer {
    
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final OutboxService outboxService;
    
    public ExecutionConsumer(
        TransferRepository transferRepository,
        AccountRepository accountRepository,
        OutboxService outboxService
    ) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.outboxService = outboxService;
    }
    
    @KafkaListener(
        topics = TRANSFER_EXECUTION_TOPIC,
        groupId = "banking-system",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void executeTransfer(
        ConsumerRecord<String, TransferEvent> record,
        Acknowledgment ack
    ) {
        TransferEvent event = record.value();
        String transferId = event.getTransferId();
        
        log.info("💰 [EXECUTION] Processing transfer: {} | Partition: {} | Offset: {}", 
                transferId, record.partition(), record.offset());
        
        try {
            // 1. Find transfer
            Transfer transfer = transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found: " + transferId));
            
            // 2. ⭐ IDEMPOTENCY CHECK ⭐
            if (transfer.getStatus() == TransferStatus.COMPLETED) {
                log.info("⚠️  Transfer {} already COMPLETED, re-sending to outbox", transferId);
                
                // Re-save to outbox (idempotent)
                TransferEvent completedEvent = TransferEvent.from(transfer);
                outboxService.saveOutboxEvent(
                    transfer.getTransferId(),
                    "TransferCompleted",
                    "transfer-completed",
                    transfer.getTransferId(),  // routing key
                    completedEvent
                );
                
                ack.acknowledge();
                return;
            }
            
            if (transfer.getStatus() == TransferStatus.FAILED) {
                log.warn("⚠️  Transfer {} already FAILED, skipping", transferId);
                ack.acknowledge();
                return;
            }
            
            // 3. Update status to EXECUTING
            transfer.setStatus(TransferStatus.EXECUTING);
            transferRepository.save(transfer);
            
            // 4. EXECUTE TRANSFER (pessimistic locking)
            executeTransferWithLocking(transfer);
            
            // 5. Mark as COMPLETED
            transfer.setStatus(TransferStatus.COMPLETED);
            transfer.setProcessedAt(Instant.now());
            transferRepository.save(transfer);
            
            // 6. ⭐ SAVE TO OUTBOX (in same transaction!) ⭐
            TransferEvent completedEvent = TransferEvent.from(transfer);
            outboxService.saveOutboxEvent(
                transfer.getTransferId(),
                "TransferCompleted",
                TRANSFER_COMPLETED_TOPIC,
                transfer.getTransferId(),
                completedEvent
            );
            
            log.info("✅ [EXECUTION] Transfer completed + saved to outbox: {} | Amount: {}", 
                    transferId, transfer.getAmount());
            
            // 7. Commit offset (safe now!)
            ack.acknowledge();
        } catch (Exception e) {
            log.error("❌ [EXECUTION] Error: {}", e.getMessage(), e);
            
            // Mark as FAILED
            handleExecutionError(transferId, e, ack);
        }
    }
    
    private void executeTransferWithLocking(Transfer transfer) {
        log.debug("Executing transfer with pessimistic locking: {}", transfer.getTransferId());
        
        // ⭐ PESSIMISTIC LOCK - prevents concurrent modifications ⭐
        Account fromAccount = accountRepository
            .findByAccountNumberWithLock(transfer.getFromAccountNumber())
            .orElseThrow(() -> new RuntimeException(
                "Account not found: " + transfer.getFromAccountNumber()
            ));
        
        Account toAccount = accountRepository
            .findByAccountNumberWithLock(transfer.getToAccountNumber())
            .orElseThrow(() -> new RuntimeException(
                "Account not found: " + transfer.getToAccountNumber()
            ));
        
        log.debug("Accounts locked | From: {} (balance: {}) | To: {} (balance: {})",
                fromAccount.getAccountNumber(), fromAccount.getBalance(),
                toAccount.getAccountNumber(), toAccount.getBalance());
        
        // Execute transfer
        fromAccount.withdraw(transfer.getAmount());
        toAccount.deposit(transfer.getAmount());
        
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
        
        log.debug("✅ Transfer executed | From new balance: {} | To new balance: {}",
                fromAccount.getBalance(), toAccount.getBalance());
    }
    
    @Transactional
    private void handleExecutionError(String transferId, Exception e, Acknowledgment ack) {
        try {
            Transfer transfer = transferRepository.findByTransferId(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found"));
            
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason(e.getMessage());
            transfer.setProcessedAt(Instant.now());
            transferRepository.save(transfer);
            
            log.error("Transfer {} marked as FAILED: {}", transferId, e.getMessage());
            
        } catch (Exception ex) {
            log.error("Failed to mark transfer as FAILED: {}", ex.getMessage());
        } finally {
            ack.acknowledge();  // Don't retry indefinitely
        }
    }
}