# Looking inside the databases

WordRush has two stores, and neither is SQLite, so a SQLite viewer can't open them:

| Store | Holds | Lifetime |
|---|---|---|
| **Postgres** | `users` (guests and bots), plus the `games`, `rounds`, `guesses` history tables (empty until Stage 6) | permanent |
| **Redis** | live rooms, rounds, scores, timers. Key layout in [`RoomKeys.java`](../backend/src/main/java/dev/wordrush/room/RoomKeys.java) | every room key expires 2 h after the last join or round start |

## In Cursor / VS Code

Install **Database Client** (`cweijan.vscode-database-client2`). It handles both Postgres and Redis:

```bash
cursor --install-extension cweijan.vscode-database-client2
```

Start the local stack (`docker compose -f infra/docker-compose.yml up -d`), then open the Database sidebar and add two connections:

| | Postgres | Redis |
|---|---|---|
| Host | `localhost` | `localhost` |
| Port | `5432` | `6379` |
| User / password | `wordrush` / `wordrush` | (none) |
| Database | `wordrush` | `0` |

In the Redis view, a key's TTL is shown next to its value. Every `wr:room:{CODE}*` key should have one. A key showing `-1` (no TTL) would be a leak.

## From the terminal

```bash
docker exec -it wordrush-postgres-1 psql -U wordrush -d wordrush   # then: \dt, select * from users;
docker exec -it wordrush-redis-1 redis-cli                         # then: scan 0 match wr:* , ttl <key>
```

## Production (Render)

Both stores have `ipAllowList: []` in [`render.yaml`](../render.yaml), so only the web service can reach them. That's deliberate. To look at production data:

- **Postgres:** open `wordrush-db` in the Render dashboard and use **Connect > PSQL Command**. Or add your own IP under **Access Control** for a while, connect with the **External Database URL**, and remove the IP when you're done.
- **Key Value:** same idea, if your plan allows outside connections. Add your IP temporarily, then connect with the **External Key Value URL** (check its page in the dashboard).
