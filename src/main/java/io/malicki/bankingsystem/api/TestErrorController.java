package io.malicki.bankingsystem.api;

import io.malicki.bankingsystem.domain.account.Account;
import io.malicki.bankingsystem.domain.account.AccountRepository;
import io.malicki.bankingsystem.domain.transfer.Transfer;
import io.malicki.bankingsystem.domain.transfer.TransferRepository;
import io.malicki.bankingsystem.domain.transfer.TransferStatus;
import io.malicki.bankingsystem.kafka.outbox.OutboxService;
import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/test/errors")
@Slf4j
public class TestErrorController {
    
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final OutboxService outboxService;
    
    public TestErrorController(
        TransferRepository transferRepository,
        AccountRepository accountRepository,
        OutboxService outboxService
    ) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.outboxService = outboxService;
    }
    
    @PostMapping("/account-not-found")
    public Map<String, String> testAccountNotFound() {
        String transferId = UUID.randomUUID().toString();
        
        log.warn("🧪 [TEST] Creating transfer with non-existent account: {}", transferId);
        
        Transfer transfer = new Transfer();
        transfer.setTransferId(transferId);
        transfer.setFromAccountNumber("ACC999");  // ← Does NOT exist!
        transfer.setToAccountNumber("ACC002");
        transfer.setAmount(new BigDecimal("100.00"));
        transfer.setDescription("Test: AccountNotFoundException");
        transfer.setStatus(TransferStatus.PENDING);
        
        transferRepository.save(transfer);
        
        // Send to validation (will fail with AccountNotFoundException)
        TransferEvent event = TransferEvent.from(transfer);
        outboxService.saveOutboxEvent(
            transferId,
            "TransferCreated",
            "transfer-validation",
            "ACC999",
            event
        );
        
        Map<String, String> response = new HashMap<>();
        response.put("scenario", "AccountNotFoundException");
        response.put("transferId", transferId);
        response.put("expectedBehavior", "Should go to DLT immediately (0 retries)");
        response.put("dltTopic", "transfer-validation-dlt");
        
        return response;
    }
    
    @PostMapping("/insufficient-funds")
    public Map<String, String> testInsufficientFunds() {
        String transferId = UUID.randomUUID().toString();
        
        log.warn("🧪 [TEST] Creating transfer with insufficient funds: {}", transferId);
        
        // ACC005 has only 500.00
        Transfer transfer = new Transfer();
        transfer.setTransferId(transferId);
        transfer.setFromAccountNumber("ACC005");
        transfer.setToAccountNumber("ACC002");
        transfer.setAmount(new BigDecimal("1000.00"));  // ← More than balance!
        transfer.setDescription("Test: InsufficientFundsException");
        transfer.setStatus(TransferStatus.PENDING);
        
        transferRepository.save(transfer);
        
        TransferEvent event = TransferEvent.from(transfer);
        outboxService.saveOutboxEvent(
            transferId,
            "TransferCreated",
            "transfer-validation",
            "ACC005",
            event
        );
        
        Map<String, String> response = new HashMap<>();
        response.put("scenario", "InsufficientFundsException");
        response.put("transferId", transferId);
        response.put("expectedBehavior", "Should go to DLT immediately (0 retries)");
        response.put("dltTopic", "transfer-validation-dlt");
        
        return response;
    }
    
    @PostMapping("/same-account")
    public Map<String, String> testSameAccount() {
        String transferId = UUID.randomUUID().toString();
        
        log.warn("🧪 [TEST] Creating transfer to same account: {}", transferId);
        
        Transfer transfer = new Transfer();
        transfer.setTransferId(transferId);
        transfer.setFromAccountNumber("ACC001");
        transfer.setToAccountNumber("ACC001");  // ← Same account!
        transfer.setAmount(new BigDecimal("100.00"));
        transfer.setDescription("Test: Same account validation");
        transfer.setStatus(TransferStatus.PENDING);
        
        transferRepository.save(transfer);
        
        TransferEvent event = TransferEvent.from(transfer);
        outboxService.saveOutboxEvent(
            transferId,
            "TransferCreated",
            "transfer-validation",
            "ACC001",
            event
        );
        
        Map<String, String> response = new HashMap<>();
        response.put("scenario", "InvalidAccountException (same account)");
        response.put("transferId", transferId);
        response.put("expectedBehavior", "Should go to DLT immediately (0 retries)");
        response.put("dltTopic", "transfer-validation-dlt");
        
        return response;
    }
    
    @PostMapping("/negative-amount")
    public Map<String, String> testNegativeAmount() {
        String transferId = UUID.randomUUID().toString();
        
        log.warn("🧪 [TEST] Creating transfer with negative amount: {}", transferId);
        
        Transfer transfer = new Transfer();
        transfer.setTransferId(transferId);
        transfer.setFromAccountNumber("ACC001");
        transfer.setToAccountNumber("ACC002");
        transfer.setAmount(new BigDecimal("-50.00"));  // ← Negative!
        transfer.setDescription("Test: Negative amount");
        transfer.setStatus(TransferStatus.PENDING);
        
        transferRepository.save(transfer);
        
        TransferEvent event = TransferEvent.from(transfer);
        outboxService.saveOutboxEvent(
            transferId,
            "TransferCreated",
            "transfer-validation",
            "ACC001",
            event
        );
        
        Map<String, String> response = new HashMap<>();
        response.put("scenario", "IllegalArgumentException (negative amount)");
        response.put("transferId", transferId);
        response.put("expectedBehavior", "Should go to DLT immediately (0 retries)");
        response.put("dltTopic", "transfer-validation-dlt");
        
        return response;
    }
    
    @GetMapping("/check-balances")
    public Map<String, Object> checkBalances() {
        Map<String, Object> balances = new HashMap<>();
        
        accountRepository.findAll().forEach(account -> {
            Map<String, Object> accountInfo = new HashMap<>();
            accountInfo.put("ownerName", account.getOwnerName());
            accountInfo.put("balance", account.getBalance());
            accountInfo.put("active", account.isActive());
            balances.put(account.getAccountNumber(), accountInfo);
        });
        
        return balances;
    }
}