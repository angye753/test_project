package com.example.bank.repository;

import com.example.bank.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * LedgerEntryRepository - Data access layer for append-only LedgerEntry entities.
 * Provides queries for audit trail and ledger balance calculations.
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Finds all ledger entries for a transaction.
     * Useful for verifying double-entry bookkeeping compliance.
     */
    List<LedgerEntry> findByTransactionId(UUID transactionId);

    /**
     * Finds all ledger entries for an account, ordered by timestamp.
     * Used to reconstruct account state and generate audit trail.
     */
    List<LedgerEntry> findByAccountIdOrderByTimestampAsc(UUID accountId);

    /**
     * Counts ledger entries for a transaction.
     * For double-entry bookkeeping, should be exactly 2 for most transactions.
     */
    long countByTransactionId(UUID transactionId);

    /**
     * Finds all ledger entries for an account filtered by type.
     */
    @Query("SELECT l FROM LedgerEntry l WHERE l.accountId = :accountId AND l.type = :type ORDER BY l.timestamp ASC")
    List<LedgerEntry> findByAccountAndType(@Param("accountId") UUID accountId, @Param("type") LedgerEntry.EntryType type);

    /**
     * Calculates total debits for an account.
     */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l WHERE l.accountId = :accountId AND l.type = 'DEBIT'")
    java.math.BigDecimal sumDebitsByAccount(@Param("accountId") UUID accountId);

    /**
     * Calculates total credits for an account.
     */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l WHERE l.accountId = :accountId AND l.type = 'CREDIT'")
    java.math.BigDecimal sumCreditsByAccount(@Param("accountId") UUID accountId);

    /**
     * Calculates total fees for an account.
     */
    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LedgerEntry l WHERE l.accountId = :accountId AND l.type = 'FEE'")
    java.math.BigDecimal sumFeesByAccount(@Param("accountId") UUID accountId);
}
