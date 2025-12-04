package com.example.bank.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transaction Entity - Represents a bank transaction (withdrawal, deposit, transfer, fee).
 * Each transaction is uniquely identified and must be idempotent.
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_idempotency_key", columnList = "idempotency_key", unique = true),
    @Index(name = "idx_source_account", columnList = "source_account_id"),
    @Index(name = "idx_destination_account", columnList = "destination_account_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Column(columnDefinition = "UUID")
    private UUID sourceAccountId;

    @Column(columnDefinition = "UUID")
    private UUID destinationAccountId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, unique = true, columnDefinition = "UUID")
    private UUID idempotencyKey;

    @Column(nullable = false)
    private String initiatedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        id = UUID.randomUUID();
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
    }

    /**
     * Validates transaction invariants.
     */
    public void validate() {
        if (type == null) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
        if (currency == null || currency.isBlank() || currency.length() != 3) {
            throw new IllegalArgumentException("Currency must be valid ISO 4217 code");
        }
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (initiatedBy == null || initiatedBy.isBlank()) {
            throw new IllegalArgumentException("Initiated by is required");
        }

        // Type-specific validations
        switch (type) {
            case WITHDRAWAL:
                if (sourceAccountId == null) {
                    throw new IllegalArgumentException("Withdrawal requires source account");
                }
                if (destinationAccountId != null) {
                    throw new IllegalArgumentException("Withdrawal cannot have destination account");
                }
                break;
            case DEPOSIT:
                if (destinationAccountId == null) {
                    throw new IllegalArgumentException("Deposit requires destination account");
                }
                if (sourceAccountId != null) {
                    throw new IllegalArgumentException("Deposit cannot have source account");
                }
                break;
            case TRANSFER:
                if (sourceAccountId == null || destinationAccountId == null) {
                    throw new IllegalArgumentException("Transfer requires both source and destination accounts");
                }
                if (sourceAccountId.equals(destinationAccountId)) {
                    throw new IllegalArgumentException("Cannot transfer to same account");
                }
                break;
            case FEE:
                if (destinationAccountId == null) {
                    throw new IllegalArgumentException("Fee requires destination account");
                }
                break;
        }
    }

    /**
     * Marks transaction as posted (successfully completed).
     */
    public void markAsPosted() {
        this.status = TransactionStatus.POSTED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Marks transaction as failed.
     */
    public void markAsFailed() {
        this.status = TransactionStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * Checks if transaction is in pending state.
     */
    public boolean isPending() {
        return status == TransactionStatus.PENDING;
    }

    public enum TransactionType {
        WITHDRAWAL, TRANSFER, DEPOSIT, FEE
    }

    public enum TransactionStatus {
        PENDING, POSTED, FAILED
    }
}
