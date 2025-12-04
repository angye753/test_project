---
description: Create or update the project constitution from interactive or provided principle inputs, ensuring all dependent templates stay in sync.
handoffs: 
  - label: Build Specification
    agent: speckit.specify
    prompt: Implement the feature specification based on the updated constitution. I want to build...
---

# Bank Microservice Constitution

## Core Principles

### I. Security and Privacy (NON-NEGOTIABLE)
- All API endpoints MUST require secure authentication and strict authorization.
- Communication MUST be encrypted (TLS 1.2+).
- Sensitive data (account numbers, balances, personal details) MUST NOT be exposed or logged.
- Idempotency keys are mandatory for all financial operations.

### II. Atomicity and Consistency (NON-NEGOTIABLE)
- Every withdrawal or transfer MUST be atomic (all-or-nothing) and idempotent.
- The domain model (Account, Transaction, Money) enforces invariants: no negative balances, double-entry bookkeeping (sum of entries per transaction = 0).
- Status for each transaction MUST be tracked as PENDING, POSTED, or FAILED.

### III. Auditability and Traceability
- Every operation affecting finances MUST generate immutable ledger entries (LedgerEntry).
- Each transaction and event is traceable end-to-end with unique references and timestamps.
- Audit logs MUST be immutable and accessible for compliance checks.

### IV. Integration and Observability
- The microservice MUST publish business events (withdrawals, transfers, failed transactions) to Kafka.
- All major state changes (success, failure, external integrations) MUST fire events for downstream systems (fraud, notifications, analytics).
- The system MUST support structured logging, metrics (Prometheus), and end-to-end tracing (OpenTelemetry).

### V. Scalability and Resilience
- REST endpoints MUST be stateless and support horizontal scaling.
- Outbox patterns and message queues (Kafka) MUST ensure no loss of business-critical events.
- Redis (or similar) MUST be used for caching/idempotency handling and optimized read models.
- All critical failures SHOULD be gracefully degraded and observable by monitoring.

## Additional Constraints

- Schema and API versioning MUST ensure backward compatibility and explicit migration for changes.
- The domain layer (business logic) MUST remain decoupled from technical infrastructure.
- All code changes MUST pass security, audit, and atomicity tests.
- Double-entry invariant violations and negative balances are STRICTLY FORBIDDEN.

## Development and Review Workflow

- All production code MUST be accompanied by automated tests for security, atomicity, audit trail, and event publication.
- Code reviews MUST verify all requirements above and ensure no architectural or security regression.
- Emergency changes require immediate post-hoc compliance review.

## Governance

- This constitution supersedes technical defaults or legacy documents.
- Any amendments MUST be versioned, documented, and reviewed for impact on domain rules, auditability, or integrations.
- Versioning follows semantic rules (major = breaking change, minor = new rule/expansion, patch = clarification).
- Ratification and amendment dates MUST be tracked in ISO format.
- Reviews MUST reference this constitution and checklist as standard.

**Version**: 1.0.0 | **Ratified**: TODO(RATIFICATION_DATE) | **Last Amended**: 2025-12-02