package dev.wordrush.room;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Live room state in Redis. Every key for one room shares the hash tag {CODE}, so in Redis
 * Cluster they land on the same slot and a Lua script may touch them together.
 *
 * <pre>
 * wr:room:{CODE}          HASH   status, hostId, totalRounds, roundSeconds, maxPlayers, createdAt, seq
 * wr:room:{CODE}:players  HASH   userId -> Player JSON
 * wr:user:{userId}:room   STRING the room this user is in (for reconnect)
 * </pre>
 */
@Repository
public class RoomRepository {

    enum JoinOutcome { JOINED, ALREADY_MEMBER, NOT_FOUND, NOT_JOINABLE, FULL }

    record LeaveOutcome(Kind kind, UUID newHostId) {
        enum Kind { NOT_FOUND, NOT_MEMBER, LEFT, HOST_CHANGED, ROOM_CLOSED }
    }

    private static final RedisScript<Long> CREATE =
            RedisScript.of(new ClassPathResource("redis/create_room.lua"), Long.class);
    private static final RedisScript<String> JOIN =
            RedisScript.of(new ClassPathResource("redis/join_room.lua"), String.class);
    private static final RedisScript<String> LEAVE =
            RedisScript.of(new ClassPathResource("redis/leave_room.lua"), String.class);
    private static final String HOST_CHANGED_PREFIX = "HOST_CHANGED:";

    private final StringRedisTemplate redis;
    private final HashOperations<String, String, String> hashes;
    private final JsonMapper json;

    public RoomRepository(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.hashes = redis.opsForHash();
        this.json = json;
    }

    static String roomKey(String code) {
        return "wr:room:{" + code + "}";
    }

    static String playersKey(String code) {
        return roomKey(code) + ":players";
    }

    static String userRoomKey(UUID userId) {
        return "wr:user:" + userId + ":room";
    }

    /** Returns false if the code is already in use. The room's first player must be the host. */
    boolean create(Room room, Duration ttl) {
        Player host = room.players().getFirst();
        RoomSettings settings = room.settings();
        List<String> args = new ArrayList<>(List.of(
                host.userId().toString(), json.writeValueAsString(host), seconds(ttl),
                "status", room.status().name(),
                "hostId", room.hostId().toString(),
                "totalRounds", String.valueOf(settings.totalRounds()),
                "roundSeconds", String.valueOf(settings.roundSeconds()),
                "maxPlayers", String.valueOf(settings.maxPlayers()),
                "createdAt", String.valueOf(room.createdAt().toEpochMilli()),
                "seq", String.valueOf(room.seq())));
        Long created = redis.execute(CREATE, keys(room.code()), args.toArray());
        return created != null && created == 1L;
    }

    Optional<Room> find(String code) {
        Map<String, String> fields = hashes.entries(roomKey(code));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        List<Player> players = hashes.entries(playersKey(code)).values().stream()
                .map(value -> json.readValue(value, Player.class))
                .sorted(Comparator.comparingLong(Player::joinedAt))
                .toList();
        return Optional.of(new Room(
                code,
                RoomStatus.valueOf(fields.get("status")),
                UUID.fromString(fields.get("hostId")),
                new RoomSettings(
                        Integer.parseInt(fields.get("totalRounds")),
                        Integer.parseInt(fields.get("roundSeconds")),
                        Integer.parseInt(fields.get("maxPlayers"))),
                players,
                Instant.ofEpochMilli(Long.parseLong(fields.get("createdAt"))),
                Long.parseLong(fields.get("seq"))));
    }

    JoinOutcome join(String code, Player player, Duration ttl) {
        String result = redis.execute(JOIN, keys(code),
                player.userId().toString(), json.writeValueAsString(player), seconds(ttl));
        return JoinOutcome.valueOf(result);
    }

    LeaveOutcome leave(String code, UUID userId) {
        String result = redis.execute(LEAVE, keys(code), userId.toString());
        if (result.startsWith(HOST_CHANGED_PREFIX)) {
            UUID newHost = UUID.fromString(result.substring(HOST_CHANGED_PREFIX.length()));
            return new LeaveOutcome(LeaveOutcome.Kind.HOST_CHANGED, newHost);
        }
        return new LeaveOutcome(LeaveOutcome.Kind.valueOf(result), null);
    }

    void setCurrentRoom(UUID userId, String code, Duration ttl) {
        redis.opsForValue().set(userRoomKey(userId), code, ttl);
    }

    Optional<String> currentRoom(UUID userId) {
        return Optional.ofNullable(redis.opsForValue().get(userRoomKey(userId)));
    }

    /** Clears the pointer only if it still points at {@code code} (the user may have moved on). */
    void clearCurrentRoom(UUID userId, String code) {
        if (code.equals(redis.opsForValue().get(userRoomKey(userId)))) {
            redis.delete(userRoomKey(userId));
        }
    }

    private static List<String> keys(String code) {
        return List.of(roomKey(code), playersKey(code));
    }

    private static String seconds(Duration ttl) {
        return String.valueOf(ttl.toSeconds());
    }
}
