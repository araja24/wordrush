# WordRush

Multiplayer Wordle race with a server-authoritative Spring Boot backend. See [PLAN.md](PLAN.md) for the design and build stages.

## Run locally

Requires JDK 21 and Docker.

```bash
# Postgres + Redis
docker compose -f infra/docker-compose.yml up -d --wait

# Backend on :8080 (Flyway migrates on startup)
./gradlew :backend:bootRun

curl localhost:8080/actuator/health/readiness
```

Everything in containers instead: `docker compose -f infra/docker-compose.yml --profile app up --build`

## Test

```bash
./gradlew test   # integration tests start their own Postgres/Redis via Testcontainers (Docker must be running)
```

## Configuration

| Env var | Default (local) |
|---|---|
| `PORT` | `8080` |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/wordrush` |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `wordrush` / `wordrush` |
| `REDIS_URL` | `redis://localhost:6379` |
| `LOG_FORMAT` | empty (plain text); `logstash` for JSON |
| `HEALTH_SHOW_DETAILS` | `always` |
