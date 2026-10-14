package dev.wordrush.room;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Live room state as read from Redis. {@code seq} increases on every change; clients use it
 * to spot missed updates (Stage 5).
 */
public record Room(
        String code,
        RoomStatus status,
        UUID hostId,
        RoomSettings settings,
        List<Player> players,
        Instant createdAt,
        long seq) {

    public boolean isMember(UUID userId) {
        return players.stream().anyMatch(player -> player.userId().equals(userId));
    }

    public boolean isHost(UUID userId) {
        return hostId.equals(userId);
    }
}
