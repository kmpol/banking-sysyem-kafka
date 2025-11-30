package io.malicki.bankingsystem.domain.outbox;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(
    name = "outbox_events",
    indexes = {
        @Index(name = "idx_processed", columnList = "processed"),
        @Index(name = "idx_created_at", columnList = "createdAt")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String aggregateId;  // transferId
    
    @Column(nullable = false)
    private String eventType;  // "TransferValidated", "TransferCompleted"
    
    @Column(nullable = false)
    private String destinationTopic;
    
    @Column(nullable = false)
    private String routingKey;  // Kafka partition key
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;  // JSON
    
    @Column(nullable = false)
    private boolean processed = false;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column
    private Instant processedAt;
    
    @Column
    private Integer retryCount = 0;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}