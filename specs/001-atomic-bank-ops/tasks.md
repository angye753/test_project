# Tasks: Atomic Bank Operations Microservice

---

## Phase 1: Setup

- [x] T001 Create git repository and clone locally
- [x] T002 Initialize Java 21 Spring Boot project structure in `/src/main/java/com/example/bank`
- [x] T003 Create Docker Compose file at `/docker-compose.yml` for Postgres, Kafka, Redis, Zookeeper
- [x] T004 Add Maven build file at `/pom.xml`
- [x] T005 Setup `.env.sample` file with required environment variables
- [x] T006 [P] Create initial `README.md` and `quickstart.md` documentation in project root

---

## Phase 2: Foundational

- [x] T007 Configure Spring Security (OAuth2) basics in `/src/main/java/com/example/bank/config/SecurityConfig.java`
- [x] T008 [P] Add Postgres DB connection config in `/src/main/resources/application.yml`
- [x] T009 [P] Add basic Redis and Kafka integration configs in `/src/main/resources/application.yml`
- [x] T010 Seed database with test users using migration script in `/src/main/resources/db/migration/V2__Seed_test_users.sql`
- [x] T011 Implement exception handling framework in `/src/main/java/com/example/bank/exception/`
- [x] T012 [P] Add structured logging configuration for OpenTelemetry/Prometheus in `/src/main/resources/application.yml`

---

## Phase 3: User Story 1 — Atomic Withdrawals/Transfers ([US1])

- [x] T013 [US1] Define `Account` entity in `/src/main/java/com/example/bank/model/Account.java`
- [x] T014 [US1] Define `Transaction` entity in `/src/main/java/com/example/bank/model/Transaction.java`
- [x] T015 [US1] Define `LedgerEntry` value object in `/src/main/java/com/example/bank/model/LedgerEntry.java`
- [x] T016 [US1] Implement `Money` value object in `/src/main/java/com/example/bank/model/Money.java`
- [x] T017 [P] [US1] Implement `AccountRepository` in `/src/main/java/com/example/bank/repository/AccountRepository.java`
- [x] T018 [P] [US1] Implement `TransactionRepository` and `LedgerEntryRepository` in `/src/main/java/com/example/bank/repository/`
- [x] T019 [US1] Create `AccountService` with atomic withdraw/transfer methods in `/src/main/java/com/example/bank/service/AccountService.java`
- [x] T020 [US1] Enforce balance, idempotency, and invariants in `/src/main/java/com/example/bank/service/AccountService.java`
- [x] T021 [US1] Implement REST controllers for withdrawal and transfer endpoints in `/src/main/java/com/example/bank/controller/AccountController.java`
- [x] T022 [P] [US1] Integrate idempotency using Redis in `/src/main/java/com/example/bank/service/IdempotencyService.java`
- [x] T023 [P] [US1] Unit/Integration tests for atomic ops in `/src/test/java/com/example/bank/service/AccountServiceTests.java`
- [x] T024 [US1] Generate OpenAPI contract for endpoints in `/contracts/openapi.yaml`
- [x] T025 [US1] Document operational metrics and tracing in `/src/main/java/com/example/bank/observability/`

---

## Phase 4: User Story 2 — Auditable Ledger ([US2])

- [x] T026 [US2] Implement append-only ledger logic in `/src/main/java/com/example/bank/service/LedgerService.java`
- [x] T027 [US2] Enforce double-entry bookkeeping rules in `/src/main/java/com/example/bank/service/LedgerService.java`
- [x] T028 [P] [US2] Implement Audit API endpoint for operators in `/src/main/java/com/example/bank/controller/AuditController.java`
- [x] T029 [P] [US2] Integration tests for immutable ledger in `/src/test/java/com/example/bank/service/LedgerServiceTests.java`

---

## Phase 5: User Story 3 — Secure, Scalable API ([US3])

- [ ] T030 [US3] Harden OAuth2 and JWT handling in `/src/main/java/com/example/bank/config/SecurityConfig.java`
- [ ] T031 [US3] Implement role-based authorization for all routes in `/src/main/java/com/example/bank/config/SecurityConfig.java`
- [ ] T032 [P] [US3] Write integration tests for REST API security in `/src/test/java/com/example/bank/controller/AccountControllerTests.java`
- [ ] T033 [US3] Document API versioning/upgrade path in `/contracts/openapi.yaml`

---

## Phase 6: User Story 4 — Integration Events ([US4])

- [ ] T034 [US4] Implement Outbox pattern with Kafka in `/src/main/java/com/example/bank/integration/OutboxService.java`
- [ ] T035 [US4] Publish CloudEvents business events in `/src/main/java/com/example/bank/integration/EventPublisher.java`
- [ ] T036 [P] [US4] Integrate business event schemas in `/contracts/events/`
- [ ] T037 [US4] Integration tests for event publication + retry in `/src/test/java/com/example/bank/integration/OutboxServiceTests.java`

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T038 Review `quickstart.md`, ensure onboarding instructions are correct
- [ ] T039 (P) Refactor code and update all docs as needed
- [ ] T040 (P) Perform final acceptance and compliance checks using specification checklist
- [ ] T041 Final demo/walkthrough with stakeholders

---

## Dependencies

- Setup → Foundational → US1 → [US2, US3, US4 can begin once US1 services/entities are stable] → Polish

---

## Parallel Execution Opportunities

- `[P]` tasks in foundational and within user story phases can be done in parallel by different team members (e.g., repository/services, contract/tests).

---

## MVP Scope

- Completion of all US1 tasks achieves a minimal, production-safe banking core with atomic, auditable ops.

---
