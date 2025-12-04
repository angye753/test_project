# Research and Design Decisions: Atomic Bank Operations Microservice

---

## 1. Language & Runtime

**Decision:**  
Java 21 with Spring Boot

**Rationale:**  
Modern LTS release (long-term support), enables advanced type safety, memory efficiency, and latest JVM features. Spring Boot provides robust support for layered microservice patterns, rapid development, and widely-used security/integration features.

**Alternatives Considered:**  
- Kotlin + Spring Boot: more concise but the team’s expertise is in Java.
- Quarkus/Micronaut: lower spring plugin ecosystem maturity.

---

## 2. Authentication & Authorization

**Decision:**  
Spring Security (with OAuth2 and optionally mutual TLS)

**Rationale:**  
Best-in-class for enterprise/financial services. OAuth2 expected for federated identity; mTLS available for internal service calls.

**Alternatives Considered:**  
- JWT-only: less control over revocation and RBAC.
- API Key: inadequate for sensitive financial ops.

---

## 3. Database & Persistence

**Decision:**  
PostgreSQL (ACID), Spring Data JPA

**Rationale:**  
Trusted RDBMS for financial data; strong transactional guarantees; schema migrations, audit trails, and multi-table joins supported.

---

## 4. Messaging & Events

**Decision:**  
Kafka (Outbox Pattern)  
Events use CloudEvents JSON contracts.

**Rationale:**  
Kafka scales horizontally, durable; Outbox design guarantees at-least-once semantics. CloudEvents interoperable and standard across microservices.

---

## 5. Idempotency & Caching

**Decision:**  
Redis for idempotency and caching frequently read models

**Rationale:**  
Simple setup, high throughput, in-memory. Redis handles idempotency tokens and replay protection with persistence fallback.

**Alternatives Considered:**  
- In-DB idempotency (Postgres): more coupled, may slow down high-volume endpoints.

---

## 6. Observability

**Decision:**  
Prometheus (metrics), OpenTelemetry (tracing), structured logging

**Rationale:**  
Industry standard tools ensure all system actions are traceable and alertable for both runtime and security monitoring.

**Alternatives Considered:**  
- Grafana.
- Proprietary APMs: possible via OpenTelemetry exporters.

---

## 7. Containerization & Deployment

**Decision:**  
Docker, Docker Compose for development; compatible with K8s for cloud

**Rationale:**  
Consistent local dev/prod parity, easy onboarding. All infra (DB, Kafka, Redis) provisioned in containers.

**Alternatives Considered:**  
- Direct VM/JVM deployment: slower, harder to manage environmental consistency.

---

## 8. Testing

**Decision:**  
Pre-defined test users loaded via migration scripts; automated CI/CD (JUnit, Testcontainers)

**Rationale:**  
Ensures tests are deterministic, repeatable, and reflect typical user flows.

**Alternatives Considered:**  
- Manual testing: too error-prone for regulated financial software.

---

## 9. Data Retention and Compliance

**Decision:**  
Ledger and logs retained for 7 years (configurable), append-only, with audit APIs for operators

**Rationale:**  
Meets/exceeds most regulatory requirements for transactional systems in financial domains.

**Alternatives Considered:**  
- Shorter retention: non-compliant.
- Immutable S3/GCS for archive: possible supplement, more complexity.

---

## Open Issues/Notes

- *None at this time—all clarifications resolved with industry standards.*

---

## Change Log

- **2025-12-02:** Initial research decisions drafted

---
