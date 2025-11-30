package io.malicki.bankingsystem.domain.transfer;

public enum TransferStatus {
    PENDING,        // Created, waiting for validation phase
    VALIDATING,     // Validation started, not finished yet
    VALIDATED,      // Validated, waiting for execution
    EXECUTING,      // Execution started, not completed yet
    COMPLETED,      // Success
    FAILED          // Failed, business reason
}