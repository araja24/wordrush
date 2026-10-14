package dev.wordrush.room;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lobby-level view of a room. {@code seq} is the last broadcast sequence number. */
public record RoomSnapshot(
        String code,
        RoomStatus status,
        UUID hostId,
        RoomSettings settings,
        List<PlayerView> players,
        Instant createdAt,
        long seq) {

    public record PlayerView(UUID userId, String nickname, boolean bot, boolean connected, boolean host) {
    }

    public static RoomSnapshot from(Room room) {
        List<PlayerView> players = room.players().stream()
                .map(p -> new PlayerView(p.userId(), p.nickname(), p.bot(), p.connected(), room.isHost(p.userId())))
                .toList();
        return new RoomSnapshot(room.code(), room.status(), room.hostId(), room.settings(), players,
                room.createdAt(), room.seq());
    }
}
