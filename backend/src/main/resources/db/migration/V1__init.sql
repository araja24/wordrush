-- Durable history only. Live room state lives in Redis.
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
    id            uuid PRIMARY KEY,
    username      citext      NOT NULL UNIQUE,
    password_hash text,                            -- NULL for guests and bots
    is_guest      boolean     NOT NULL DEFAULT true,
    is_bot        boolean     NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE games (
    id            uuid PRIMARY KEY,
    room_code     char(6)     NOT NULL,
    host_id       uuid        NOT NULL REFERENCES users (id),
    total_rounds  smallint    NOT NULL CHECK (total_rounds BETWEEN 1 AND 10),
    round_seconds smallint    NOT NULL CHECK (round_seconds BETWEEN 30 AND 180),
    status        text        NOT NULL,
    started_at    timestamptz NOT NULL,
    ended_at      timestamptz
);

CREATE TABLE game_players (
    game_id     uuid     NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    user_id     uuid     NOT NULL REFERENCES users (id),
    final_score integer  NOT NULL DEFAULT 0,
    final_rank  smallint,
    PRIMARY KEY (game_id, user_id)
);
CREATE INDEX idx_game_players_user ON game_players (user_id);

CREATE TABLE rounds (
    id              uuid PRIMARY KEY,
    game_id         uuid        NOT NULL REFERENCES games (id) ON DELETE CASCADE,
    round_no        smallint    NOT NULL,
    answer          char(5)     NOT NULL,
    started_at      timestamptz NOT NULL,
    ended_at        timestamptz,
    winner_id       uuid REFERENCES users (id),
    winner_attempts smallint,
    end_reason      text,                          -- SOLVED | TIMEOUT | ALL_FAILED
    UNIQUE (game_id, round_no)
);

CREATE TABLE guesses (
    id          uuid PRIMARY KEY,                  -- the client's guessId (idempotency key)
    round_id    uuid        NOT NULL REFERENCES rounds (id) ON DELETE CASCADE,
    user_id     uuid        NOT NULL REFERENCES users (id),
    attempt_no  smallint    NOT NULL CHECK (attempt_no BETWEEN 1 AND 6),
    word        char(5)     NOT NULL,
    feedback    char(5)     NOT NULL,              -- G=green, Y=yellow, B=gray, e.g. GYBBG
    correct     boolean     NOT NULL,
    received_at timestamptz NOT NULL,
    UNIQUE (round_id, user_id, attempt_no)
);
CREATE INDEX idx_guesses_round ON guesses (round_id);
