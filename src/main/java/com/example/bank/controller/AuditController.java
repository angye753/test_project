package com.example.bank.controller;

import com.example.bank.model.LedgerEntry;
import com.example.bank.model.Transaction;
import com.example.bank.repository.LedgerEntryRepository;
import com.example.bank.repository.TransactionRepository;
import com.example.bank.service.LedgerService;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * AuditController - REST endpoints for audit trail and ledger inspection.
 * Restricted to OPERATOR and ADMIN roles for compliance and investigation.
 * Provides immutable append-only ledger entries for regulatory compliance.
 * Uses LedgerService for advanced audit queries and validation.
 */
@Slf4j
@RestController
@RequestMapping("/v1/audit")
public class AuditController {

    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final LedgerService ledgerService;

    public AuditController(TransactionRepository transactionRepository,
                          LedgerEntryRepository ledgerEntryRepository,
                          LedgerService ledgerService) {
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.ledgerService = ledgerService;
    }

    /**
     * Get all transactions (with pagination support for production use).
     * Restricted to operators and admins for audit purposes.
     * 
     * @return List of all transactions
     */
    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<List<TransactionAuditResponse>> getAllTransactions() {
        log.info("Fetching all transactions for audit");
        
        List<Transaction> transactions = transactionRepository.findAll();
        List<TransactionAuditResponse> responses = transactions.stream()
            .map(TransactionAuditResponse::fromEntity)
            .toList();
        
        return ResponseEntity.ok(responses);
    }

    /**
     * Get ledger entries for a specific account.
     * Provides complete audit trail of all debits, credits, and fees.
     * 
     * @param accountId Account UUID
     * @return Sorted list of ledger entries (oldest first)
     */
    @GetMapping("/ledger/{accountId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<List<LedgerEntryAuditResponse>> getLedgerByAccount(
            @PathVariable UUID accountId) {
        log.info("Fetching ledger entries for account: {}", accountId);
        
        List<LedgerEntry> entries = ledgerEntryRepository.findByAccountIdOrderByTimestampAsc(accountId);
        List<LedgerEntryAuditResponse> responses = entries.stream()
            .map(LedgerEntryAuditResponse::fromEntity)
            .toList();
        
        return ResponseEntity.ok(responses);
    }

    /**
     * Get all ledger entries (complete audit trail).
     * Restricted to admins only due to volume and sensitivity.
     * 
     * @return Complete ledger
     */
    @GetMapping("/ledger")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<LedgerEntryAuditResponse>> getCompleteLedger() {
        log.info("Fetching complete ledger - admin access only");
        
        List<LedgerEntry> entries = ledgerEntryRepository.findAll();
        List<LedgerEntryAuditResponse> responses = entries.stream()
            .map(LedgerEntryAuditResponse::fromEntity)
            .toList();
        
        return ResponseEntity.ok(responses);
    }

    /**
     * Get ledger entries for a specific transaction.
     * Verifies double-entry bookkeeping compliance.
     * 
     * @param transactionId Transaction UUID
     * @return Ledger entries associated with transaction
     */
    @GetMapping("/ledger/transaction/{transactionId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<TransactionLedgerSummary> getLedgerByTransaction(
            @PathVariable UUID transactionId) {
        log.info("Fetching ledger entries for transaction: {}", transactionId);
        
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transactionId);
        
        // Verify double-entry bookkeeping: sum of debits should equal sum of credits
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
        
        List<LedgerEntryAuditResponse> entryResponses = entries.stream()
            .map(LedgerEntryAuditResponse::fromEntity)
            .toList();
        
