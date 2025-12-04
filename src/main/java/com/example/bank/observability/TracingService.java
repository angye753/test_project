package com.example.bank.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * TracingService - Provides distributed tracing using OpenTelemetry.
 * Tracks transaction flows across services and infrastructure.
 */
@Slf4j
@Service
public class TracingService {

    private final Tracer tracer;

    public TracingService(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer(
            "com.example.bank",
            "1.0.0"
        );
    }

    /**
     * Starts a span for a withdrawal operation.
     */
    public Span startWithdrawalSpan(UUID accountId, java.math.BigDecimal amount) {
        Span span = tracer.spanBuilder("bank.withdrawal")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        span.setAttribute("account.id", accountId.toString());
        span.setAttribute("transaction.amount", amount.doubleValue());
        span.setAttribute("transaction.type", "WITHDRAWAL");

        log.debug("Withdrawal span started: account={}, amount={}", accountId, amount);
        return span;
    }

    /**
     * Starts a span for a transfer operation.
     */
    public Span startTransferSpan(UUID sourceAccountId, UUID destinationAccountId, java.math.BigDecimal amount) {
        Span span = tracer.spanBuilder("bank.transfer")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        span.setAttribute("account.source.id", sourceAccountId.toString());
        span.setAttribute("account.destination.id", destinationAccountId.toString());
        span.setAttribute("transaction.amount", amount.doubleValue());
        span.setAttribute("transaction.type", "TRANSFER");

        log.debug("Transfer span started: from={}, to={}, amount={}", sourceAccountId, destinationAccountId, amount);
        return span;
    }

    /**
     * Starts a span for a deposit operation.
     */
    public Span startDepositSpan(UUID accountId, java.math.BigDecimal amount) {
        Span span = tracer.spanBuilder("bank.deposit")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        span.setAttribute("account.id", accountId.toString());
        span.setAttribute("transaction.amount", amount.doubleValue());
        span.setAttribute("transaction.type", "DEPOSIT");

        log.debug("Deposit span started: account={}, amount={}", accountId, amount);
        return span;
    }

    /**
     * Starts a span for account creation.
     */
    public Span startAccountCreationSpan(String holderName) {
        Span span = tracer.spanBuilder("bank.account.creation")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        span.setAttribute("account.holder_name", holderName);

        log.debug("Account creation span started: holder={}", holderName);
        return span;
    }

    /**
     * Starts a span for idempotency check.
     */
    public Span startIdempotencyCheckSpan(UUID idempotencyKey) {
        Span span = tracer.spanBuilder("bank.idempotency.check")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        span.setAttribute("idempotency.key", idempotencyKey.toString());

        return span;
    }

    /**
     * Starts a span for pessimistic locking.
     */
    public Span startLockingSpan(UUID accountId, String lockType) {
        Span span = tracer.spanBuilder("bank.locking")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        span.setAttribute("account.id", accountId.toString());
        span.setAttribute("lock.type", lockType); // READ_LOCK or WRITE_LOCK

        return span;
    }

    /**
     * Starts a span for ledger entry creation.
     */
    public Span startLedgerEntrySpan(UUID transactionId, String entryType) {
        Span span = tracer.spanBuilder("bank.ledger.entry")
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();

        span.setAttribute("transaction.id", transactionId.toString());
        span.setAttribute("ledger.entry.type", entryType); // DEBIT, CREDIT, or FEE

        return span;
    }

    /**
     * Adds an event to current span.
     */
    public void addEvent(Span span, String eventName, String details) {
        span.addEvent(eventName, io.opentelemetry.api.common.Attributes.builder()
            .put("details", details)
            .build()
        );

        log.debug("Span event added: {}: {}", eventName, details);
    }

    /**
     * Records an exception in the span.
     */
    public void recordException(Span span, Exception exception) {
        span.recordException(exception);
        span.setAttribute("exception.occurred", true);

        log.warn("Exception recorded in span: {}", exception.getMessage());
    }

    /**
     * Records success status for span.
     */
    public void recordSuccess(Span span) {
        span.setAttribute("operation.success", true);
        span.setAttribute("operation.status", "SUCCESS");
    }

    /**
     * Records failure status for span.
     */
    public void recordFailure(Span span, String reason) {
        span.setAttribute("operation.success", false);
        span.setAttribute("operation.status", "FAILURE");
        span.setAttribute("failure.reason", reason);

        log.warn("Operation failed: {}", reason);
    }

    /**
     * Ends the span.
     */
    public void endSpan(Span span) {
        span.end();
    }

    /**
     * Executes a callback within a span context.
     * Useful for automatic span management.
     */
    public <T> T executeWithSpan(String spanName, SpanCallback<T> callback) {
        Span span = tracer.spanBuilder(spanName).startSpan();

        try (Scope scope = span.makeCurrent()) {
            T result = callback.execute(span);
            recordSuccess(span);
            return result;
        } catch (Exception ex) {
            recordException(span, ex);
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException(ex);
        } finally {
            span.end();
        }
    }

    /**
     * Functional interface for span callbacks.
     */
    @FunctionalInterface
    public interface SpanCallback<T> {
        T execute(Span span) throws Exception;
    }
}
