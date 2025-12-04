# Atomic Bank Operations Microservice

A production-safe banking microservice with atomic operations, auditable ledger, and event-driven architecture.

## Features

- **Atomic Withdrawals/Transfers**: Guaranteed consistency with double-entry bookkeeping
- **Auditable Ledger**: Immutable append-only transaction log
- **Secure API**: OAuth2/JWT with role-based access control
- **Event-Driven**: Kafka integration with CloudEvents for business events
- **Observable**: Prometheus metrics and OpenTelemetry distributed tracing

## Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.2
- **Database**: PostgreSQL 15
- **Cache**: Redis 7
- **Message Queue**: Kafka
- **Security**: Spring Security with JWT

## Quick Start

See [quickstart.md](quickstart.md) for detailed setup instructions.

```bash
docker-compose up -d
mvn clean install
mvn spring-boot:run
```

## Project Structure

```
src/
├── main/java/com/example/bank/
│   ├── model/              # Domain entities
│   ├── repository/         # Data access layer
│   ├── service/            # Business logic
│   ├── controller/         # REST endpoints
│   ├── exception/          # Custom exceptions
│   ├── config/             # Spring configuration
│   ├── security/           # JWT & authentication
│   └── integration/        # Event & integration logic
├── main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── db/migration/       # Flyway migrations
└── test/java/              # Unit & integration tests
```

## Quick Links

- [Quick Start Guide](quickstart.md)
- [Implementation Plan](specs/001-atomic-bank-ops/plan.md)
- [Data Model](specs/001-atomic-bank-ops/data-model.md)
- [Task List](specs/001-atomic-bank-ops/tasks.md)
