# Observability Guide — Metrics and Tracing

## Overview

This microservice provides comprehensive observability through:
- **Prometheus metrics** for monitoring and alerting
- **OpenTelemetry tracing** for distributed tracing across services
- **Structured logging** at all operational levels

---

## Prometheus Metrics

### Accessing Metrics

Metrics are exposed at: `http://localhost:8080/api/actuator/prometheus`

### Transaction Counters

**Total Transactions**
```
bank_transactions_total{} (counter)
```
Total number of all transactions processed.

**Transaction Types**
```
bank_transactions_withdrawal_total{}
bank_transactions_deposit_total{}
bank_transactions_transfer_total{}
```
Count of each transaction type.

**Transaction Status**
```
bank_transactions_success_total{}
bank_transactions_failure_total{}
```
Count of successful and failed transactions.

**Error Counters**
```
bank_transactions_insufficient_funds_total{}
```
Count of transactions rejected due to insufficient funds.

```
bank_transactions_idempotency_duplicate_total{}
```
Count of duplicate requests detected by idempotency key.

### Transaction Timers

**Withdrawal Duration**
```
bank_transactions_withdrawal_duration{quantile="0.5"} (milliseconds, p50)
bank_transactions_withdrawal_duration{quantile="0.95"} (milliseconds, p95)
bank_transactions_withdrawal_duration{quantile="0.99"} (milliseconds, p99)
```

**Deposit Duration**
```
bank_transactions_deposit_duration{quantile="0.5"}
bank_transactions_deposit_duration{quantile="0.95"}
bank_transactions_deposit_duration{quantile="0.99"}
```

**Transfer Duration**
```
bank_transactions_transfer_duration{quantile="0.5"}
bank_transactions_transfer_duration{quantile="0.95"}
bank_transactions_transfer_duration{quantile="0.99"}
```

**Account Creation Duration**
```
bank_accounts_creation_duration{}
```

### Gauges

**Active Pending Transactions**
```
bank_transactions_pending_active{}
```
Current count of transactions in PENDING state.

**Balance Changes**
```
bank_accounts_balance_change{accountId="...",direction="increase"|"decrease"}
```
Track balance modifications for anomaly detection.

**Concurrent Transactions**
```
bank_accounts_concurrent_transactions{accountId="..."}
```
Active transaction count per account.

---

## Example Prometheus Queries

### Transaction Rate (per minute)
```promql
rate(bank_transactions_total[1m])
```

### Failed Transaction Ratio
```promql
rate(bank_transactions_failure_total[5m]) / rate(bank_transactions_total[5m])
```

### Withdrawal Latency (95th percentile)
```promql
bank_transactions_withdrawal_duration{quantile="0.95"}
```

### Average Transaction Processing Time
```promql
rate(bank_transactions_withdrawal_duration_sum[5m]) / rate(bank_transactions_withdrawal_duration_count[5m])
```

### Insufficient Funds Rate
```promql
rate(bank_transactions_insufficient_funds_total[5m])
```

### Idempotency Duplicate Rate
```promql
rate(bank_transactions_idempotency_duplicate_total[5m])
```

---

## OpenTelemetry Tracing

### Configuration

Traces are configured via environment variables:
```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
```

### Tracer Instrumentation

The `TracingService` provides convenient methods for span creation:

```java
// Start a withdrawal span
Span span = tracingService.startWithdrawalSpan(accountId, amount);

// Add attributes
span.setAttribute("account.status", "ACTIVE");
span.addEvent("balance_verified");

// Record operations
tracingService.recordSuccess(span);
span.end();
```

### Span Types

#### 1. Withdrawal Span
```
Span Name: bank.withdrawal
Attributes:
  - account.id (UUID)
  - transaction.amount (decimal)
  - transaction.type (WITHDRAWAL)
```

#### 2. Transfer Span
```
Span Name: bank.transfer
Attributes:
  - account.source.id (UUID)
  - account.destination.id (UUID)
  - transaction.amount (decimal)
  - transaction.type (TRANSFER)
```

#### 3. Deposit Span
```
Span Name: bank.deposit
Attributes:
  - account.id (UUID)
  - transaction.amount (decimal)
  - transaction.type (DEPOSIT)
```

#### 4. Account Creation Span
```
Span Name: bank.account.creation
Attributes:
  - account.holder_name (string)
```

#### 5. Idempotency Check Span
```
Span Name: bank.idempotency.check
Attributes:
  - idempotency.key (UUID)
```

#### 6. Locking Span
```
Span Name: bank.locking
Attributes:
  - account.id (UUID)
  - lock.type (READ_LOCK | WRITE_LOCK)
```

#### 7. Ledger Entry Span
```
Span Name: bank.ledger.entry
Attributes:
  - transaction.id (UUID)
  - ledger.entry.type (DEBIT | CREDIT | FEE)
```

