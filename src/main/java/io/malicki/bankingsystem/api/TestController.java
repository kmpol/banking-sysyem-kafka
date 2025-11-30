package io.malicki.bankingsystem.api;

import io.malicki.bankingsystem.domain.transfer.TransferEvent;
import io.malicki.bankingsystem.domain.transfer.Transfer;
import io.malicki.bankingsystem.domain.transfer.TransferRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@Slf4j
public class TestController {
    
    private final TransferRepository transferRepository;
    private final KafkaTemplate<String, TransferEvent> kafkaTemplate;
    
    public TestController(
        TransferRepository transferRepository,
        KafkaTemplate<String, TransferEvent> kafkaTemplate
    ) {
        this.transferRepository = transferRepository;
        this.kafkaTemplate = kafkaTemplate;
    }
    
    @PostMapping("/resend-to-execution/{transferId}")
    public Map<String, String> resendToExecution(@PathVariable String transferId) {
        Transfer transfer = transferRepository.findByTransferId(transferId)
            .orElseThrow(() -> new RuntimeException("Transfer not found: " + transferId));
        
        TransferEvent event = TransferEvent.from(transfer);
        
        log.warn("🔄 [TEST] Manually resending event to transfer-execution: {}", transferId);
        
        kafkaTemplate.send(
            "transfer-execution",
            transfer.getFromAccountNumber(),
            event
        );
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Event resent to transfer-execution");
        response.put("transferId", transferId);
        response.put("currentStatus", transfer.getStatus().toString());
        
        return response;
    }
    
    @PostMapping("/resend-to-validation/{transferId}")
    public Map<String, String> resendToValidation(@PathVariable String transferId) {
        Transfer transfer = transferRepository.findByTransferId(transferId)
            .orElseThrow(() -> new RuntimeException("Transfer not found: " + transferId));
        
        TransferEvent event = TransferEvent.from(transfer);
        
        log.warn("🔄 [TEST] Manually resending event to transfer-validation: {}", transferId);
        
        kafkaTemplate.send(
            "transfer-validation",
            transfer.getFromAccountNumber(),
            event
        );
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Event resent to transfer-validation");
        response.put("transferId", transferId);
        response.put("currentStatus", transfer.getStatus().toString());
        
        return response;
    }
}