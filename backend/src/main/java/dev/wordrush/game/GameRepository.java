package dev.wordrush.game;

import dev.wordrush.core.Feedback;
import dev.wordrush.core.GameRules;
import dev.wordrush.core.Scoring;
import dev.wordrush.event.RoundEndReason;
import dev.wordrush.room.RoomKeys;
import dev.wordrush.room.RoomStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

/** Game state in Redis: phases, rounds, guesses and scores. Key layout: {@link RoomKeys}. */
@Repository
class GameRepository {

    record Phase(RoomStatus status, int roundNo, long phaseEndsAt) {
    }

    record RoundState(
            UUID roundId,
            int roundNo,
            String answer,
            long startedAt,
            long endsAt,
            UUID winnerId,
            Integer winnerAttempts,
            Long winnerAt,
            RoundEndReason endReason) {

        /** How long the winner took, from round start to their winning guess; null if nobody solved it. */
        Long solveMillis() {
            return winnerAt == null ? null : winnerAt - startedAt;
        }
    }

    record GuessEntry(String word, String feedback) {
    }

    /**
     * What submit_guess.lua decided. {@code replay} means this guessId was already processed;
     * {@code word} and {@code feedback} are what was committed (empty if the guess wasn't counted).
     */
    record GuessCommit(boolean replay, String outcome, int attempt, int points, boolean roundEnded,
                       long msBehind, String word, String feedback) {
    }

    record Score(UUID userId, int points) {
    }

    private static final RedisScript<Long> TRANSITION = script("transition.lua", Long.class);
    private static final RedisScript<Long> START_ROUND = script("start_round.lua", Long.class);
    private static final RedisScript<Long> END_ROUND = script("end_round.lua", Long.class);
    private static final RedisScript<String> SUBMIT_GUESS = script("submit_guess.lua", String.class);

    private final StringRedisTemplate redis;
    private final HashOperations<String, String, String> hashes;
    private final ZSetOperations<String, String> zsets;

    GameRepository(StringRedisTemplate redis) {
        this.redis = redis;
        this.hashes = redis.opsForHash();
        this.zsets = redis.opsForZSet();
    }

    Optional<Phase> phase(String code) {
        List<String> values = hashes.multiGet(RoomKeys.room(code), List.of("status", "roundNo", "phaseEndsAt"));
        if (values.getFirst() == null) {
            return Optional.empty();
        }
        return Optional.of(new Phase(
                RoomStatus.valueOf(values.get(0)), parseInt(values.get(1)), parseLong(values.get(2))));
    }

    /** Compare-and-set: applies {@code changes} only if status (and roundNo, if given) still match. */
    boolean transition(String code, RoomStatus expected, Integer expectedRoundNo, Map<String, String> changes) {
        List<String> args = new ArrayList<>();
        args.add(expected.name());
        args.add(expectedRoundNo == null ? "" : expectedRoundNo.toString());
        changes.forEach((field, value) -> {
            args.add(field);
            args.add(value);
        });
        return isOne(redis.execute(TRANSITION, List.of(RoomKeys.room(code)), args.toArray()));
    }

    /** Fresh scoreboard (everyone at 0) and no used answers. Called once by whoever won the start CAS. */
    void resetForNewGame(String code, Collection<UUID> playerIds, Duration ttl) {
        redis.delete(List.of(RoomKeys.leaderboard(code), RoomKeys.usedAnswers(code), RoomKeys.round(code),
                RoomKeys.guesses(code), RoomKeys.attempts(code)));
        for (UUID playerId : playerIds) {
            zsets.add(RoomKeys.leaderboard(code), playerId.toString(), 0);
        }
        redis.expire(RoomKeys.leaderboard(code), ttl);
    }

    Set<String> usedAnswers(String code) {
        Set<String> used = redis.opsForSet().members(RoomKeys.usedAnswers(code));
        return used == null ? Set.of() : used;
    }

    boolean startRound(String code, int previousRoundNo, int roundNo, UUID roundId, String answer,
                       long startedAt, long endsAt, Duration ttl) {
        List<String> keys = List.of(RoomKeys.room(code), RoomKeys.round(code), RoomKeys.guesses(code),
                RoomKeys.attempts(code), RoomKeys.usedAnswers(code), RoomKeys.players(code),
                RoomKeys.leaderboard(code));
        return isOne(redis.execute(START_ROUND, keys,
                String.valueOf(previousRoundNo), String.valueOf(roundNo), roundId.toString(), answer,
                String.valueOf(startedAt), String.valueOf(endsAt), String.valueOf(ttl.toSeconds())));
    }