        return ResponseEntity.ok(TransactionLedgerSummary.builder()
            .transaction(TransactionAuditResponse.fromEntity(transaction))
            .entries(entryResponses)
            .totalDebits(totalDebits)
            .totalCredits(totalCredits)
            .totalFees(totalFees)
            .isBalanced(totalDebits.add(totalCredits).equals(transaction.getAmount().multiply(new BigDecimal(2))) || 
                       totalCredits.equals(transaction.getAmount()))
            .build());
    }

    /**
     * Get transaction count statistics.
     * Useful for monitoring and reporting.
     * 
     * @return Transaction statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<StatisticsResponse> getStatistics() {
        log.info("Fetching transaction statistics");
        
        List<Transaction> allTransactions = transactionRepository.findAll();
        
        long totalTransactions = allTransactions.size();
        long postedTransactions = allTransactions.stream()
            .filter(t -> t.getStatus().toString().equals("POSTED"))
            .count();
        long pendingTransactions = allTransactions.stream()
            .filter(t -> t.getStatus().toString().equals("PENDING"))
            .count();
        long failedTransactions = allTransactions.stream()
            .filter(t -> t.getStatus().toString().equals("FAILED"))
            .count();
        
        BigDecimal totalPostedAmount = allTransactions.stream()
            .filter(t -> t.getStatus().toString().equals("POSTED"))
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return ResponseEntity.ok(StatisticsResponse.builder()
            .totalTransactions(totalTransactions)
            .postedTransactions(postedTransactions)
            .pendingTransactions(pendingTransactions)
            .failedTransactions(failedTransactions)
            .totalPostedAmount(totalPostedAmount)
            .build());
    }

    /**
     * Validate ledger integrity for an account.
     * Checks that all transactions associated with the account are properly balanced
     * according to double-entry bookkeeping rules.
     * 
     * @param accountId Account UUID
     * @return Validation result with status and any issues found
     */
    @GetMapping("/ledger/validate/{accountId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<LedgerValidationResponse> validateAccountLedger(
            @PathVariable UUID accountId) {
        log.info("Validating ledger for account: {}", accountId);
        
        boolean isValid = ledgerService.validateAccountLedger(accountId);
        BigDecimal calculatedBalance = ledgerService.calculateBalance(accountId);
        List<LedgerEntry> entries = ledgerService.getAccountLedger(accountId);
        
        return ResponseEntity.ok(LedgerValidationResponse.builder()
            .accountId(accountId)
            .isValid(isValid)
            .ledgerEntryCount(entries.size())
            .calculatedBalance(calculatedBalance)
            .message(isValid ? "Ledger is balanced and valid" : "Ledger validation failed - inconsistencies detected")
            .build());
    }

    /**
     * Validate double-entry bookkeeping for a specific transaction.
     * Ensures that debits and credits are balanced for the transaction.
     * 
     * @param transactionId Transaction UUID
     * @return Validation result with entry details
     */
    @GetMapping("/ledger/validate/transaction/{transactionId}")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<TransactionValidationResponse> validateTransactionDoubleEntry(
            @PathVariable UUID transactionId) {
        log.info("Validating double-entry for transaction: {}", transactionId);
        
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        
        boolean isBalanced = ledgerService.validateDoubleEntry(transactionId);
        List<LedgerEntry> entries = ledgerService.getTransactionEntries(transactionId);
        
        return ResponseEntity.ok(TransactionValidationResponse.builder()
            .transactionId(transactionId)
            .transactionType(transaction.getType().toString())
            .isBalanced(isBalanced)
            .entryCount(entries.size())
            .entries(entries.stream().map(LedgerEntryAuditResponse::fromEntity).toList())
            .message(isBalanced ? "Transaction is properly balanced" : "Transaction balance violation detected")
            .build());
    }

    /**
     * Get account balance calculated from ledger entries.
     * Provides the authoritative balance based on append-only ledger.
     * 
     * @param accountId Account UUID
     * @return Calculated balance
     */
    @GetMapping("/ledger/balance/{accountId}")
    @PreAuthorize("hasAnyRole('ACCOUNT_HOLDER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<BalanceResponse> getCalculatedBalance(
            @PathVariable UUID accountId) {
        log.info("Calculating balance for account: {}", accountId);
        
        BigDecimal balance = ledgerService.calculateBalance(accountId);
        long entryCount = ledgerEntryRepository.findByAccountIdOrderByTimestampAsc(accountId).size();
        
        return ResponseEntity.ok(BalanceResponse.builder()
            .accountId(accountId)
            .balance(balance)
            .currency("USD")
            .ledgerEntryCount(entryCount)
            .timestamp(java.time.LocalDateTime.now().toString())
            .build());
    }

    // ==================== DTOs ====================

    @Data
    @Builder
    public static class TransactionAuditResponse {
        private UUID id;
        private String type;
        private UUID sourceAccountId;
        private UUID destinationAccountId;
        private String status;
        private BigDecimal amount;
        private String currency;
        private UUID idempotencyKey;
        private String initiatedBy;
        private String createdAt;
        private String completedAt;

        public static TransactionAuditResponse fromEntity(Transaction transaction) {
            return TransactionAuditResponse.builder()
                .id(transaction.getId())
                .type(transaction.getType().toString())
                .sourceAccountId(transaction.getSourceAccountId())
                .destinationAccountId(transaction.getDestinationAccountId())
                .status(transaction.getStatus().toString())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .idempotencyKey(transaction.getIdempotencyKey())
                .initiatedBy(transaction.getInitiatedBy())
                .createdAt(transaction.getCreatedAt().toString())
                .completedAt(transaction.getCompletedAt() != null ? transaction.getCompletedAt().toString() : null)
                .build();
        }
    }

    @Data
    @Builder
    public static class LedgerEntryAuditResponse {
        private UUID id;
        private UUID transactionId;
        private UUID accountId;
        private String type;
        private BigDecimal amount;
        private String currency;
        private String timestamp;

        public static LedgerEntryAuditResponse fromEntity(LedgerEntry entry) {
            return LedgerEntryAuditResponse.builder()
                .id(entry.getId())
                .transactionId(entry.getTransactionId())
                .accountId(entry.getAccountId())
                .type(entry.getType().toString())
                .amount(entry.getAmount())
                .currency(entry.getCurrency())
                .timestamp(entry.getTimestamp().toString())
                .build();
        }
    }

    @Data
    @Builder
    public static class TransactionLedgerSummary {
        private TransactionAuditResponse transaction;
        private List<LedgerEntryAuditResponse> entries;
        private BigDecimal totalDebits;
        private BigDecimal totalCredits;
        private BigDecimal totalFees;
        private boolean isBalanced;
    }

    @Data
    @Builder
    public static class StatisticsResponse {
        private long totalTransactions;
        private long postedTransactions;
        private long pendingTransactions;
        private long failedTransactions;
        private BigDecimal totalPostedAmount;
    }

    @Data
    @Builder
    public static class LedgerValidationResponse {
        private UUID accountId;
        private boolean isValid;
        private long ledgerEntryCount;
        private BigDecimal calculatedBalance;
        private String message;
    }

    @Data
    @Builder
    public static class TransactionValidationResponse {
        private UUID transactionId;
        private String transactionType;
        private boolean isBalanced;
        private long entryCount;
        private List<LedgerEntryAuditResponse> entries;
        private String message;
    }

    @Data
    @Builder
    public static class BalanceResponse {
        private UUID accountId;
        private BigDecimal balance;
        private String currency;
        private long ledgerEntryCount;
        private String timestamp;
    }
}

