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

/** Room membership state in Redis. Key layout: {@link RoomKeys}. */
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
    private static final RedisScript<Long> NEXT_SEQ =
            RedisScript.of(new ClassPathResource("redis/next_seq.lua"), Long.class);
    private static final String HOST_CHANGED_PREFIX = "HOST_CHANGED:";

    private final StringRedisTemplate redis;
    private final HashOperations<String, String, String> hashes;
    private final JsonMapper json;

    public RoomRepository(StringRedisTemplate redis, JsonMapper json) {
        this.redis = redis;
        this.hashes = redis.opsForHash();
        this.json = json;
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
                "seq", String.valueOf(room.seq()),
                "roundNo", "0"));
        Long created = redis.execute(CREATE, RoomKeys.allForRoom(room.code()), args.toArray());
        return created != null && created == 1L;
    }

    public Optional<Room> find(String code) {
        Map<String, String> fields = hashes.entries(RoomKeys.room(code));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        List<Player> players = hashes.entries(RoomKeys.players(code)).values().stream()
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

    public boolean isMember(String code, UUID userId) {
        return hashes.hasKey(RoomKeys.players(code), userId.toString());
    }

    JoinOutcome join(String code, Player player, Duration ttl) {
        String result = redis.execute(JOIN, membershipKeys(code),
                player.userId().toString(), json.writeValueAsString(player), seconds(ttl));
        return JoinOutcome.valueOf(result);
    }

    LeaveOutcome leave(String code, UUID userId) {
        String result = redis.execute(LEAVE, RoomKeys.allForRoom(code), userId.toString());
        if (result.startsWith(HOST_CHANGED_PREFIX)) {
            UUID newHost = UUID.fromString(result.substring(HOST_CHANGED_PREFIX.length()));
            return new LeaveOutcome(LeaveOutcome.Kind.HOST_CHANGED, newHost);
        }
        return new LeaveOutcome(LeaveOutcome.Kind.valueOf(result), null);
    }

    /** Next broadcast sequence number, or -1 if the room is gone. */
    public long nextSeq(String code) {
        Long seq = redis.execute(NEXT_SEQ, List.of(RoomKeys.room(code)));
        return seq == null ? -1 : seq;
    }

    void setCurrentRoom(UUID userId, String code, Duration ttl) {
        redis.opsForValue().set(RoomKeys.userRoom(userId), code, ttl);
    }

    /** Extends the pointer's TTL only if it still points at {@code code}. */
    public void refreshCurrentRoom(UUID userId, String code, Duration ttl) {
        if (code.equals(redis.opsForValue().get(RoomKeys.userRoom(userId)))) {
            redis.expire(RoomKeys.userRoom(userId), ttl);
        }
    }

    Optional<String> currentRoom(UUID userId) {
        return Optional.ofNullable(redis.opsForValue().get(RoomKeys.userRoom(userId)));
    }

    /** Clears the pointer only if it still points at {@code code} (the user may have moved on). */
    void clearCurrentRoom(UUID userId, String code) {
        if (code.equals(redis.opsForValue().get(RoomKeys.userRoom(userId)))) {
            redis.delete(RoomKeys.userRoom(userId));
        }
    }

    private static List<String> membershipKeys(String code) {
        return List.of(RoomKeys.room(code), RoomKeys.players(code));
    }

    private static String seconds(Duration ttl) {
        return String.valueOf(ttl.toSeconds());
    }
}
