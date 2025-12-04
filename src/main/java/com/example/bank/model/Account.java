package com.example.bank.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Account Entity - Represents a bank account with balance and status.
 * Enforces non-negative balance invariant.
 * 
 * Each account has a unique UUID identifier (accountId) and maintains a balance
 * with currency information. Accounts can be ACTIVE, FROZEN, or CLOSED.
 */
@Entity
@Table(name = "accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@AttributeOverrides({
    @AttributeOverride(name = "balance.amount", column = @Column(name = "balance_amount")),
    @AttributeOverride(name = "balance.currency", column = @Column(name = "balance_currency"))
})
public class Account {

    /**
     * Unique identifier for the account (UUID).
     * Used in API paths as {accountId}.
     */
    @Id
    @Column(columnDefinition = "UUID")
    @JsonProperty("accountId")
    private UUID id;

    @Column(nullable = false)
    private String holderName;

    @Embedded
    private Money balance;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AccountStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        id = UUID.randomUUID();
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = AccountStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Validates account invariants before persistence.
     */
    public void validate() {
        if (holderName == null || holderName.isBlank()) {
            throw new IllegalArgumentException("Account holder name is required");
        }
        if (balance == null) {
            throw new IllegalArgumentException("Balance is required");
        }
        balance.validate();
        if (status == null) {
            throw new IllegalArgumentException("Account status is required");
        }
    }

    /**
     * Checks if account is able to perform transactions.
     */
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /**
     * Debits the account (subtracts money).
     * Throws exception if result would be negative.
     */
    public void debit(Money amount) {
        if (!isActive()) {
            throw new IllegalStateException("Cannot debit inactive account");
        }
        this.balance = this.balance.subtract(amount);
    }

    /**
     * Credits the account (adds money).
     */
    public void credit(Money amount) {
        if (!isActive()) {
            throw new IllegalStateException("Cannot credit inactive account");
        }
        this.balance = this.balance.add(amount);
    }

    public enum AccountStatus {
        ACTIVE, FROZEN, CLOSED
    }
}
