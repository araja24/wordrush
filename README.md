# WordRush

Multiplayer Wordle race with a server-authoritative Spring Boot backend. See [PLAN.md](PLAN.md) for the design and build stages.

## Run locally

Requires JDK 21 and Docker.

```bash
# Postgres + Redis
docker compose -f infra/docker-compose.yml up -d --wait

# Backend on :8080 with the dev profile (Flyway migrates on startup)
./gradlew :backend:bootRun

curl localhost:8080/actuator/health/readiness
```

API docs: http://localhost:8080/swagger-ui.html. Call `POST /api/v1/auth/guest`, then **Authorize** with the returned `accessToken`.

Frontend on :5173 (proxies `/api` and `/ws` to the backend):

```bash
cd frontend
npm install
npm run dev        # open http://localhost:5173
```

Each browser tab is a separate player (the session lives in `sessionStorage`), so two tabs are enough to play against yourself. Send friends an invite link from the lobby (`/?room=CODE`).

### Bots

In the lobby the host can **Add bot** (easy, medium or hard). Bots play through the same rules as people and only ever see their own colours.

Or run bots as separate clients over the real WebSocket protocol:

```bash
./gradlew :bot:bootJar
java -jar bot/build/libs/wordrush-bot.jar --room ABC234 --count 3 --difficulty HARD
#   --server http://localhost:8080   --think-scale 1.0 (smaller = faster)   --stay (keep playing after a game)
```

Everything in containers instead: `docker compose -f infra/docker-compose.yml --profile app up --build`

## Test

```bash
cd frontend && npm test && npm run typecheck   # reducer unit tests + TypeScript
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
| `JWT_SECRET` | **required** outside the dev profile (32+ chars); the app refuses to start without it |
| `ALLOWED_ORIGINS` | `http://localhost:5173` (comma-separated browser origins for CORS) |
| `HEALTH_SHOW_DETAILS` | `never` (the `dev` profile, used by `bootRun` and tests, shows details) |
