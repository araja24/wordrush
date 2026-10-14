# Stage 5: Robustness (deferred)

**Status:** deferred. We skipped this stage on 2026-10-14 to move on to persistence (Stage 6) and will come back to it. The game works without it; this stage hardens it against spam, dropped connections and players who vanish.

This doc records what already exists, what is left, how to build it, and how to know it's done.

## Already in place (built in earlier stages)

| Concern | Where it lives |
|---|---|
| Idempotent guesses (same `guessId` = same result, one attempt) | `submit_guess.lua`, per-user idempotency keys (Stage 3) |
| Per-room sequence numbers on every broadcast | `next_seq.lua`, `StompRoomBroadcaster` |
| Client drops events the snapshot already covers and flags gaps | `frontend/src/state/reducer.ts` (`needsResync`) |
| Re-fetching the snapshot on a gap, and on every (re)connect | `frontend/src/realtime/RoomSocket.ts` (subscribe topic first, then `/app/rooms/{code}/state`) |
| Refresh resumes the room and restores your board | `GET /api/v1/me/room` + the snapshot; verified in the browser |
| STOMP transport heartbeats, 10 s both ways | `WebSocketConfig`, `RoomSocket` |
| Host hand-off when the host *leaves* (not when they disconnect) | `leave_room.lua` |
| Ex-members stop receiving room messages | `RoomTopicDeliveryFilter` (delivery-time membership check) |

## What's left

### 1. Rate limiting

- **Limits:**
  - guesses: 5 per second per user
  - room creation: 10 per minute per user
  - guest sign-in: 20 per minute per IP
- **Mechanism:** a Redis fixed-window counter `wr:rl:{subject}:{action}:{windowStart}`. A small Lua script does `INCR` and sets `EXPIRE` on the first hit, so the counter can never be left without a TTL.
- **Responses:**
  - REST: `429` problem+json with code `RATE_LIMITED` and a `Retry-After` header.
  - STOMP: `{code: "RATE_LIMITED"}` on `/user/queue/errors`.
  - The frontend shows a toast.
- **Where:** a `RateLimiter` component, called from `GameService.guess`, `RoomService.create` and `AuthService.loginAsGuest`. Keep it out of controllers so bots and REST get the same rules.

### 2. Presence: detect players who disconnect

- **On `SessionDisconnectEvent`:**
  - set `connected=false` in the player's JSON,
  - broadcast a new `PLAYER_CONNECTION {userId, connected}` event,
  - record `now` in `wr:room:{CODE}:presence` (ZSET, userId → last seen).
- **While connected:** the client sends `/app/rooms/{code}/heartbeat` every 5 s, which refreshes the presence score. On reconnect, set `connected=true` and broadcast again.
- **`PresenceSweeper`:** a `@Scheduled` job every 5 s.
  - It finds members last seen over 30 s ago (`ZRANGEBYSCORE presence 0 now-30000`), claims each with `ZREM` (safe with several instances, like the timers), and removes them through the normal leave path.
  - That path already broadcasts `PLAYER_LEFT`, hands the host role on, and closes rooms with no humans left.
- **The 30 s grace period is what makes a refresh harmless.**
- **Frontend:**
  - the reducer handles `PLAYER_CONNECTION`,
  - the lobby and lanes dim disconnected players,
  - `RoomSocket` sends the heartbeat.

### 3. In-app bots across restarts

Bots live in one server's memory. After a restart, their seats remain in Redis but nobody plays them. Two options:
- **(a)** on startup, scan rooms for `bot: true` players and rebuild their `BotPlayer`s,
- **(b)** let the presence sweeper remove them, giving bots no heartbeat and no grace period.

(a) is friendlier; (b) is simpler. In both cases bots must never be swept while their instance is alive: `BotService` should refresh their presence.

### 4. Ending a round when a leaver makes "everyone is out" true

`submit_guess.lua` ends the round with `ALL_FAILED` when the last player uses their sixth guess. But if the only player still guessing **leaves** instead, nobody triggers that check and the round runs to its timeout.

Fix: after a leave during `IN_ROUND`, run the same "everyone out?" check, as its own CAS script.

### 5. Client retry of an in-flight guess

If the socket drops after a guess is sent but before the result arrives, the UI unlocks after 5 s and the guess is lost from the player's view. Keep the pending `{guessId, word}`, and after reconnect and snapshot, resend it with the **same** `guessId`. Idempotency makes this safe; the CLI bot already does it.

## Done when

- [ ] Refreshing mid-round restores the board in under 1 s (already true; measure and record the number).
- [ ] Closing a tab: others see the player marked disconnected immediately and `PLAYER_LEFT` within about 35 s. If it was the host, someone else becomes host.
- [ ] Spamming guesses returns `RATE_LIMITED` and never more than 5 counted attempts per second.
- [ ] Restarting the backend mid-game: in-app bots either keep playing or are removed (whichever option was chosen), never stuck.
- [ ] The last active player leaving mid-round ends it as `ALL_FAILED` right away.
- [ ] A guess sent just before a disconnect is counted exactly once after reconnecting.

## Tests to write

- `RateLimitIT`: burst 20 guesses in one second → at most 5 counted, the rest `RATE_LIMITED`; 21st sign-in from one IP → `429`.
- `PresenceIT`: with a short grace period in test config, connect two players and close one socket → `PLAYER_CONNECTION` then `PLAYER_LEFT`; close the host's socket → `HOST_CHANGED`.
- `PresenceSweeperTest`: two sweepers racing over the same stale entry → one removal (ZREM claim).
- Reducer unit tests for `PLAYER_CONNECTION` and for resending the pending guess.
- Browser script: refresh mid-round and time how long the board takes to come back (feeds the resume-time metric in PLAN.md section 8).

## Metrics this stage unlocks (PLAN.md section 8)

- dead-player detection time (`wordrush.presence.eviction.delay`)
- resync time after refresh (`wordrush.resync.latency`)
- rate-limit rejections (`wordrush.ratelimit.rejected`)
