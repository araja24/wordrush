package dev.wordrush.room;

import dev.wordrush.auth.AuthenticatedUser;
import dev.wordrush.config.WordRushProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Room membership rules: one room per player at a time, host-only actions, members-only reads.
 * Atomicity lives in {@link RoomRepository}'s Lua scripts; this class decides what to do with
 * their outcomes.
 */
@Service
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private static final int MAX_CODE_ATTEMPTS = 10;

    private final RoomRepository rooms;
    private final RoomCodeGenerator codes;
    private final Clock clock;
    private final Duration ttl;

    public RoomService(RoomRepository rooms, RoomCodeGenerator codes, Clock clock, WordRushProperties properties) {
        this.rooms = rooms;
        this.codes = codes;
        this.clock = clock;
        this.ttl = properties.room().ttl();
    }

    public RoomSnapshot create(AuthenticatedUser caller, RoomSettings settings) {
        leaveCurrentRoom(caller.id());
        Instant now = clock.instant();
        Player host = Player.joining(caller, now);
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            Room room = new Room(codes.next(), RoomStatus.LOBBY, caller.id(), settings, List.of(host), now, 0);
            if (rooms.create(room, ttl)) {
                rooms.setCurrentRoom(caller.id(), room.code(), ttl);
                log.info("Room created room={} host={} settings={}", room.code(), caller.id(), settings);
                return RoomSnapshot.from(room);
            }
        }
        throw new IllegalStateException("Could not allocate a free room code");
    }

    public RoomSnapshot join(AuthenticatedUser caller, String rawCode) {
        String code = normalize(rawCode);
        rooms.currentRoom(caller.id())
                .filter(current -> !current.equals(code))
                .ifPresent(current -> leaveQuietly(caller.id(), current));

        switch (rooms.join(code, Player.joining(caller, clock.instant()), ttl)) {
            case NOT_FOUND -> throw RoomException.notFound(code);
            case FULL -> throw RoomException.full(code);
            case NOT_JOINABLE -> throw RoomException.gameInProgress(code);
            case JOINED -> log.info("Player joined room={} user={}", code, caller.id());
            case ALREADY_MEMBER -> log.debug("Player rejoined room={} user={}", code, caller.id());
        }
        rooms.setCurrentRoom(caller.id(), code, ttl);
        return get(caller, code);
    }

    public void leave(AuthenticatedUser caller, String rawCode) {
        String code = normalize(rawCode);
        RoomRepository.LeaveOutcome outcome = rooms.leave(code, caller.id());
        switch (outcome.kind()) {
            case NOT_FOUND -> throw RoomException.notFound(code);
            case NOT_MEMBER -> throw RoomException.notMember(code);
            default -> logLeave(code, caller.id(), outcome);
        }
        rooms.clearCurrentRoom(caller.id(), code);
    }

    public void kick(AuthenticatedUser caller, String rawCode, UUID target) {
        String code = normalize(rawCode);
        Room room = rooms.find(code).orElseThrow(() -> RoomException.notFound(code));
        if (!room.isHost(caller.id())) {
            throw RoomException.notHost(code);
        }
        if (target.equals(caller.id())) {
            throw RoomException.cannotKickSelf();
        }
        RoomRepository.LeaveOutcome outcome = rooms.leave(code, target);
        switch (outcome.kind()) {
            case NOT_FOUND -> throw RoomException.notFound(code);
            case NOT_MEMBER -> throw RoomException.playerNotFound(code, target);
            default -> log.info("Player kicked room={} user={} by={}", code, target, caller.id());
        }
        rooms.clearCurrentRoom(target, code);
    }

    public RoomSnapshot get(AuthenticatedUser caller, String rawCode) {
        String code = normalize(rawCode);
        Room room = rooms.find(code).orElseThrow(() -> RoomException.notFound(code));
        if (!room.isMember(caller.id())) {
            throw RoomException.notMember(code);
        }
        return RoomSnapshot.from(room);
    }

    /** The caller's current room, used to resume after a refresh. Cleans up stale pointers. */
    public Optional<RoomSnapshot> current(AuthenticatedUser caller) {
        Optional<String> code = rooms.currentRoom(caller.id());
        if (code.isEmpty()) {
            return Optional.empty();
        }
        Optional<Room> room = rooms.find(code.get()).filter(r -> r.isMember(caller.id()));
        if (room.isEmpty()) {
            rooms.clearCurrentRoom(caller.id(), code.get());
        }
        return room.map(RoomSnapshot::from);
    }

    private void leaveCurrentRoom(UUID userId) {
        rooms.currentRoom(userId).ifPresent(code -> leaveQuietly(userId, code));
    }

    /** Leaving a room that already vanished is fine when we're only cleaning up. */
    private void leaveQuietly(UUID userId, String code) {
        RoomRepository.LeaveOutcome outcome = rooms.leave(code, userId);
        logLeave(code, userId, outcome);
        rooms.clearCurrentRoom(userId, code);
    }

    private static void logLeave(String code, UUID userId, RoomRepository.LeaveOutcome outcome) {
        switch (outcome.kind()) {
            case HOST_CHANGED -> log.info("Player left room={} user={} newHost={}", code, userId, outcome.newHostId());
            case ROOM_CLOSED -> log.info("Player left room={} user={}; room closed", code, userId);
            case LEFT -> log.info("Player left room={} user={}", code, userId);
            case NOT_FOUND, NOT_MEMBER -> log.debug("Stale room pointer room={} user={}", code, userId);
        }
    }

    /** Codes are case-insensitive for humans; anything malformed simply doesn't exist. */
    private static String normalize(String rawCode) {
        String code = rawCode == null ? "" : rawCode.strip().toUpperCase(Locale.ROOT);
        if (!RoomCodeGenerator.isValid(code)) {
            throw RoomException.notFound(code);
        }
        return code;
    }
}
