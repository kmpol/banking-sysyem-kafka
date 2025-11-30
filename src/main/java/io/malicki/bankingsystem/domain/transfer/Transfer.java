package io.malicki.bankingsystem.domain.transfer;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "transfers",
    indexes = {
        @Index(name = "idx_transfer_id", columnList = "transferId", unique = true),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_created_at", columnList = "createdAt")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Transfer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 36)
    private String transferId;  // UUID - idempotency key
    
    @Column(nullable = false)
    private String fromAccountNumber;
    
    @Column(nullable = false)
    private String toAccountNumber;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column
    private Instant processedAt;
    
    @Column(length = 500)
    private String failureReason;
    
    @Column(length = 200)
    private String description;
    
    @Version
    private Long version;  // Optimistic locking
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = TransferStatus.PENDING;
        }
    }
}