    boolean endRoundOnTimeout(String code, UUID roundId, long resultsEndsAt) {
        return isOne(redis.execute(END_ROUND, List.of(RoomKeys.room(code), RoomKeys.round(code)),
                roundId.toString(), String.valueOf(resultsEndsAt)));
    }

    Optional<RoundState> round(String code) {
        Map<String, String> fields = hashes.entries(RoomKeys.round(code));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        String winnerId = fields.get("winnerId");
        String winnerAttempts = fields.get("winnerAttempts");
        String winnerAt = fields.get("winnerAt");
        String endReason = fields.get("endReason");
        return Optional.of(new RoundState(
                UUID.fromString(fields.get("roundId")),
                parseInt(fields.get("roundNo")),
                fields.get("answer"),
                parseLong(fields.get("startedAt")),
                parseLong(fields.get("endsAt")),
                winnerId == null ? null : UUID.fromString(winnerId),
                winnerAttempts == null ? null : Integer.valueOf(winnerAttempts),
                winnerAt == null ? null : Long.valueOf(winnerAt),
                endReason == null ? null : RoundEndReason.valueOf(endReason)));
    }

    GuessCommit submitGuess(String code, UUID userId, UUID roundId, UUID guessId, String word, Feedback feedback,
                            long now, long resultsEndsAt, Duration idempotencyTtl) {
        List<String> keys = List.of(RoomKeys.room(code), RoomKeys.round(code), RoomKeys.players(code),
                RoomKeys.guesses(code), RoomKeys.attempts(code), RoomKeys.leaderboard(code),
                RoomKeys.idempotency(code, userId, guessId));
        List<String> args = new ArrayList<>(List.of(
                userId.toString(), roundId.toString(), word, feedback.toString(),
                feedback.isSolved() ? "1" : "0", String.valueOf(now), String.valueOf(resultsEndsAt),
                String.valueOf(idempotencyTtl.toSeconds()), String.valueOf(GameRules.MAX_ATTEMPTS)));
        for (int attempt = 1; attempt <= GameRules.MAX_ATTEMPTS; attempt++) {
            args.add(String.valueOf(Scoring.winnerPoints(attempt))); // Java stays the single source of scoring
        }
        String[] parts = redis.execute(SUBMIT_GUESS, keys, args.toArray()).split("\\|", -1);
        return new GuessCommit("1".equals(parts[0]), parts[1], parseInt(parts[2]), parseInt(parts[3]),
                "1".equals(parts[4]), parseLong(parts[5]), parts[6], parts[7]);
    }

    /** This round's guesses per player, in order. */
    Map<UUID, List<GuessEntry>> guesses(String code) {
        Map<UUID, List<GuessEntry>> result = new HashMap<>();
        hashes.entries(RoomKeys.guesses(code)).forEach((userId, joined) -> {
            List<GuessEntry> entries = new ArrayList<>();
            for (String entry : joined.split(",")) {
                String[] wordAndFeedback = entry.split(":");
                entries.add(new GuessEntry(wordAndFeedback[0], wordAndFeedback[1]));
            }
            result.put(UUID.fromString(userId), entries);
        });
        return result;
    }

    /** Scores, highest first. */
    List<Score> scores(String code) {
        Set<ZSetOperations.TypedTuple<String>> tuples =
                zsets.reverseRangeWithScores(RoomKeys.leaderboard(code), 0, -1);
        Map<UUID, Integer> ordered = new LinkedHashMap<>();
        if (tuples != null) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                ordered.put(UUID.fromString(tuple.getValue()), tuple.getScore() == null ? 0 : tuple.getScore().intValue());
            }
        }
        return ordered.entrySet().stream().map(e -> new Score(e.getKey(), e.getValue())).toList();
    }

    void clearRound(String code) {
        redis.delete(List.of(RoomKeys.round(code), RoomKeys.guesses(code), RoomKeys.attempts(code)));
    }

    private static <T> RedisScript<T> script(String name, Class<T> type) {
        return RedisScript.of(new ClassPathResource("redis/" + name), type);
    }

    private static boolean isOne(Long result) {
        return result != null && result == 1L;
    }

    private static int parseInt(String value) {
        return value == null || value.isEmpty() ? 0 : Integer.parseInt(value);
    }

    private static long parseLong(String value) {
        return value == null || value.isEmpty() ? 0 : Long.parseLong(value);
    }
}
