package io.malicki.bankingsystem.domain.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    
    List<OutboxEvent> findTop100ByProcessedFalseOrderByCreatedAtAsc();
    
    long countByProcessedFalse();
}