package com.example.bank.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Money Value Object - Immutable representation of monetary amount with currency.
 * Ensures all monetary operations use consistent precision and non-negative values.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Money implements Serializable {

    private BigDecimal amount;
    private String currency;

    public Money(long amount, String currency) {
        this.amount = BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(100));
        this.currency = currency;
    }

    public Money(String amount, String currency) {
        this.amount = new BigDecimal(amount);
        this.currency = currency;
    }

    /**
     * Validates that the amount is non-negative.
     */
    public void validate() {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount cannot be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be null or empty");
        }
        if (currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be ISO 4217 code (3 characters)");
        }
    }

    /**
     * Adds money (returns new instance, maintains immutability).
     */
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot add different currencies");
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Subtracts money (returns new instance, maintains immutability).
     */
    public Money subtract(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot subtract different currencies");
        }
        Money result = new Money(this.amount.subtract(other.amount), this.currency);
        result.validate();
        return result;
    }

    /**
     * Checks if amount is greater than or equal to another money value.
     */
    public boolean isGreaterThanOrEqual(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot compare different currencies");
        }
        return this.amount.compareTo(other.amount) >= 0;
    }

    /**
     * Checks if amount is greater than another money value.
     */
    public boolean isGreaterThan(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Cannot compare different currencies");
        }
        return this.amount.compareTo(other.amount) > 0;
    }

    /**
     * Checks if amount equals zero.
     */
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }
}
