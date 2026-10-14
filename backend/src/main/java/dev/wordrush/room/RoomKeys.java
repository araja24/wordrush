package dev.wordrush.room;

import java.util.List;
import java.util.UUID;

/**
 * Every Redis key for one room shares the hash tag {CODE}, so in Redis Cluster they map to one
 * slot and a Lua script may touch them together.
 *
 * <pre>
 * wr:room:{CODE}             HASH   status, hostId, settings, createdAt, seq, gameId, roundNo, phaseEndsAt
 * wr:room:{CODE}:players     HASH   userId -> Player JSON
 * wr:room:{CODE}:round       HASH   roundId, roundNo, answer, startedAt, endsAt, winnerId, winnerAttempts, winnerAt, endReason
 * wr:room:{CODE}:guesses     HASH   userId -> "CRANE:BYGBB,SLOTH:..." this round
 * wr:room:{CODE}:attempts    HASH   userId -> guesses used this round
 * wr:room:{CODE}:lb          ZSET   userId -> points this game
 * wr:room:{CODE}:used        SET    answers already used this game
 * wr:room:{CODE}:idem:{id}   STRING cached result of guess {id} (idempotency)
 * wr:user:{userId}:room      STRING the room this user is in
 * wr:timers                  ZSET   "CODE|TYPE|token" -> due epoch ms (all rooms)
 * </pre>
 */
public final class RoomKeys {

    public static final String TIMERS = "wr:timers";

    private RoomKeys() {
    }

    public static String room(String code) {
        return "wr:room:{" + code + "}";
    }

    public static String players(String code) {
        return room(code) + ":players";
    }

    public static String round(String code) {
        return room(code) + ":round";
    }

    public static String guesses(String code) {
        return room(code) + ":guesses";
    }

    public static String attempts(String code) {
        return room(code) + ":attempts";
    }

    public static String leaderboard(String code) {
        return room(code) + ":lb";
    }

    public static String usedAnswers(String code) {
        return room(code) + ":used";
    }

    public static String idempotency(String code, UUID guessId) {
        return room(code) + ":idem:" + guessId;
    }

    public static String userRoom(UUID userId) {
        return "wr:user:" + userId + ":room";
    }

    /** Keys deleted when a room closes (idempotency keys simply expire). */
    static List<String> allForRoom(String code) {
        return List.of(room(code), players(code), round(code), guesses(code), attempts(code),
                leaderboard(code), usedAnswers(code));
    }
}
