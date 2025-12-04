package com.example.bank.service;

import com.example.bank.exception.AccountNotFoundException;
import com.example.bank.exception.InsufficientFundsException;
import com.example.bank.model.Account;
import com.example.bank.model.LedgerEntry;
import com.example.bank.model.Money;
import com.example.bank.model.Transaction;
import com.example.bank.repository.AccountRepository;
import com.example.bank.repository.LedgerEntryRepository;
import com.example.bank.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AccountService - Business logic for atomic banking operations.
 * Ensures all transactions are atomic, idempotent, and maintain double-entry bookkeeping.
 * Uses pessimistic locking and database transactions for consistency.
 */
@Slf4j
@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final IdempotencyService idempotencyService;
    private final LedgerService ledgerService;

    public AccountService(AccountRepository accountRepository,
                         TransactionRepository transactionRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         IdempotencyService idempotencyService,
                         LedgerService ledgerService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.idempotencyService = idempotencyService;
        this.ledgerService = ledgerService;
    }

    /**
     * Creates a new account with initial balance.
     * @param holderName Account holder name
     * @param initialBalance Initial balance (non-negative)
     * @param currency ISO 4217 currency code
     * @return Created account
     */
    public Account createAccount(String holderName, BigDecimal initialBalance, String currency) {
        log.info("Creating account for holder: {}", holderName);
        
        Account account = Account.builder()
            .holderName(holderName)
            .balance(new Money(initialBalance, currency))
            .status(Account.AccountStatus.ACTIVE)
            .build();

        account.validate();
        return accountRepository.save(account);
    }

    /**
     * Retrieves account by ID.
     * @param accountId Account UUID
     * @return Account if found
     * @throws AccountNotFoundException if account doesn't exist
     */
    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));
    }

    /**
     * Performs an atomic withdrawal from an account.
     * Enforces idempotency - same idempotency key returns same result.
     * 
     * @param accountId Account to withdraw from
     * @param amount Amount to withdraw
     * @param currency Currency code
     * @param idempotencyKey Unique idempotency key for this withdrawal
     * @param initiatedBy User/service initiating the withdrawal
     * @return Completed transaction
     * @throws AccountNotFoundException if account doesn't exist
     * @throws InsufficientFundsException if balance insufficient
     */
    public Transaction withdraw(UUID accountId, BigDecimal amount, String currency, 
                               UUID idempotencyKey, String initiatedBy) {
        log.info("Processing withdrawal: account={}, amount={}, idempotencyKey={}", 
                 accountId, amount, idempotencyKey);

        // Check idempotency - return existing transaction if already processed
        var existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            log.info("Idempotent withdrawal already processed: {}", idempotencyKey);
            return existingTx.get();
        }

        // Validate idempotency with Redis cache (for distributed systems)
        if (!idempotencyService.tryRegisterIdempotencyKey(idempotencyKey)) {
            throw new IllegalStateException("Idempotency key already in use: " + idempotencyKey);
        }

        try {
            // Create transaction in PENDING state
            Transaction transaction = Transaction.builder()
                .type(Transaction.TransactionType.WITHDRAWAL)
                .sourceAccountId(accountId)
                .status(Transaction.TransactionStatus.PENDING)
                .amount(amount)
                .currency(currency)
                .idempotencyKey(idempotencyKey)
                .initiatedBy(initiatedBy)
                .build();
            transaction.validate();

            Transaction savedTx = transactionRepository.save(transaction);

            // Acquire write lock on account and perform debit
            Account account = accountRepository.findByIdWithWriteLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

            Money withdrawalAmount = new Money(amount, currency);
            
            // Check balance invariant
            if (!account.getBalance().isGreaterThanOrEqual(withdrawalAmount)) {
                savedTx.markAsFailed();
                transactionRepository.save(savedTx);
                throw new InsufficientFundsException(
                    String.format("Insufficient funds for withdrawal. Required: %s %s, Available: %s %s",
                                 amount, currency, account.getBalance().getAmount(), account.getBalance().getCurrency())
                );
            }

            // Perform atomic debit
            account.debit(withdrawalAmount);
            accountRepository.save(account);

            // Create ledger entries (double-entry bookkeeping)
            // Debit: money leaves account
            ledgerService.recordDebit(savedTx.getId(), accountId, amount, currency);

            // Mark transaction as posted
            savedTx.markAsPosted();
            transactionRepository.save(savedTx);

            log.info("Withdrawal completed successfully: txId={}, amount={}", savedTx.getId(), amount);
            return savedTx;

        } catch (Exception ex) {
            log.error("Withdrawal failed: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Performs an atomic transfer between two accounts.
     * Ensures both accounts are debited/credited atomically with pessimistic locking.
     * 
     * @param sourceAccountId Account to transfer from
     * @param destinationAccountId Account to transfer to
     * @param amount Amount to transfer
     * @param currency Currency code
     * @param idempotencyKey Unique idempotency key
     * @param initiatedBy User/service initiating the transfer
     * @return Completed transaction
     * @throws AccountNotFoundException if either account doesn't exist
     * @throws InsufficientFundsException if source balance insufficient
     */
    public Transaction transfer(UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount, 
                               String currency, UUID idempotencyKey, String initiatedBy) {
        log.info("Processing transfer: from={}, to={}, amount={}, idempotencyKey={}", 
                 sourceAccountId, destinationAccountId, amount, idempotencyKey);

        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to same account");
        }

        // Check idempotency
        var existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            log.info("Idempotent transfer already processed: {}", idempotencyKey);
            return existingTx.get();
        }

        if (!idempotencyService.tryRegisterIdempotencyKey(idempotencyKey)) {
            throw new IllegalStateException("Idempotency key already in use: " + idempotencyKey);
        }

        try {
            // Create transaction in PENDING state
            Transaction transaction = Transaction.builder()
                .type(Transaction.TransactionType.TRANSFER)
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .status(Transaction.TransactionStatus.PENDING)
                .amount(amount)
                .currency(currency)
                .idempotencyKey(idempotencyKey)
                .initiatedBy(initiatedBy)
                .build();
            transaction.validate();

            Transaction savedTx = transactionRepository.save(transaction);

            // Acquire write locks on both accounts (ordered to prevent deadlock)
            UUID firstId = sourceAccountId.compareTo(destinationAccountId) < 0 ? sourceAccountId : destinationAccountId;
            UUID secondId = firstId.equals(sourceAccountId) ? destinationAccountId : sourceAccountId;

            Account account1 = accountRepository.findByIdWithWriteLock(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId.toString()));
            Account account2 = accountRepository.findByIdWithWriteLock(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId.toString()));

            Account sourceAccount = sourceAccountId.equals(account1.getId()) ? account1 : account2;
            Account destAccount = destinationAccountId.equals(account1.getId()) ? account1 : account2;

            Money transferAmount = new Money(amount, currency);

            // Verify source balance
            if (!sourceAccount.getBalance().isGreaterThanOrEqual(transferAmount)) {
                savedTx.markAsFailed();
                transactionRepository.save(savedTx);
                throw new InsufficientFundsException(
                    String.format("Insufficient funds for transfer. Required: %s %s, Available: %s %s",
                                 amount, currency, sourceAccount.getBalance().getAmount(), sourceAccount.getBalance().getCurrency())
                );
            }

            // Perform atomic debit/credit
            sourceAccount.debit(transferAmount);
            destAccount.credit(transferAmount);
            accountRepository.save(sourceAccount);
            accountRepository.save(destAccount);

            // Create ledger entries (double-entry bookkeeping)
            // Debit: money leaves source
            ledgerService.recordDebit(savedTx.getId(), sourceAccountId, amount, currency);

            // Credit: money enters destination
            ledgerService.recordCredit(savedTx.getId(), destinationAccountId, amount, currency);

            // Mark transaction as posted
            savedTx.markAsPosted();
            transactionRepository.save(savedTx);

            log.info("Transfer completed successfully: txId={}, from={}, to={}, amount={}", 
                    savedTx.getId(), sourceAccountId, destinationAccountId, amount);
            return savedTx;

        } catch (Exception ex) {
            log.error("Transfer failed: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Performs a deposit into an account.
     * 
     * @param accountId Account to deposit into
     * @param amount Amount to deposit
     * @param currency Currency code
     * @param idempotencyKey Unique idempotency key
     * @param initiatedBy User/service initiating the deposit
     * @return Completed transaction
     * @throws AccountNotFoundException if account doesn't exist
     */
    public Transaction deposit(UUID accountId, BigDecimal amount, String currency, 
                              UUID idempotencyKey, String initiatedBy) {
        log.info("Processing deposit: account={}, amount={}, idempotencyKey={}", 
                 accountId, amount, idempotencyKey);

        // Check idempotency
        var existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            log.info("Idempotent deposit already processed: {}", idempotencyKey);
            return existingTx.get();
        }

        if (!idempotencyService.tryRegisterIdempotencyKey(idempotencyKey)) {
            throw new IllegalStateException("Idempotency key already in use: " + idempotencyKey);
        }

        try {
            // Create transaction in PENDING state
            Transaction transaction = Transaction.builder()
                .type(Transaction.TransactionType.DEPOSIT)
                .destinationAccountId(accountId)
                .status(Transaction.TransactionStatus.PENDING)
                .amount(amount)
                .currency(currency)
                .idempotencyKey(idempotencyKey)
                .initiatedBy(initiatedBy)
                .build();
            transaction.validate();

            Transaction savedTx = transactionRepository.save(transaction);

            // Acquire write lock on account and perform credit
            Account account = accountRepository.findByIdWithWriteLock(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));

            Money depositAmount = new Money(amount, currency);
            account.credit(depositAmount);
            accountRepository.save(account);

            // Create ledger entry (credit entry for deposit)
            ledgerService.recordCredit(savedTx.getId(), accountId, amount, currency);

            // Mark transaction as posted
            savedTx.markAsPosted();
            transactionRepository.save(savedTx);

            log.info("Deposit completed successfully: txId={}, amount={}", savedTx.getId(), amount);
            return savedTx;

        } catch (Exception ex) {
            log.error("Deposit failed: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Retrieves transaction history for an account.
     * @param accountId Account UUID
     * @return List of transactions
     */
    public java.util.List<Transaction> getTransactionHistory(UUID accountId) {
        accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId.toString()));
        return transactionRepository.findAllByAccountId(accountId);
    }
}
