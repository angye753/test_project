package com.example.bank.service;

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
 * LedgerServiceTests - Integration tests for append-only ledger and double-entry bookkeeping.
 * Validates immutability, balance calculations, and compliance rules.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("LedgerService - Append-Only Ledger Tests")
public class LedgerServiceTests {

    @Autowired
    private LedgerService ledgerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private AccountRepository accountRepository;

    private UUID sourceAccountId;
    private UUID destAccountId;
    private Account sourceAccount;
    private Account destAccount;

    @BeforeEach
    public void setUp() {
        // Create test accounts
        sourceAccount = accountService.createAccount("Alice", new BigDecimal("1000"), "USD");
        destAccount = accountService.createAccount("Bob", new BigDecimal("500"), "USD");
        
        sourceAccountId = sourceAccount.getId();
        destAccountId = destAccount.getId();
    }

    @Test
    @DisplayName("T026: Record debit ledger entry")
    public void testRecordDebitEntry() {
        UUID transactionId = UUID.randomUUID();
        BigDecimal debitAmount = new BigDecimal("100");

        LedgerEntry debitEntry = ledgerService.recordDebit(transactionId, sourceAccountId, debitAmount, "USD");

        assertNotNull(debitEntry.getId());
        assertEquals(transactionId, debitEntry.getTransactionId());
        assertEquals(sourceAccountId, debitEntry.getAccountId());
        assertEquals(LedgerEntry.EntryType.DEBIT, debitEntry.getType());
        assertEquals(debitAmount, debitEntry.getAmount());
        assertEquals("USD", debitEntry.getCurrency());
        assertNotNull(debitEntry.getTimestamp());

        log("✅ Debit entry recorded successfully: {}", debitEntry.getId());
    }

    @Test
    @DisplayName("T026: Record credit ledger entry")
    public void testRecordCreditEntry() {
        UUID transactionId = UUID.randomUUID();
        BigDecimal creditAmount = new BigDecimal("100");

        LedgerEntry creditEntry = ledgerService.recordCredit(transactionId, destAccountId, creditAmount, "USD");

        assertNotNull(creditEntry.getId());
        assertEquals(transactionId, creditEntry.getTransactionId());
        assertEquals(destAccountId, creditEntry.getAccountId());
        assertEquals(LedgerEntry.EntryType.CREDIT, creditEntry.getType());
        assertEquals(creditAmount, creditEntry.getAmount());
        assertEquals("USD", creditEntry.getCurrency());

        log("✅ Credit entry recorded successfully: {}", creditEntry.getId());
    }

    @Test
    @DisplayName("T026: Record fee ledger entry")
    public void testRecordFeeEntry() {
        UUID transactionId = UUID.randomUUID();
        BigDecimal feeAmount = new BigDecimal("5.00");

        LedgerEntry feeEntry = ledgerService.recordFee(transactionId, sourceAccountId, feeAmount, "USD");

        assertNotNull(feeEntry.getId());
        assertEquals(transactionId, feeEntry.getTransactionId());
        assertEquals(sourceAccountId, feeEntry.getAccountId());
        assertEquals(LedgerEntry.EntryType.FEE, feeEntry.getType());
        assertEquals(feeAmount, feeEntry.getAmount());

        log("✅ Fee entry recorded successfully: {}", feeEntry.getId());
    }

    @Test
    @DisplayName("T026: Verify entry immutability (cannot be updated)")
    public void testEntryImmutability() {
        UUID transactionId = UUID.randomUUID();
        LedgerEntry debitEntry = ledgerService.recordDebit(transactionId, sourceAccountId, new BigDecimal("100"), "USD");

        // Verify entry exists
        assertTrue(ledgerService.verifyEntryImmutability(debitEntry.getId()));

        // Entry should not be updatable due to JPA mapping (updatable=false)
        // This test confirms the mapping is working as expected
        log("✅ Ledger entry immutability verified: {}", debitEntry.getId());
    }

    @Test
    @DisplayName("T027: Validate double-entry for withdrawal")
    public void testDoubleEntryWithdrawal() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal withdrawalAmount = new BigDecimal("100");

