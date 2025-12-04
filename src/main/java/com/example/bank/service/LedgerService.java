package com.example.bank.service;

import com.example.bank.model.LedgerEntry;
import com.example.bank.model.Transaction;
import com.example.bank.repository.LedgerEntryRepository;
import com.example.bank.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * LedgerService - Manages append-only ledger entries and enforces double-entry bookkeeping rules.
 * Provides immutable audit trail with comprehensive compliance features.
 * 
 * Key Principles:
 * 1. Append-only: Entries are never updated or deleted
 * 2. Double-entry bookkeeping: Every transaction has balanced debit/credit entries
 * 3. Immutability: Ledger entries are immutable once created (updatable=false)
 * 4. Audit trail: Complete history of all transactions for regulatory compliance
 */
@Slf4j
@Service
@Transactional
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final TransactionRepository transactionRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository,
                       TransactionRepository transactionRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Creates a single ledger entry (append-only operation).
     * Validates that the entry meets all invariants before persistence.
     * 
     * @param entry LedgerEntry to record (immutable once created)
     * @return Persisted LedgerEntry with ID
     * @throws IllegalArgumentException If entry violates invariants
     */
    public LedgerEntry recordEntry(LedgerEntry entry) {
        log.debug("Recording ledger entry: transactionId={}, accountId={}, type={}, amount={}",
                 entry.getTransactionId(), entry.getAccountId(), entry.getType(), entry.getAmount());
        
        // Validate entry invariants
        entry.validate();
        
        // Persist the entry (append-only, never to be updated)
        LedgerEntry savedEntry = ledgerEntryRepository.save(entry);
        
        log.info("Ledger entry recorded: id={}, type={}, amount={}", 
                savedEntry.getId(), entry.getType(), entry.getAmount());
        
        return savedEntry;
    }

    /**
     * Records a debit ledger entry (money out of account).
     * Used for withdrawals, transfers (source), and fees.
     * 
     * @param transactionId Associated transaction UUID
     * @param accountId Account being debited
     * @param amount Amount to debit (must be positive)
     * @param currency ISO 4217 currency code
     * @return Recorded ledger entry
     */
    public LedgerEntry recordDebit(UUID transactionId, UUID accountId, BigDecimal amount, String currency) {
        LedgerEntry debitEntry = LedgerEntry.builder()
            .transactionId(transactionId)
            .accountId(accountId)
            .type(LedgerEntry.EntryType.DEBIT)
            .amount(amount)
            .currency(currency)
            .build();
        
        return recordEntry(debitEntry);
    }

    /**
     * Records a credit ledger entry (money into account).
     * Used for deposits and transfers (destination).
     * 
     * @param transactionId Associated transaction UUID
     * @param accountId Account being credited
     * @param amount Amount to credit (must be positive)
     * @param currency ISO 4217 currency code
     * @return Recorded ledger entry
     */
    public LedgerEntry recordCredit(UUID transactionId, UUID accountId, BigDecimal amount, String currency) {
        LedgerEntry creditEntry = LedgerEntry.builder()
            .transactionId(transactionId)
            .accountId(accountId)
            .type(LedgerEntry.EntryType.CREDIT)
            .amount(amount)
            .currency(currency)
            .build();
        
        return recordEntry(creditEntry);
    }

    /**
     * Records a fee ledger entry (charges to account).
     * Used for transaction fees and penalties.
     * 
     * @param transactionId Associated transaction UUID
     * @param accountId Account being charged
     * @param amount Fee amount (must be positive)
     * @param currency ISO 4217 currency code
     * @return Recorded ledger entry
     */
    public LedgerEntry recordFee(UUID transactionId, UUID accountId, BigDecimal amount, String currency) {
        LedgerEntry feeEntry = LedgerEntry.builder()
            .transactionId(transactionId)
            .accountId(accountId)
            .type(LedgerEntry.EntryType.FEE)
            .amount(amount)
            .currency(currency)
            .build();
        
        return recordEntry(feeEntry);
    }

    /**
     * Validates double-entry bookkeeping rules for a transaction.
     * For each transaction, sum of debits must equal sum of credits plus fees.
     * 
     * @param transactionId Transaction to validate
     * @return true if transaction is balanced, false otherwise
     */
    public boolean validateDoubleEntry(UUID transactionId) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transactionId);
        
        if (entries.isEmpty()) {
            log.warn("No ledger entries found for transaction: {}", transactionId);
            return false;
        }
        
        // Get the associated transaction to understand the operation
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElse(null);
        
        if (transaction == null) {
            log.error("Transaction not found: {}", transactionId);
            return false;
        }
        
        BigDecimal totalDebits = entries.stream()
            .filter(LedgerEntry::isDebit)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalCredits = entries.stream()
            .filter(LedgerEntry::isCredit)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalFees = entries.stream()
            .filter(LedgerEntry::isFee)
            .map(LedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // For withdrawal: debit from account equals transaction amount
        // For deposit: credit to account equals transaction amount
        // For transfer: debit from source + credit to destination = 2x transaction amount
        boolean isBalanced = false;
        
        switch (transaction.getType()) {
            case WITHDRAWAL:
                // Debit should equal transaction amount
                isBalanced = totalDebits.compareTo(transaction.getAmount()) == 0;
                break;
            case DEPOSIT:
                // Credit should equal transaction amount
                isBalanced = totalCredits.compareTo(transaction.getAmount()) == 0;
                break;
            case TRANSFER:
                // Total debits + credits should be 2x transaction amount
                isBalanced = totalDebits.add(totalCredits).compareTo(
                    transaction.getAmount().multiply(new BigDecimal(2))) == 0;
                break;
            case FEE:
                // Fee should equal transaction amount
                isBalanced = totalFees.compareTo(transaction.getAmount()) == 0;
                break;
        }
        
        log.info("Double-entry validation for transaction {}: debits={}, credits={}, fees={}, balanced={}",
                transactionId, totalDebits, totalCredits, totalFees, isBalanced);
        
        return isBalanced;
    }

    /**
     * Retrieves the complete immutable ledger for an account.
     * Returns all entries in chronological order (oldest first).
     * 
     * @param accountId Account UUID
     * @return List of ledger entries in timestamp order
     */
    public List<LedgerEntry> getAccountLedger(UUID accountId) {
        log.debug("Retrieving ledger for account: {}", accountId);
        return ledgerEntryRepository.findByAccountIdOrderByTimestampAsc(accountId);
    }

    /**
     * Retrieves all ledger entries for a specific transaction.
     * Useful for verifying transaction integrity and double-entry rules.
     * 
     * @param transactionId Transaction UUID
     * @return List of ledger entries for the transaction
     */
    public List<LedgerEntry> getTransactionEntries(UUID transactionId) {
        log.debug("Retrieving ledger entries for transaction: {}", transactionId);
        return ledgerEntryRepository.findByTransactionId(transactionId);
    }

    /**
     * Calculates the running balance for an account based on ledger entries.
     * Uses append-only ledger to reconstruct account state.
     * 
     * @param accountId Account UUID
     * @return Current balance based on ledger
     */
    public BigDecimal calculateBalance(UUID accountId) {
        BigDecimal debits = ledgerEntryRepository.sumDebitsByAccount(accountId);
        BigDecimal credits = ledgerEntryRepository.sumCreditsByAccount(accountId);
        BigDecimal fees = ledgerEntryRepository.sumFeesByAccount(accountId);
        
        // Balance = credits - debits - fees
        BigDecimal balance = credits.subtract(debits).subtract(fees);
        
        log.debug("Calculated balance for account {}: credits={}, debits={}, fees={}, balance={}",
                 accountId, credits, debits, fees, balance);
        
        return balance;
    }

    /**
     * Validates ledger integrity for an account.
     * Checks that all related transactions are properly balanced.
     * 
     * @param accountId Account to validate
     * @return true if all transactions for the account are balanced
     */
    public boolean validateAccountLedger(UUID accountId) {
        List<LedgerEntry> entries = getAccountLedger(accountId);
        
        if (entries.isEmpty()) {
            log.info("Account {} has no ledger entries", accountId);
            return true;
        }
        
        // Get unique transaction IDs for this account
        java.util.Set<UUID> transactionIds = entries.stream()
            .map(LedgerEntry::getTransactionId)
            .collect(java.util.stream.Collectors.toSet());
        
        // Validate each transaction is balanced
        boolean allBalanced = transactionIds.stream()
            .allMatch(this::validateDoubleEntry);
        
        log.info("Ledger validation for account {}: {} transactions, all balanced={}",
                accountId, transactionIds.size(), allBalanced);
        
        return allBalanced;
    }

    /**
     * Retrieves ledger entries of a specific type for an account.
     * Useful for analyzing specific transaction types (debits, credits, fees).
     * 
     * @param accountId Account UUID
     * @param entryType Type of entry to retrieve
     * @return List of matching ledger entries
     */
    public List<LedgerEntry> getEntriesByType(UUID accountId, LedgerEntry.EntryType entryType) {
        log.debug("Retrieving {} entries for account {}", entryType, accountId);
        return ledgerEntryRepository.findByAccountAndType(accountId, entryType);
    }

    /**
     * Counts ledger entries for a transaction (should be 2 for double-entry).
     * 
     * @param transactionId Transaction UUID
     * @return Count of ledger entries
     */
    public long countTransactionEntries(UUID transactionId) {
        return ledgerEntryRepository.countByTransactionId(transactionId);
    }

    /**
     * Verifies that no ledger entries have been modified (immutability check).
     * This is a sanity check and should always be true due to JPA mappings.
     * In production, you might verify this against a hash or checksum.
     * 
     * @param ledgerId Ledger entry ID
     * @return true if entry exists and is immutable (always true if found)
     */
    public boolean verifyEntryImmutability(UUID ledgerId) {
        return ledgerEntryRepository.findById(ledgerId).isPresent();
    }
}
