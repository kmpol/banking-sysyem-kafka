package io.malicki.bankingsystem.exception;

import lombok.Getter;

@Getter
public class InvalidAccountException extends RuntimeException {
    
    private final String accountNumber;
    private final String reason;
    
    public InvalidAccountException(String accountNumber, String reason) {
        super(String.format("Invalid account %s: %s", accountNumber, reason));
        this.accountNumber = accountNumber;
        this.reason = reason;
    }

}