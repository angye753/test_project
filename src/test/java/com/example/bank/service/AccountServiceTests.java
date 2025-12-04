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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AccountService.
 * Tests atomic operations, idempotency, balance invariants, and double-entry bookkeeping.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountServiceTests {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    private UUID testAccountId;
    private UUID secondAccountId;

    @BeforeEach
    void setUp() {
        // Create test accounts
        Account account1 = accountService.createAccount("Test User 1", BigDecimal.valueOf(10000), "USD");
        testAccountId = account1.getId();

        Account account2 = accountService.createAccount("Test User 2", BigDecimal.valueOf(5000), "USD");
        secondAccountId = account2.getId();
    }

    @Test
    @DisplayName("Should create account with initial balance")
    void testCreateAccount() {
        Account account = accountService.createAccount("John Doe", BigDecimal.valueOf(1000), "USD");

        assertNotNull(account.getId());
        assertEquals("John Doe", account.getHolderName());
        assertEquals(BigDecimal.valueOf(1000), account.getBalance().getAmount());
        assertEquals("USD", account.getBalance().getCurrency());
        assertEquals(Account.AccountStatus.ACTIVE, account.getStatus());
    }

    @Test
    @DisplayName("Should retrieve account by ID")
    void testGetAccount() {
        Account account = accountService.getAccount(testAccountId);

        assertNotNull(account);
        assertEquals(testAccountId, account.getId());
        assertEquals("Test User 1", account.getHolderName());
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException for non-existent account")
    void testGetAccountNotFound() {
        UUID nonExistentId = UUID.randomUUID();

        assertThrows(AccountNotFoundException.class, () -> accountService.getAccount(nonExistentId));
    }

    @Test
    @DisplayName("Should perform atomic withdrawal")
    void testWithdraw() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal withdrawAmount = BigDecimal.valueOf(1000);

        Transaction tx = accountService.withdraw(
            testAccountId,
            withdrawAmount,
            "USD",
            idempotencyKey,
            "test-user"
        );

        assertNotNull(tx);
        assertEquals(Transaction.TransactionStatus.POSTED, tx.getStatus());
        assertEquals(Transaction.TransactionType.WITHDRAWAL, tx.getType());

        // Verify account balance decreased
        Account account = accountService.getAccount(testAccountId);
        assertEquals(BigDecimal.valueOf(9000), account.getBalance().getAmount());

        // Verify ledger entry created
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx.getId());
        assertEquals(1, entries.size());
        assertEquals(LedgerEntry.EntryType.DEBIT, entries.get(0).getType());
    }

    @Test
    @DisplayName("Should enforce insufficient funds invariant")
    void testWithdrawInsufficientFunds() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal withdrawAmount = BigDecimal.valueOf(20000); // More than balance

        assertThrows(InsufficientFundsException.class, () ->
            accountService.withdraw(testAccountId, withdrawAmount, "USD", idempotencyKey, "test-user")
        );

        // Verify balance unchanged
        Account account = accountService.getAccount(testAccountId);
        assertEquals(BigDecimal.valueOf(10000), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("Should enforce idempotency for withdrawals")
    void testWithdrawIdempotency() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal withdrawAmount = BigDecimal.valueOf(1000);

        // First withdrawal
        Transaction tx1 = accountService.withdraw(
            testAccountId,
            withdrawAmount,
            "USD",
            idempotencyKey,
            "test-user"
        );

        // Second withdrawal with same idempotency key - should return same transaction
        Transaction tx2 = accountService.withdraw(
            testAccountId,
            withdrawAmount,
            "USD",
            idempotencyKey,
            "test-user"
        );

        assertEquals(tx1.getId(), tx2.getId());

        // Verify account balance decreased only once
        Account account = accountService.getAccount(testAccountId);
        assertEquals(BigDecimal.valueOf(9000), account.getBalance().getAmount());

        // Verify only one ledger entry created
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx1.getId());
        assertEquals(1, entries.size());
    }

    @Test
    @DisplayName("Should perform atomic transfer between accounts")
    void testTransfer() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal transferAmount = BigDecimal.valueOf(2000);

        Transaction tx = accountService.transfer(
            testAccountId,
            secondAccountId,
            transferAmount,
            "USD",
            idempotencyKey,
            "test-user"
        );

        assertNotNull(tx);
        assertEquals(Transaction.TransactionStatus.POSTED, tx.getStatus());
        assertEquals(Transaction.TransactionType.TRANSFER, tx.getType());

        // Verify source account balance decreased
        Account sourceAccount = accountService.getAccount(testAccountId);
        assertEquals(BigDecimal.valueOf(8000), sourceAccount.getBalance().getAmount());

        // Verify destination account balance increased
        Account destAccount = accountService.getAccount(secondAccountId);
        assertEquals(BigDecimal.valueOf(7000), destAccount.getBalance().getAmount());

        // Verify double-entry bookkeeping: 2 ledger entries (debit & credit)
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx.getId());
        assertEquals(2, entries.size());

        LedgerEntry debit = entries.stream()
            .filter(e -> e.getType() == LedgerEntry.EntryType.DEBIT)
            .findFirst()
            .orElseThrow();
        LedgerEntry credit = entries.stream()
            .filter(e -> e.getType() == LedgerEntry.EntryType.CREDIT)
            .findFirst()
            .orElseThrow();

        assertEquals(testAccountId, debit.getAccountId());
        assertEquals(secondAccountId, credit.getAccountId());
        assertEquals(transferAmount, debit.getAmount());
        assertEquals(transferAmount, credit.getAmount());
    }

    @Test
    @DisplayName("Should enforce insufficient funds for transfer")
    void testTransferInsufficientFunds() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal transferAmount = BigDecimal.valueOf(20000); // More than balance

        assertThrows(InsufficientFundsException.class, () ->
            accountService.transfer(testAccountId, secondAccountId, transferAmount, "USD", idempotencyKey, "test-user")
        );

        // Verify balances unchanged
        Account sourceAccount = accountService.getAccount(testAccountId);
        Account destAccount = accountService.getAccount(secondAccountId);
        assertEquals(BigDecimal.valueOf(10000), sourceAccount.getBalance().getAmount());
        assertEquals(BigDecimal.valueOf(5000), destAccount.getBalance().getAmount());
    }

    @Test
    @DisplayName("Should enforce idempotency for transfers")
    void testTransferIdempotency() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal transferAmount = BigDecimal.valueOf(1000);

        // First transfer
        Transaction tx1 = accountService.transfer(
            testAccountId,
            secondAccountId,
            transferAmount,
            "USD",
            idempotencyKey,
            "test-user"
        );

        // Second transfer with same idempotency key
        Transaction tx2 = accountService.transfer(
            testAccountId,
            secondAccountId,
            transferAmount,
            "USD",
            idempotencyKey,
            "test-user"
        );

        assertEquals(tx1.getId(), tx2.getId());

        // Verify balances changed only once
        Account sourceAccount = accountService.getAccount(testAccountId);
        Account destAccount = accountService.getAccount(secondAccountId);
        assertEquals(BigDecimal.valueOf(9000), sourceAccount.getBalance().getAmount());
        assertEquals(BigDecimal.valueOf(6000), destAccount.getBalance().getAmount());
    }

    @Test
    @DisplayName("Should perform atomic deposit")
    void testDeposit() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal depositAmount = BigDecimal.valueOf(500);

        Transaction tx = accountService.deposit(
            testAccountId,
            depositAmount,
            "USD",
            idempotencyKey,
            "bank-system"
        );

        assertNotNull(tx);
        assertEquals(Transaction.TransactionStatus.POSTED, tx.getStatus());
        assertEquals(Transaction.TransactionType.DEPOSIT, tx.getType());

        // Verify account balance increased
        Account account = accountService.getAccount(testAccountId);
        assertEquals(BigDecimal.valueOf(10500), account.getBalance().getAmount());

        // Verify ledger entry created
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(tx.getId());
        assertEquals(1, entries.size());
        assertEquals(LedgerEntry.EntryType.CREDIT, entries.get(0).getType());
    }

    @Test
    @DisplayName("Should enforce idempotency for deposits")
    void testDepositIdempotency() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal depositAmount = BigDecimal.valueOf(500);

        // First deposit
        Transaction tx1 = accountService.deposit(
            testAccountId,
            depositAmount,
            "USD",
            idempotencyKey,
            "bank-system"
        );

        // Second deposit with same idempotency key
        Transaction tx2 = accountService.deposit(
            testAccountId,
            depositAmount,
            "USD",
            idempotencyKey,
            "bank-system"
        );

        assertEquals(tx1.getId(), tx2.getId());

        // Verify balance increased only once
        Account account = accountService.getAccount(testAccountId);
        assertEquals(BigDecimal.valueOf(10500), account.getBalance().getAmount());
    }

    @Test
    @DisplayName("Should maintain double-entry bookkeeping across multiple transactions")
    void testDoubleEntryBookkeeping() {
        // Perform multiple transactions
        UUID key1 = UUID.randomUUID();
        accountService.withdraw(testAccountId, BigDecimal.valueOf(1000), "USD", key1, "user1");

        UUID key2 = UUID.randomUUID();
        accountService.transfer(testAccountId, secondAccountId, BigDecimal.valueOf(2000), "USD", key2, "user1");

        UUID key3 = UUID.randomUUID();
        accountService.deposit(testAccountId, BigDecimal.valueOf(500), "USD", key3, "system");

        // Verify ledger integrity
        List<LedgerEntry> account1Entries = ledgerEntryRepository.findByAccountIdOrderByTimestampAsc(testAccountId);
        List<LedgerEntry> account2Entries = ledgerEntryRepository.findByAccountIdOrderByTimestampAsc(secondAccountId);

        // Account 1: 1 debit (withdraw) + 1 debit (transfer) + 1 credit (deposit) = 3 entries
        assertEquals(3, account1Entries.size());

        // Account 2: 1 credit (transfer in) = 1 entry
        assertEquals(1, account2Entries.size());

        // Verify final balance consistency
        Account account1 = accountService.getAccount(testAccountId);
        Account account2 = accountService.getAccount(secondAccountId);

        // Account 1: 10000 - 1000 - 2000 + 500 = 7500
        assertEquals(BigDecimal.valueOf(7500), account1.getBalance().getAmount());

        // Account 2: 5000 + 2000 = 7000
        assertEquals(BigDecimal.valueOf(7000), account2.getBalance().getAmount());
    }

    @Test
    @DisplayName("Should retrieve transaction history for account")
    void testGetTransactionHistory() {
        UUID key1 = UUID.randomUUID();
        accountService.withdraw(testAccountId, BigDecimal.valueOf(1000), "USD", key1, "user1");

        UUID key2 = UUID.randomUUID();
        accountService.transfer(testAccountId, secondAccountId, BigDecimal.valueOf(500), "USD", key2, "user1");

        List<Transaction> history = accountService.getTransactionHistory(testAccountId);

        assertEquals(2, history.size());
        assertTrue(history.stream().anyMatch(t -> t.getType() == Transaction.TransactionType.WITHDRAWAL));
        assertTrue(history.stream().anyMatch(t -> t.getType() == Transaction.TransactionType.TRANSFER));
    }
}
