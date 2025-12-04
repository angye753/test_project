# Quick Start Guide: Atomic Bank Operations Microservice

## Prerequisites

- Java 21 JDK
- Docker & Docker Compose
- Maven 3.8+
- Git

## Setup

### 1. Clone & Install

```bash
git clone <repository-url>
cd bank_test
cp .env.sample .env
```

### 2. Start Infrastructure

```bash
docker-compose up -d
# Wait for services to be healthy
docker-compose ps
```

All services should show `Up` status.

### 3. Build & Run

```bash
mvn clean install
mvn spring-boot:run
```

The service will start on `http://localhost:8080`

### 4. Verify Installation

```bash
# Health check
curl http://localhost:8080/api/actuator/health

# Prometheus metrics
curl http://localhost:8080/api/actuator/prometheus
```

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
mvn verify
```

### Run All Tests with Coverage

```bash
mvn clean test jacoco:report
```

## Key Endpoints

### Authentication

- `POST /api/v1/auth/login` - Login and get JWT token

### Accounts

- `POST /api/v1/accounts` - Create account
- `GET /api/v1/accounts/{accountId}` - Get account details
- `POST /api/v1/accounts/{accountId}/withdraw` - Withdraw funds
- `POST /api/v1/accounts/{accountId}/transfer` - Transfer between accounts

### Audit

- `GET /api/v1/audit/ledger` - View audit ledger
- `GET /api/v1/audit/transactions` - View all transactions

## Example API Calls

### Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "holder1",
    "password": "password123"
  }'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9..."
}
```

### Create Account

```bash
curl -X POST http://localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "holderName": "John Doe",
    "currency": "USD",
    "initialBalance": 1000.00
  }'
```

### Withdraw Funds

```bash
curl -X POST http://localhost:8080/api/v1/accounts/{accountId}/withdraw \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 100.00,
    "idempotencyKey": "550e8400-e29b-41d4-a716-446655440000"
  }'
```

## Common Tasks

### Reset Database

```bash
docker-compose exec postgres dropdb -U bankuser bankdb
docker-compose exec postgres createdb -U bankuser bankdb
mvn spring-boot:run
```

### View Logs

```bash
docker-compose logs -f postgres
docker-compose logs -f kafka
mvn spring-boot:run | grep -i error
```

### Access Database

```bash
docker-compose exec postgres psql -U bankuser -d bankdb
```

**Common queries:**
```sql
SELECT * FROM accounts;
SELECT * FROM transactions;
SELECT * FROM ledger_entries;
```

### Stop All Services

```bash
docker-compose down
```

### Clean Rebuild

```bash
mvn clean install
docker-compose restart
mvn spring-boot:run
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| **Connection refused** | Ensure `docker-compose up -d` completed. Check `docker-compose ps` |
| **Port already in use** | Change port in `.env` or `docker-compose kill && docker-compose rm` |
| **Build fails** | Run `mvn clean` and ensure Java 21: `java -version` |
| **Migrations failed** | Verify DB connection in `application.yml` |
| **JWT token invalid** | Ensure token is passed as `Authorization: Bearer <token>` |

## Next Steps

1. Review [Implementation Plan](specs/001-atomic-bank-ops/plan.md)
2. Check [Data Model](specs/001-atomic-bank-ops/data-model.md)
3. Study [Task List](specs/001-atomic-bank-ops/tasks.md)
4. Implement Phase 3 (US1) domain entities
