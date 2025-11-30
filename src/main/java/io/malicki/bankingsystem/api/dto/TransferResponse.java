package io.malicki.bankingsystem.api.dto;

import io.malicki.bankingsystem.domain.transfer.TransferStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferResponse {
    
    private String transferId;
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private String description;
    private TransferStatus status;
    private Instant createdAt;
    
    public static TransferResponse from(io.malicki.bankingsystem.domain.transfer.Transfer transfer) {
        return new TransferResponse(
            transfer.getTransferId(),
            transfer.getFromAccountNumber(),
            transfer.getToAccountNumber(),
            transfer.getAmount(),
            transfer.getDescription(),
            transfer.getStatus(),
            transfer.getCreatedAt()
        );
    }
}