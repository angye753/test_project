# Quickstart Guide: Atomic Bank Operations Microservice

Welcome! This guide will help you get the banking microservice running locally, including all dependencies, for development and testing.

---

## Prerequisites

- Java 21 (JDK)
- Docker & Docker Compose
- Git
- cURL or Postman for API requests

---

## 1. Clone the Repository

git clone <YOUR_REPO_URL>
cd <YOUR_PROJECT_DIRECTORY>---


---

## 2. Start Infrastructure Dependencies

The project uses PostgreSQL, Kafka, and Redis. These can be started using Docker Compose:

docker compose up -d
.
docker run --env-file .env -p 8080:8080 bank-app---

## 3. Configure Environment

Duplicate the example environment settings if you have a template:

cp .env.sample .envOr set the following variables in your environment or Docker run command:
sh
git clone <YOUR_REPO_URL>
cd <YOUR_PROJECT_DIRECTORY>

## 5. Test the API

- **Health check:**
 
  curl http://localhost:8080/actuator/health
  - **Authenticate and call an endpoint (replace `<token>` with a real token):**
 
  curl -H "Authorization: Bearer <token>" http://localhost:8080/accounts
  - **View OpenAPI/Swagger (if enabled):**
  [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

---

## 6. Seed Sample/Test Data

If needed, run the provided script or use Postgres UI/tools to insert sample accounts and users.

./scripts/seed-demo-data.sh

---

## 7. Tear Down

To stop everything:

docker compose downOr terminate Java/Docker app as needed.

---

## Troubleshooting

- If you see “connection refused” errors, make sure the containers are healthy (`docker ps`, `docker logs`).
- For database migration or credential issues, check your environment variables and database state.
- Ensure Java 21 is on your `PATH` (`java --version`).

---

## Next Steps

- Review the [data-model.md](data-model.md) and [OpenAPI contracts](contracts/openapi.yaml).
- Start building or testing endpoints!
- Extend/modify as needed for your use case and team.

---

Welcome to the team, and happy hacking!
---