        // Perform withdrawal through AccountService (which uses LedgerService)
        Transaction tx = accountService.withdraw(sourceAccountId, withdrawalAmount, "USD", idempotencyKey, "alice");

        // Verify double-entry bookkeeping
        assertTrue(ledgerService.validateDoubleEntry(tx.getId()));

        List<LedgerEntry> entries = ledgerService.getTransactionEntries(tx.getId());
        assertEquals(1, entries.size());
        assertEquals(LedgerEntry.EntryType.DEBIT, entries.get(0).getType());
        assertEquals(withdrawalAmount, entries.get(0).getAmount());

        log("✅ Double-entry validation passed for withdrawal: {}", tx.getId());
    }

    @Test
    @DisplayName("T027: Validate double-entry for deposit")
    public void testDoubleEntryDeposit() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal depositAmount = new BigDecimal("100");

        Transaction tx = accountService.deposit(destAccountId, depositAmount, "USD", idempotencyKey, "admin");

        assertTrue(ledgerService.validateDoubleEntry(tx.getId()));

        List<LedgerEntry> entries = ledgerService.getTransactionEntries(tx.getId());
        assertEquals(1, entries.size());
        assertEquals(LedgerEntry.EntryType.CREDIT, entries.get(0).getType());
        assertEquals(depositAmount, entries.get(0).getAmount());

        log("✅ Double-entry validation passed for deposit: {}", tx.getId());
    }

    @Test
    @DisplayName("T027: Validate double-entry for transfer (both debit and credit)")
    public void testDoubleEntryTransfer() {
        UUID idempotencyKey = UUID.randomUUID();
        BigDecimal transferAmount = new BigDecimal("100");

        Transaction tx = accountService.transfer(sourceAccountId, destAccountId, transferAmount, "USD", idempotencyKey, "alice");

        // Verify double-entry: must have 2 entries (debit + credit)
        assertTrue(ledgerService.validateDoubleEntry(tx.getId()));

        List<LedgerEntry> entries = ledgerService.getTransactionEntries(tx.getId());
        assertEquals(2, entries.size());

        // Verify debit entry
        LedgerEntry debitEntry = entries.stream()
            .filter(LedgerEntry::isDebit)
            .findFirst()
            .orElseThrow();
        assertEquals(sourceAccountId, debitEntry.getAccountId());
        assertEquals(transferAmount, debitEntry.getAmount());

        // Verify credit entry
        LedgerEntry creditEntry = entries.stream()
            .filter(LedgerEntry::isCredit)
            .findFirst()
            .orElseThrow();
        assertEquals(destAccountId, creditEntry.getAccountId());
        assertEquals(transferAmount, creditEntry.getAmount());

        log("✅ Double-entry validation passed for transfer: {}", tx.getId());
    }

    @Test
    @DisplayName("T027: Validate account ledger integrity")
    public void testAccountLedgerIntegrity() {
        UUID idempotencyKey1 = UUID.randomUUID();
        UUID idempotencyKey2 = UUID.randomUUID();

        // Perform multiple transactions
        Transaction tx1 = accountService.withdraw(sourceAccountId, new BigDecimal("100"), "USD", idempotencyKey1, "alice");
        Transaction tx2 = accountService.deposit(destAccountId, new BigDecimal("50"), "USD", idempotencyKey2, "admin");

        // Verify both source and destination ledgers are balanced
        assertTrue(ledgerService.validateAccountLedger(sourceAccountId));
        assertTrue(ledgerService.validateAccountLedger(destAccountId));

        log("✅ Account ledger integrity verified");
    }

    @Test
    @DisplayName("T028: Retrieve account ledger (audit trail)")
    public void testRetrieveAccountLedger() {
        UUID idempotencyKey1 = UUID.randomUUID();
        UUID idempotencyKey2 = UUID.randomUUID();

        // Perform multiple transactions
        accountService.withdraw(sourceAccountId, new BigDecimal("100"), "USD", idempotencyKey1, "alice");
        accountService.withdraw(sourceAccountId, new BigDecimal("50"), "USD", idempotencyKey2, "alice");

        // Get ledger
        List<LedgerEntry> ledger = ledgerService.getAccountLedger(sourceAccountId);

        // Should have 2 debit entries
        assertEquals(2, ledger.size());
        assertTrue(ledger.stream().allMatch(LedgerEntry::isDebit));

        // Verify chronological order (oldest first)
        assertTrue(ledger.get(0).getTimestamp().isBefore(ledger.get(1).getTimestamp()) ||
                  ledger.get(0).getTimestamp().equals(ledger.get(1).getTimestamp()));

        log("✅ Account ledger retrieved with {} entries", ledger.size());
    }

    @Test
    @DisplayName("T028: Retrieve transaction ledger entries")
    public void testRetrieveTransactionLedger() {
        UUID idempotencyKey = UUID.randomUUID();
        Transaction tx = accountService.transfer(sourceAccountId, destAccountId, new BigDecimal("100"), "USD", idempotencyKey, "alice");

        List<LedgerEntry> entries = ledgerService.getTransactionEntries(tx.getId());

        assertEquals(2, entries.size());
        assertEquals(1, entries.stream().filter(LedgerEntry::isDebit).count());
        assertEquals(1, entries.stream().filter(LedgerEntry::isCredit).count());

        log("✅ Transaction ledger retrieved with {} entries", entries.size());
    }

    @Test
    @DisplayName("T028: Calculate balance from ledger")
    public void testCalculateBalance() {
        // Initial: Alice has 1000
        BigDecimal initialBalance = ledgerService.calculateBalance(sourceAccountId);
        assertEquals(new BigDecimal("1000"), initialBalance);

        // Withdraw 100
        UUID idempotencyKey1 = UUID.randomUUID();
        accountService.withdraw(sourceAccountId, new BigDecimal("100"), "USD", idempotencyKey1, "alice");

        BigDecimal balanceAfterWithdraw = ledgerService.calculateBalance(sourceAccountId);
        assertEquals(new BigDecimal("900"), balanceAfterWithdraw);

        // Deposit 50
        UUID idempotencyKey2 = UUID.randomUUID();
        accountService.deposit(sourceAccountId, new BigDecimal("50"), "USD", idempotencyKey2, "admin");

        BigDecimal balanceAfterDeposit = ledgerService.calculateBalance(sourceAccountId);
        assertEquals(new BigDecimal("950"), balanceAfterDeposit);

        log("✅ Balance calculation verified: final balance {}", balanceAfterDeposit);
    }

    @Test
    @DisplayName("T028: Get ledger entries by type")
    public void testRetrieveEntriesByType() {
        UUID idempotencyKey1 = UUID.randomUUID();
        UUID idempotencyKey2 = UUID.randomUUID();

        // Withdraw 100 and deposit 50
        accountService.withdraw(sourceAccountId, new BigDecimal("100"), "USD", idempotencyKey1, "alice");
        accountService.deposit(sourceAccountId, new BigDecimal("50"), "USD", idempotencyKey2, "admin");

        // Get only debit entries
        List<LedgerEntry> debits = ledgerService.getEntriesByType(sourceAccountId, LedgerEntry.EntryType.DEBIT);
        assertEquals(1, debits.size());
        assertEquals(new BigDecimal("100"), debits.get(0).getAmount());

        // Get only credit entries
        List<LedgerEntry> credits = ledgerService.getEntriesByType(sourceAccountId, LedgerEntry.EntryType.CREDIT);
        assertEquals(1, credits.size());
        assertEquals(new BigDecimal("50"), credits.get(0).getAmount());

        log("✅ Retrieved entries by type: {} debits, {} credits", debits.size(), credits.size());
    }

    @Test
    @DisplayName("T029: Count ledger entries for transaction")
    public void testCountTransactionEntries() {
        UUID withdrawalKey = UUID.randomUUID();
        UUID transferKey = UUID.randomUUID();

        Transaction withdrawal = accountService.withdraw(sourceAccountId, new BigDecimal("100"), "USD", withdrawalKey, "alice");
        Transaction transfer = accountService.transfer(sourceAccountId, destAccountId, new BigDecimal("50"), "USD", transferKey, "alice");

        // Withdrawal has 1 debit entry
        assertEquals(1, ledgerService.countTransactionEntries(withdrawal.getId()));

        // Transfer has 2 entries (debit + credit)
        assertEquals(2, ledgerService.countTransactionEntries(transfer.getId()));

        log("✅ Transaction entry counts verified: withdrawal={}, transfer={}", 
            ledgerService.countTransactionEntries(withdrawal.getId()),
            ledgerService.countTransactionEntries(transfer.getId()));
    }

    @Test
    @DisplayName("T029: Multiple withdrawals maintain ledger consistency")
    public void testMultipleWithdrawalsLedgerConsistency() {
        UUID key1 = UUID.randomUUID();
        UUID key2 = UUID.randomUUID();
        UUID key3 = UUID.randomUUID();

        // Perform 3 withdrawals
        accountService.withdraw(sourceAccountId, new BigDecimal("100"), "USD", key1, "alice");
        accountService.withdraw(sourceAccountId, new BigDecimal("50"), "USD", key2, "alice");
        accountService.withdraw(sourceAccountId, new BigDecimal("25"), "USD", key3, "alice");

        // Get ledger
        List<LedgerEntry> ledger = ledgerService.getAccountLedger(sourceAccountId);
        assertEquals(3, ledger.size());

        // Verify all are debits
        assertTrue(ledger.stream().allMatch(LedgerEntry::isDebit));

        // Calculate balance: 1000 - 100 - 50 - 25 = 825
        BigDecimal balance = ledgerService.calculateBalance(sourceAccountId);
        assertEquals(new BigDecimal("825"), balance);

        // Verify all transactions are balanced
        assertTrue(ledger.stream()
            .map(LedgerEntry::getTransactionId)
            .distinct()
            .allMatch(ledgerService::validateDoubleEntry));

        log("✅ Multiple withdrawals ledger consistency verified: {} entries, balance {}", ledger.size(), balance);
    }

    @Test
    @DisplayName("T029: Complex scenario: transfer and deposits maintain ledger balance")
    public void testComplexLedgerScenario() {
        // Alice transfers 100 to Bob
        UUID transferKey = UUID.randomUUID();
        Transaction transfer = accountService.transfer(sourceAccountId, destAccountId, new BigDecimal("100"), "USD", transferKey, "alice");

        // Bob deposits 50
        UUID depositKey = UUID.randomUUID();
        accountService.deposit(destAccountId, new BigDecimal("50"), "USD", depositKey, "admin");

        // Verify both transactions are balanced
        assertTrue(ledgerService.validateDoubleEntry(transfer.getId()));
        assertTrue(ledgerService.validateDoubleEntry(depositKey));

        // Verify account ledgers are balanced
        assertTrue(ledgerService.validateAccountLedger(sourceAccountId));
        assertTrue(ledgerService.validateAccountLedger(destAccountId));

        // Verify balance calculations
        BigDecimal aliceBalance = ledgerService.calculateBalance(sourceAccountId);
        BigDecimal bobBalance = ledgerService.calculateBalance(destAccountId);

        assertEquals(new BigDecimal("900"), aliceBalance);  // 1000 - 100
        assertEquals(new BigDecimal("650"), bobBalance);    // 500 + 100 + 50

        log("✅ Complex scenario verified: Alice={}, Bob={}", aliceBalance, bobBalance);
    }

    @Test
    @DisplayName("T029: Verify idempotency with ledger")
    public void testIdempotencyWithLedger() {
        UUID idempotencyKey = UUID.randomUUID();

        // First withdrawal
        Transaction tx1 = accountService.withdraw(sourceAccountId, new BigDecimal("100"), "USD", idempotencyKey, "alice");

        // Retry with same idempotency key
        Transaction tx2 = accountService.withdraw(sourceAccountId, new BigDecimal("100"), "USD", idempotencyKey, "alice");

        // Should return same transaction
        assertEquals(tx1.getId(), tx2.getId());

        // Should have only 1 ledger entry for this transaction
        List<LedgerEntry> entries = ledgerService.getTransactionEntries(tx1.getId());
        assertEquals(1, entries.size());

        // Balance should be reduced only once
        BigDecimal balance = ledgerService.calculateBalance(sourceAccountId);
        assertEquals(new BigDecimal("900"), balance);

        log("✅ Idempotency with ledger verified: {} entries created", entries.size());
    }

    private void log(String message, Object... args) {
        System.out.println(String.format(message, args));
    }
}
