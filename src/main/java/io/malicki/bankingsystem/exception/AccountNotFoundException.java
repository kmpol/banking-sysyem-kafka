package io.malicki.bankingsystem.exception;

import lombok.Getter;

@Getter
public class AccountNotFoundException extends RuntimeException {

    private final String accountNumber;

    public AccountNotFoundException(String accountNumber) {
        super("Account not found: " + accountNumber);
        this.accountNumber = accountNumber;
    }

}