package com.example.bank.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LedgerEntry Entity - Represents an immutable, append-only ledger entry.
 * Implements double-entry bookkeeping: each transaction creates balanced debit/credit entries.
 * Entries are never updated or deleted, only compensating entries are added.
 */
@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_account_id", columnList = "account_id"),
    @Index(name = "idx_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(nullable = false, columnDefinition = "UUID")
    private UUID transactionId;

    @Column(nullable = false, columnDefinition = "UUID")
    private UUID accountId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EntryType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        id = UUID.randomUUID();
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    /**
     * Validates ledger entry invariants.
     * Entries are immutable and amount must be non-negative.
     */
    public void validate() {
        if (transactionId == null) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        if (accountId == null) {
            throw new IllegalArgumentException("Account ID is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("Entry type is required");
        }
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Entry amount cannot be negative");
        }
        if (currency == null || currency.isBlank() || currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be valid ISO 4217 code");
        }
    }

    /**
     * Checks if this is a debit entry (money out).
     */
    public boolean isDebit() {
        return type == EntryType.DEBIT;
    }

    /**
     * Checks if this is a credit entry (money in).
     */
    public boolean isCredit() {
        return type == EntryType.CREDIT;
    }

    /**
     * Checks if this is a fee entry.
     */
    public boolean isFee() {
        return type == EntryType.FEE;
    }

    public enum EntryType {
        DEBIT,   // Money out of account
        CREDIT,  // Money into account
        FEE      // Fee charged to account
    }
}
