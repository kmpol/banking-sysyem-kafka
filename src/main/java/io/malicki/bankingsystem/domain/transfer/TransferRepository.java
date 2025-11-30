package io.malicki.bankingsystem.domain.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {
    
    Optional<Transfer> findByTransferId(String transferId);
    
    boolean existsByTransferId(String transferId);
}