---

## Structured Logging

### Log Levels

| Level | Usage |
|-------|-------|
| **DEBUG** | Detailed diagnostic information (idempotency checks, locking operations) |
| **INFO** | General informational messages (account creation, transaction start) |
| **WARN** | Warning conditions (insufficient funds, duplicate keys) |
| **ERROR** | Error conditions (exceptions, transaction failures) |

### Log Configuration

```yaml
logging:
  level:
    com.example.bank: DEBUG
    com.example.bank.service: DEBUG
    com.example.bank.controller: INFO
```

### Example Log Patterns

**Transaction Processing**
```
[INFO] Withdrawal request: account=550e8400-e29b-41d4-a716-446655440000, amount=100.00, idempotencyKey=...
[DEBUG] Idempotency key registered: 550e8400...
[DEBUG] Acquired write lock on account: 550e8400...
[INFO] Withdrawal completed successfully: txId=..., amount=100.00
```

**Error Handling**
```
[WARN] Insufficient funds error recorded
[ERROR] Withdrawal failed: Insufficient funds for withdrawal. Required: 10000.00 USD, Available: 5000.00 USD
```

**Security Events**
```
[INFO] Login attempt for user: holder1
[WARN] Authentication failed for user: holder1
[INFO] User logged in successfully: holder1
```

---

## Monitoring Dashboard Setup

### Prometheus Configuration

Add to your `prometheus.yml`:
```yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'bank-microservice'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/api/actuator/prometheus'
```

### Grafana Dashboards

**Example Dashboard Queries:**

1. **Transaction Rate**
   ```promql
   rate(bank_transactions_total[1m])
   ```

2. **Error Rate**
   ```promql
   rate(bank_transactions_failure_total[1m])
   ```

3. **Withdrawal Latency**
   ```promql
   bank_transactions_withdrawal_duration{quantile="0.95"}
   ```

4. **Active Pending Transactions**
   ```promql
   bank_transactions_pending_active
   ```

---

## Alerts

### Recommended Alert Rules

```yaml
groups:
  - name: bank_service_alerts
    rules:
      # Alert on high failure rate
      - alert: HighTransactionFailureRate
        expr: rate(bank_transactions_failure_total[5m]) > 0.05
        for: 5m
        annotations:
          summary: "Transaction failure rate > 5%"

      # Alert on insufficient funds spike
      - alert: HighInsufficientFundsRate
        expr: rate(bank_transactions_insufficient_funds_total[5m]) > 0.1
        for: 5m
        annotations:
          summary: "Insufficient funds errors > 10%"

      # Alert on high latency
      - alert: HighWithdrawalLatency
        expr: bank_transactions_withdrawal_duration{quantile="0.95"} > 5000
        for: 5m
        annotations:
          summary: "p95 withdrawal latency > 5 seconds"

      # Alert on high idempotency duplicate rate
      - alert: HighIdempotencyDuplicateRate
        expr: rate(bank_transactions_idempotency_duplicate_total[5m]) > 0.01
        for: 5m
        annotations:
          summary: "Idempotency duplicates > 1%"
```

---

## Health Checks

### Liveness Probe
```
GET /api/actuator/health/liveness
```
Returns 200 if application is running.

### Readiness Probe
```
GET /api/actuator/health/readiness
```
Returns 200 if application is ready to serve requests (DB connected, etc).

---

## Performance Optimization Tips

1. **Transaction Latency**
   - Monitor `bank_transactions_*_duration` metrics
   - Optimize account locking strategy if p99 exceeds 5 seconds

2. **Error Rate Monitoring**
   - Track `bank_transactions_insufficient_funds_total`
   - Investigate spikes in failure rate

3. **Idempotency Performance**
   - Monitor Redis operations: `bank.idempotency.check`
   - Ensure Redis is responsive

4. **Balance Tracking**
   - Use `bank_accounts_balance_change` to detect anomalies
   - Alert on unusual balance modification patterns

---

## Troubleshooting

### Metrics Not Appearing
1. Verify endpoint: `curl http://localhost:8080/api/actuator/prometheus`
2. Check logs: `mvn spring-boot:run | grep -i metrics`
3. Ensure Micrometer is on classpath: `mvn dependency:tree | grep micrometer`

### Traces Not Appearing
1. Check OTEL endpoint: `echo $OTEL_EXPORTER_OTLP_ENDPOINT`
2. Verify OpenTelemetry receiver is running: `telemetry receiver status`
3. Check logs for OTLP export errors

### High Latency Issues
1. Check active locks: Monitor `bank.locking` spans
2. Review database performance: Check PostgreSQL logs
3. Check network latency: Review span duration breakdown

---

## References

- [Micrometer Documentation](https://micrometer.io/)
- [OpenTelemetry Java Guide](https://opentelemetry.io/docs/instrumentation/java/)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/naming/)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
