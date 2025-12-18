package io.malicki.bankingsystem.exception;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class InsufficientFundsException extends RuntimeException {

    private final String accountNumber;
    private final BigDecimal currentBalance;
    private final BigDecimal requestedAmount;

    public InsufficientFundsException(
            String accountNumber,
            BigDecimal currentBalance,
            BigDecimal requestedAmount
    ) {
        super(String.format(
                "Insufficient funds in account %s. Balance: %s, Requested: %s",
                accountNumber, currentBalance, requestedAmount
        ));
        this.accountNumber = accountNumber;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }

}