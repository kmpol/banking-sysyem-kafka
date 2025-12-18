package io.malicki.bankingsystem.kafka.errorhandling;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FailedMessage implements Serializable {
    
    // Original message details
    private String originalTopic;
    private Integer originalPartition;
    private Long originalOffset;
    private String originalKey;
    private String originalValue;  // JSON string
    
    // Error details
    private String exceptionType;
    private String exceptionMessage;
    private String stackTrace;
    private Integer attemptCount;
    
    // Metadata
    private Instant failedAt;
    private String consumerGroupId;
    private Map<String, String> headers = new HashMap<>();
    
    // Error classification
    private ErrorCategory errorCategory;
    private boolean retryable;
}