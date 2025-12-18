package io.malicki.bankingsystem.kafka.errorhandling;

public enum ErrorCategory {
    
    BUSINESS_VALIDATION(
        0,                    // maxRetries
        0,                    // retryDelayMs
        false,                // autoRetryFromDlt
        "Business validation error - requires manual intervention"
    ),
    
    TECHNICAL_TRANSIENT(
        5,                    // maxRetries
        1000,                 // initialRetryDelayMs (1s)
        true,                 // autoRetryFromDlt
        "Transient technical error - may recover"
    ),
    
    DESERIALIZATION(
        0,
        0,
        false,
        "Deserialization error - message format invalid"
    ),
    
    UNKNOWN(
        1,
        500,
        false,
        "Unknown error - single retry then DLT"
    );
    
    private final int maxRetries;
    private final long initialRetryDelayMs;
    private final boolean autoRetryFromDlt;
    private final String description;
    
    ErrorCategory(int maxRetries, long initialRetryDelayMs, 
                  boolean autoRetryFromDlt, String description) {
        this.maxRetries = maxRetries;
        this.initialRetryDelayMs = initialRetryDelayMs;
        this.autoRetryFromDlt = autoRetryFromDlt;
        this.description = description;
    }

    public int getMaxRetries() { return maxRetries; }
    public long getInitialRetryDelayMs() { return initialRetryDelayMs; }
    public boolean isAutoRetryFromDlt() { return autoRetryFromDlt; }
    public String getDescription() { return description; }

    // Exponential backoff: 1s, 2s, 4s, 8s, 16s
    public long getRetryDelay(int attemptNumber) {
        if (maxRetries == 0) return 0;
        return initialRetryDelayMs * (long) Math.pow(2, attemptNumber);
    }
}