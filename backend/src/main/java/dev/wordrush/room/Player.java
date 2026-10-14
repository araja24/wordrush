package dev.wordrush.room;

import dev.wordrush.auth.AuthenticatedUser;
import java.time.Instant;
import java.util.UUID;

/**
 * A seat in a room, stored as JSON in the room's players hash. {@code joinedAt} (epoch ms)
 * decides host migration: the earliest-joined human takes over.
 */
public record Player(UUID userId, String nickname, boolean bot, boolean connected, long joinedAt) {

    static Player joining(AuthenticatedUser user, Instant now) {
        return new Player(user.id(), user.nickname(), user.bot(), true, now.toEpochMilli());
    }
}
