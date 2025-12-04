# Implementation Plan: Atomic Bank Operations Microservice

---

## 1. Technical Context

- **Language & Runtime:** Java 21, Spring Boot, Spring Security
- **Containerization:** Docker (suitable for Kubernetes or Compose)
- **Database:** PostgreSQL (ACID transactions for all operations)
- **Caching/Idempotency:** Redis
- **Messaging/Event Streaming:** Kafka (Outbox pattern)
- **Observability:** Prometheus, OpenTelemetry
- **API:** REST (OpenAPI documented)
- **Auth:** OAuth2 (Spring Security), role-based access
- **Testing:** Pre-seeded test users, focused on scenarios in the spec

---

## 2. Constitution & Gate Check

**Security:**  
All endpoints enforced by OAuth2 and mTLS, no unauthenticated access.

**Auditable Ledger:**  
Immutable, append-only double-entry records for every transaction/event.

**Atomicity/Consistency:**  
Spring-managed DB transactions, idempotency keys enforced and logged.

**Event Handling:**  
Outbox pattern ensures events are reliably emitted only after transaction commits.

**General Compliance:**  
Logs, events, and ledger data retained per financial domain requirements (≥7 years).

---

## 3. Phase 0: Research & Requirements Validation

- Validate all technical choices (Spring Boot, Security, Kafka, etc.) for regulatory and business fit.
- Document rationale and alternatives for:
  - Database schema designs
  - Event schemas and integration patterns
  - Idempotency strategy and Redis configuration
  - Observability implementation

*Output: `research.md` with all technology and requirements validation.*

---

## 4. Phase 1: Design

- **Data Model:**  
  Draft and review all entities in `data-model.md` (Account, Transaction, LedgerEntry, Money, Card, User).

- **API Contract:**  
  Define endpoints and schemas in `contracts/openapi.yaml`—cover all listed operations, errors, and authentication flows.

- **Event Schemas:**  
  Specify CloudEvents-compliant JSON contracts for all business and error events.

- **Integration:**  
  Document and test Kafka setup and outbox implementation detail.

- **Quickstart Instructions:**  
  Write or update `quickstart.md` to ensure rapid onboarding.

---

## 5. Phase 2: Implementation

- **Repository Setup:**  
  Prepare branches, codebase structure, environment sample/configs.

- **Service Development:**  
  - Implement domain layer (business entities and aggregates, invariants)
  - Implement repository/infrastructure adapters (JPA for Postgres)
  - Implement REST controllers (Spring Boot)
  - Integrate Spring Security; configure roles, test with seeded users

- **Event & Idempotency:**  
  - Implement outbox pattern 
  - Configure Redis for idempotency and API caching

- **Observability:**  
  - Integrate Prometheus (metrics)
  - Wire in OpenTelemetry (distributed tracing)

- **Testing:**  
  - Unit, integration, and system tests for all flows
  - Security and negative/edge-case tests (idempotency, concurrent withdraws, etc.)
  - Audit scenario testing (double entry, append-only)

---

## 6. Phase 3: Validation & Review

- Run review and acceptance checklist (see `spec.md`)
- Validate configuration/environments support rapid local onboarding and CI/CD
- Test auditability: prove full trace from API call to ledger/event/log
- Validate all business and compliance goals are covered (security, data retention, event outbox, logs, monitoring)

---

## 7. Handoff/Next Steps

- Complete tasks breakdown (see `/speckit.tasks`)
- Assign tasks to team/owners
- Schedule walkthrough/demo for stakeholders
- Prepare deployment instructions for first UAT or production cutover

---

## Timeline & Milestones

1. **Phase 0:** 1-2 days (research, validations)
2. **Phase 1:** 2-4 days (design, contracts)
3. **Phase 2:** 8-14 days (implementation, tests, hardening)
4. **Phase 3:** 2 days (reviews, handoff)

*Adjust according to team size and parallelization.*

---

**This plan should be refined as tasks are started, blockers found, or new clarifications emerge.**
