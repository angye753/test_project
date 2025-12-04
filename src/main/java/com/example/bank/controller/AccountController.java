package com.example.bank.controller;

import com.example.bank.model.Account;
import com.example.bank.model.Transaction;
import com.example.bank.service.AccountService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * AccountController - REST endpoints for account operations.
 * Handles account creation, retrieval, withdrawals, transfers, and deposits.
 * Requires JWT authentication for all endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Create a new account.
     * 
     * @param request Account creation request
     * @return Created account details
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<AccountResponse> createAccount(@RequestBody CreateAccountRequest request) {
        log.info("Creating account: {}", request.getHolderName());
        
        Account account = accountService.createAccount(
            request.getHolderName(),
            request.getInitialBalance(),
            request.getCurrency()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AccountResponse.fromEntity(account));
    }

    /**
     * Get account details by ID.
     * 
     * @param accountId Account UUID
     * @return Account details
     */
    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyRole('ACCOUNT_HOLDER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId) {
        log.info("Fetching account: {}", accountId);
        
        Account account = accountService.getAccount(accountId);
        return ResponseEntity.ok(AccountResponse.fromEntity(account));
    }

    /**
     * Withdraw funds from an account.
     * Atomically debits the account and creates ledger entries.
     * Enforces idempotency - same request idempotency key returns same result.
     * 
     * @param accountId Account to withdraw from
     * @param request Withdrawal details (amount, idempotency key)
     * @return Transaction details
     */
    @PostMapping("/{accountId}/withdraw")
    @PreAuthorize("hasAnyRole('ACCOUNT_HOLDER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable UUID accountId,
            @RequestBody WithdrawalRequest request) {
        log.info("Withdrawal request: account={}, amount={}, idempotencyKey={}", 
                 accountId, request.getAmount(), request.getIdempotencyKey());
        
        String initiatedBy = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication().getName();
        
        Transaction transaction = accountService.withdraw(
            accountId,
            request.getAmount(),
            request.getCurrency(),
            request.getIdempotencyKey(),
            initiatedBy
        );
        
        return ResponseEntity.ok(TransactionResponse.fromEntity(transaction));
    }

    /**
     * Deposit funds into an account.
     * Atomically credits the account and creates ledger entries.
     * Enforces idempotency - same request idempotency key returns same result.
     * 
     * @param accountId Account to deposit into
     * @param request Deposit details (amount, idempotency key)
     * @return Transaction details
     */
    @PostMapping("/{accountId}/deposit")
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable UUID accountId,
            @RequestBody DepositRequest request) {
        log.info("Deposit request: account={}, amount={}, idempotencyKey={}", 
                 accountId, request.getAmount(), request.getIdempotencyKey());
        
        String initiatedBy = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication().getName();
        
        Transaction transaction = accountService.deposit(
            accountId,
            request.getAmount(),
            request.getCurrency(),
            request.getIdempotencyKey(),
            initiatedBy
        );
        
        return ResponseEntity.ok(TransactionResponse.fromEntity(transaction));
    }

    /**
     * Transfer funds between two accounts.
     * Atomically debits source and credits destination with pessimistic locking.
     * Enforces idempotency - same request idempotency key returns same result.
     * 
     * @param sourceAccountId Account to transfer from
     * @param request Transfer details (destination, amount, idempotency key)
     * @return Transaction details
     */
    @PostMapping("/{sourceAccountId}/transfer")
    @PreAuthorize("hasAnyRole('ACCOUNT_HOLDER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<TransactionResponse> transfer(
            @PathVariable UUID sourceAccountId,
            @RequestBody TransferRequest request) {
        log.info("Transfer request: from={}, to={}, amount={}, idempotencyKey={}", 
                 sourceAccountId, request.getDestinationAccountId(), request.getAmount(), request.getIdempotencyKey());
        
        String initiatedBy = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication().getName();
        
        Transaction transaction = accountService.transfer(
            sourceAccountId,
            request.getDestinationAccountId(),
            request.getAmount(),
            request.getCurrency(),
            request.getIdempotencyKey(),
            initiatedBy
        );
        
        return ResponseEntity.ok(TransactionResponse.fromEntity(transaction));
    }

    /**
     * Get transaction history for an account.
     * 
     * @param accountId Account UUID
     * @return List of transactions for the account
     */
    @GetMapping("/{accountId}/transactions")
    @PreAuthorize("hasAnyRole('ACCOUNT_HOLDER', 'OPERATOR', 'ADMIN')")
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(
            @PathVariable UUID accountId) {
        log.info("Fetching transaction history: {}", accountId);
        
        List<Transaction> transactions = accountService.getTransactionHistory(accountId);
        List<TransactionResponse> responses = transactions.stream()
            .map(TransactionResponse::fromEntity)
            .toList();
        
        return ResponseEntity.ok(responses);
    }

    // ==================== DTOs ====================

    @Data
    @AllArgsConstructor
    public static class CreateAccountRequest {
        private String holderName;
        private BigDecimal initialBalance;
        private String currency;
    }

    @Data
    @AllArgsConstructor
    public static class WithdrawalRequest {
        private BigDecimal amount;
        private String currency;
        private UUID idempotencyKey;
    }

    @Data
    @AllArgsConstructor
    public static class DepositRequest {
        private BigDecimal amount;
        private String currency;
        private UUID idempotencyKey;
    }

    @Data
    @AllArgsConstructor
    public static class TransferRequest {
        private UUID destinationAccountId;
        private BigDecimal amount;
        private String currency;
        private UUID idempotencyKey;
    }

    @Data
    @Builder
    public static class AccountResponse {
        private UUID accountId;
        private String holderName;
        private BigDecimal balance;
        private String currency;
        private String status;
        private String createdAt;
        private String updatedAt;

        public static AccountResponse fromEntity(Account account) {
            return AccountResponse.builder()
                .accountId(account.getId())
                .holderName(account.getHolderName())
                .balance(account.getBalance().getAmount())
                .currency(account.getBalance().getCurrency())
                .status(account.getStatus().toString())
                .createdAt(account.getCreatedAt().toString())
                .updatedAt(account.getUpdatedAt().toString())
                .build();
        }
    }

    @Data
    @Builder
    public static class TransactionResponse {
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

        public static TransactionResponse fromEntity(Transaction transaction) {
            return TransactionResponse.builder()
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
}
