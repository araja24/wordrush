package dev.wordrush.room;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** What a member sees about a room. Stage 3 adds the current round and leaderboard. */
public record RoomSnapshot(
        String code,
        RoomStatus status,
        UUID hostId,
        RoomSettings settings,
        List<PlayerView> players,
        Instant createdAt) {

    public record PlayerView(UUID userId, String nickname, boolean bot, boolean connected, boolean host) {
    }

    static RoomSnapshot from(Room room) {
        List<PlayerView> players = room.players().stream()
                .map(p -> new PlayerView(p.userId(), p.nickname(), p.bot(), p.connected(), room.isHost(p.userId())))
                .toList();
        return new RoomSnapshot(room.code(), room.status(), room.hostId(), room.settings(), players, room.createdAt());
    }
}
