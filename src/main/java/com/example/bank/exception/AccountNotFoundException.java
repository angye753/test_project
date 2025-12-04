package com.example.bank.exception;

import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends BankException {
    
    public AccountNotFoundException(String accountId) {
        super("Account not found: " + accountId, "ACCOUNT_NOT_FOUND", HttpStatus.NOT_FOUND.value());
    }
}
