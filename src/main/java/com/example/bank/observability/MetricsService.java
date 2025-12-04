package com.example.bank.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MetricsService - Tracks business metrics for Prometheus monitoring.
 * Provides counters and timers for transaction operations and account activities.
 */
@Slf4j
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;

    // Counters
    private final Counter transactionCounter;
    private final Counter withdrawalCounter;
    private final Counter depositCounter;
    private final Counter transferCounter;
    private final Counter transactionSuccessCounter;
    private final Counter transactionFailureCounter;
    private final Counter insufficientFundsCounter;
    private final Counter idempotencyKeyDuplicateCounter;

    // Timers
    private final Timer withdrawalTimer;
    private final Timer depositTimer;
    private final Timer transferTimer;
    private final Timer accountCreationTimer;

    // Gauges (for active counts)
    private final AtomicLong activePendingTransactions;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Initialize counters
        this.transactionCounter = Counter.builder("bank.transactions.total")
            .description("Total number of transactions processed")
            .register(meterRegistry);

        this.withdrawalCounter = Counter.builder("bank.transactions.withdrawal.total")
            .description("Total number of withdrawals")
            .register(meterRegistry);

        this.depositCounter = Counter.builder("bank.transactions.deposit.total")
            .description("Total number of deposits")
            .register(meterRegistry);

        this.transferCounter = Counter.builder("bank.transactions.transfer.total")
            .description("Total number of transfers")
            .register(meterRegistry);

        this.transactionSuccessCounter = Counter.builder("bank.transactions.success.total")
            .description("Total successful transactions")
            .register(meterRegistry);

        this.transactionFailureCounter = Counter.builder("bank.transactions.failure.total")
            .description("Total failed transactions")
            .register(meterRegistry);

        this.insufficientFundsCounter = Counter.builder("bank.transactions.insufficient_funds.total")
            .description("Total transactions rejected due to insufficient funds")
            .register(meterRegistry);

        this.idempotencyKeyDuplicateCounter = Counter.builder("bank.transactions.idempotency_duplicate.total")
            .description("Total duplicate transactions detected by idempotency key")
            .register(meterRegistry);

        // Initialize timers
        this.withdrawalTimer = Timer.builder("bank.transactions.withdrawal.duration")
            .description("Time taken to process withdrawals")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        this.depositTimer = Timer.builder("bank.transactions.deposit.duration")
            .description("Time taken to process deposits")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        this.transferTimer = Timer.builder("bank.transactions.transfer.duration")
            .description("Time taken to process transfers")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        this.accountCreationTimer = Timer.builder("bank.accounts.creation.duration")
            .description("Time taken to create accounts")
            .register(meterRegistry);

        // Initialize gauges
        this.activePendingTransactions = meterRegistry.gauge(
            "bank.transactions.pending.active",
            new AtomicLong(0)
        );
    }

    // ========== Transaction Counters ==========

    public void recordTransaction(String type) {
        transactionCounter.increment();
        switch (type.toUpperCase()) {
            case "WITHDRAWAL" -> withdrawalCounter.increment();
            case "DEPOSIT" -> depositCounter.increment();
            case "TRANSFER" -> transferCounter.increment();
        }
        log.debug("Transaction recorded: {}", type);
    }

    public void recordTransactionSuccess() {
        transactionSuccessCounter.increment();
    }

    public void recordTransactionFailure() {
        transactionFailureCounter.increment();
    }

    public void recordInsufficientFundsError() {
        insufficientFundsCounter.increment();
        log.warn("Insufficient funds error recorded");
    }

    public void recordIdempotencyDuplicate() {
        idempotencyKeyDuplicateCounter.increment();
        log.debug("Idempotency duplicate detected");
    }

    // ========== Transaction Timers ==========

    public Timer.Sample startWithdrawalTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordWithdrawalTime(Timer.Sample sample) {
        sample.stop(withdrawalTimer);
    }

    public Timer.Sample startDepositTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordDepositTime(Timer.Sample sample) {
        sample.stop(depositTimer);
    }

    public Timer.Sample startTransferTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordTransferTime(Timer.Sample sample) {
        sample.stop(transferTimer);
    }

    public Timer.Sample startAccountCreationTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordAccountCreationTime(Timer.Sample sample) {
        sample.stop(accountCreationTimer);
    }

    // ========== Gauge Operations ==========

    public void incrementPendingTransactions() {
        activePendingTransactions.incrementAndGet();
    }

    public void decrementPendingTransactions() {
        long current = activePendingTransactions.decrementAndGet();
        if (current < 0) {
            activePendingTransactions.set(0);
        }
    }

    public long getPendingTransactionCount() {
        return activePendingTransactions.get();
    }

    // ========== Custom Metrics ==========

    /**
     * Records account balance change for monitoring.
     * Useful for detecting anomalies.
     */
    public void recordBalanceChange(UUID accountId, java.math.BigDecimal oldBalance, java.math.BigDecimal newBalance) {
        java.math.BigDecimal change = newBalance.subtract(oldBalance);
        java.util.concurrent.atomic.AtomicReference<java.math.BigDecimal> balanceRef = 
            new java.util.concurrent.atomic.AtomicReference<>(change);
        
        meterRegistry.gauge(
            "bank.accounts.balance.change",
            io.micrometer.core.instrument.Tags.of(
                "accountId", accountId.toString(),
                "direction", change.signum() > 0 ? "increase" : "decrease"
            ),
            balanceRef,
            ref -> ref.get().doubleValue()
        );
        log.debug("Balance change recorded: account={}, change={}", accountId, change);
    }

    /**
     * Records concurrent transaction count for an account.
     */
    public void recordConcurrentTransactions(UUID accountId, long count) {
        java.util.concurrent.atomic.AtomicLong countRef = new java.util.concurrent.atomic.AtomicLong(count);
        meterRegistry.gauge(
            "bank.accounts.concurrent_transactions",
            io.micrometer.core.instrument.Tags.of("accountId", accountId.toString()),
            countRef,
            java.util.concurrent.atomic.AtomicLong::doubleValue
        );
        log.debug("Concurrent transactions recorded: account={}, count={}", accountId, count);
    }
}
