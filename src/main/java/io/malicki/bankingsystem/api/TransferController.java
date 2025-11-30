package io.malicki.bankingsystem.api;

import io.malicki.bankingsystem.api.dto.TransferRequest;
import io.malicki.bankingsystem.api.dto.TransferResponse;
import io.malicki.bankingsystem.domain.transfer.Transfer;
import io.malicki.bankingsystem.domain.transfer.TransferService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@Slf4j
public class TransferController {
    
    private final TransferService transferService;
    
    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }
    
    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
        @Valid @RequestBody TransferRequest request
    ) {
        log.info("🏦 POST /api/transfers | From: {} → To: {} | Amount: {}", 
                request.getFromAccountNumber(),
                request.getToAccountNumber(),
                request.getAmount());
        
        Transfer transfer = transferService.createTransfer(request);
        
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(TransferResponse.from(transfer));
    }
}