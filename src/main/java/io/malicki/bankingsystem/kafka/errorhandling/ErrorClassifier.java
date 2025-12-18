package io.malicki.bankingsystem.kafka.errorhandling;

import io.malicki.bankingsystem.exception.AccountNotFoundException;
import io.malicki.bankingsystem.exception.InsufficientFundsException;
import io.malicki.bankingsystem.exception.InvalidAccountException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ErrorClassifier {
    
    public ErrorCategory classify(Exception exception) {
        
        // Business errors (0 retry)
        if (exception instanceof AccountNotFoundException ||
            exception instanceof InsufficientFundsException ||
            exception instanceof InvalidAccountException ||
            exception instanceof IllegalArgumentException ||
            exception instanceof IllegalStateException) {
            
            log.debug("Classified as BUSINESS_VALIDATION: {}", 
                    exception.getClass().getSimpleName());
            return ErrorCategory.BUSINESS_VALIDATION;
        }
        
        // Deserialization errors (0 retry)
        if (exception.getMessage() != null && 
            (exception.getMessage().contains("JSON") || 
             exception.getMessage().contains("deserialize") ||
             exception.getMessage().contains("parse"))) {
            
            log.debug("Classified as DESERIALIZATION: {}", exception.getMessage());
            return ErrorCategory.DESERIALIZATION;
        }
        
        // Technical transient errors (3-5 retry with backoff)
        if (exception instanceof java.sql.SQLException ||
            exception instanceof java.sql.SQLTransientException ||
            exception instanceof java.net.SocketTimeoutException ||
            exception instanceof java.net.ConnectException ||
            exception instanceof org.springframework.dao.TransientDataAccessException ||
            exception instanceof org.springframework.dao.QueryTimeoutException ||
            (exception.getMessage() != null && 
                (exception.getMessage().toLowerCase().contains("timeout") ||
                 exception.getMessage().toLowerCase().contains("connection") ||
                 exception.getMessage().toLowerCase().contains("lock")))) {
            
            log.debug("Classified as TECHNICAL_TRANSIENT: {}", 
                    exception.getClass().getSimpleName());
            return ErrorCategory.TECHNICAL_TRANSIENT;
        }
        
        // Unknown - conservative (1 retry)
        log.warn("Classified as UNKNOWN: {}", exception.getClass().getSimpleName());
        return ErrorCategory.UNKNOWN;
    }
    
    public boolean shouldRetry(Exception exception, int currentAttempt) {
        ErrorCategory category = classify(exception);
        return currentAttempt < category.getMaxRetries();
    }
    
    public long getRetryDelay(Exception exception, int attemptNumber) {
        ErrorCategory category = classify(exception);
        return category.getRetryDelay(attemptNumber);
    }
}