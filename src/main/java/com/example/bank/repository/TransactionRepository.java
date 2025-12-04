package com.example.bank.repository;

import com.example.bank.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TransactionRepository - Data access layer for Transaction entities.
 * Provides queries for finding transactions and checking idempotency.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /**
     * Finds a transaction by idempotency key.
     * Used to enforce idempotency - if a request with same key was already processed,
     * return the existing transaction instead of creating a new one.
     */
    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);

    /**
     * Finds all transactions for a source account.
     */
    List<Transaction> findBySourceAccountId(UUID sourceAccountId);

    /**
     * Finds all transactions for a destination account.
     */
    List<Transaction> findByDestinationAccountId(UUID destinationAccountId);

    /**
     * Finds all transactions related to an account (either source or destination).
     */
    @Query("SELECT t FROM Transaction t WHERE t.sourceAccountId = :accountId OR t.destinationAccountId = :accountId ORDER BY t.createdAt DESC")
    List<Transaction> findAllByAccountId(@Param("accountId") UUID accountId);

    /**
     * Counts pending transactions for an account.
     */
    @Query("SELECT COUNT(t) FROM Transaction t WHERE (t.sourceAccountId = :accountId OR t.destinationAccountId = :accountId) AND t.status = 'PENDING'")
    long countPendingByAccountId(@Param("accountId") UUID accountId);
}
