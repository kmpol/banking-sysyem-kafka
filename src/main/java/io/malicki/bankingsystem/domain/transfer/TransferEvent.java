package io.malicki.bankingsystem.domain.transfer;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferEvent implements Serializable {
    
    private String transferId;
    private String fromAccountNumber;
    private String toAccountNumber;
    private BigDecimal amount;
    private String description;
    private TransferStatus status;
    private Instant timestamp;
    
    public static TransferEvent from(Transfer transfer) {
        return new TransferEvent(
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