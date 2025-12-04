# Data Model: Atomic Bank Operations Service

---

## 1. Account

- **id** (UUID): Unique Account ID
- **holderName** (String): Account holder, PII masked in logs/events as needed
- **balance** (Money Value Object): Non-negative invariant enforced
- **status** (Enum): ACTIVE, FROZEN, CLOSED
- **cards** (List<Card>): Associated cards (Debit/Credit)
- **createdAt** (Timestamp)
- **updatedAt** (Timestamp)

**Invariants:**
- Balance can never be negative (enforced at domain and DB)
- Each account may have zero or more cards

---

## 2. Transaction

- **id** (UUID): Unique transaction reference
- **type** (Enum): WITHDRAWAL, TRANSFER, DEPOSIT, FEE
- **sourceAccountId** (UUID): Account debited (nullable for deposit)
- **destinationAccountId** (UUID): Account credited (nullable for withdrawal)
- **status** (Enum): PENDING, POSTED, FAILED
- **amount** (Money Value Object): Non-negative
- **currency** (String): ISO 4217
- **idempotencyKey** (UUID): Required for all mutations
- **initiatedBy** (String): Principal (user/service)
- **createdAt** (Timestamp)
- **completedAt** (Timestamp)

**Invariants:**
- No transaction can leave a negative account balance
- Each transaction is uniquely identified and idempotent

---

## 3. LedgerEntry

- **id** (UUID)
- **transactionId** (UUID)
- **accountId** (UUID)
- **type** (Enum): DEBIT, CREDIT, FEE
- **amount** (Money Value Object)
- **currency** (String)
- **timestamp** (Timestamp)

**Rules:**
- Immutable append-only (no updates/deletes; only compensating new entries)
- For each transaction, sum of associated ledger entry amounts = 0 (double-entry)

---

## 4. Money (Value Object)

- **amount** (BigDecimal/int64): Always non-negative, precision safe
- **currency** (String): ISO code (e.g., "USD"), can restrict to one

**Rules:**
- Immutable once created
- Used throughout for all monetary fields, never raw primitives

---

## 5. Card (Value Object)

- **type** (Enum): DEBIT, CREDIT
- **maskedNumber** (String): Last 4 digits shown, rest masked
- **accountId** (UUID)
- **expiresAt** (Date)
- **status** (Enum): ACTIVE, BLOCKED, EXPIRED

**Notes:**
- All card data is tokenized and not exposed directly for security

---

## 6. User (for test environments)

- **id** (UUID)
- **username** (String)
- **passwordHash** (String, securely stored)
- **roles** (Set<Enum>): ACCOUNT_HOLDER, OPERATOR, ADMIN
- **createdAt** (Timestamp)

---

## Relationships

- **Account** 1 --- * **Transaction** (account can be source or destination)
- **Transaction** 1 --- * **LedgerEntry**
- **Account** 1 --- * **Card**
- **User** (testing/admin only) may initiate Transactions

---

## State Transitions

**Transaction.status:**
- PENDING → POSTED (on success, all ledger entries written; events emitted)
- PENDING → FAILED (on invariant violation or error; account unchanged)

---

## Validation Rules

- Idempotency: Any POST with a duplicate idempotencyKey is a safe no-op after initial success/failure
- All amounts must be positive, except compensating entries (which are explicit and traceable)
- No manual update of balances—always by posting a valid transaction

---

## Outbox/Event Model (integration)

- Events triggered after DB commit only (outbox pattern): TRANSACTION_POSTED, TRANSACTION_FAILED, FRAUD_ALERT

---
