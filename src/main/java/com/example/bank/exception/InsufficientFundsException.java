package com.example.bank.exception;

import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends BankException {
    
    public InsufficientFundsException(String message) {
        super(message, "INSUFFICIENT_FUNDS", HttpStatus.BAD_REQUEST.value());
    }
}
