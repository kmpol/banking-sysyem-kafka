package io.malicki.bankingsystem.domain.account;

import io.malicki.bankingsystem.exception.InsufficientFundsException;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true, length = 26)
    private String accountNumber;
    
    @Column(nullable = false, length = 100)
    private String ownerName;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    
    @Column(nullable = false)
    private boolean active = true;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Version
    private Long version;  // Optimistic locking (important!)
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
    
    // Business methods
    public void withdraw(BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountNumber, balance, amount);
        }
        this.balance = this.balance.subtract(amount);
    }
    
    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}