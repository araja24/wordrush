package dev.wordrush.bot;

import dev.wordrush.room.RoomRepository;
import dev.wordrush.user.User;
import dev.wordrush.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A bot account exists for one room. Rooms vanish without an event (a Redis TTL, or the last human
 * leaving), so this sweep deletes bot accounts that are no longer seated in a live room. That
 * covers in-app bots and CLI bots alike, and bots orphaned by a restart. Safe on several
 * instances: deleting a row that's already gone is a no-op.
 *
 * <p>Stage 6 note: once games are recorded, history rows will reference bot ids. Those foreign keys
 * need {@code ON DELETE SET NULL} (or this sweep must skip bots that played), or the delete fails.
 */
@Component
class BotAccountCleaner {

    private static final Logger log = LoggerFactory.getLogger(BotAccountCleaner.class);
    /** A bot is created a moment before it joins; never delete one that may still be on its way in. */
    private static final Duration GRACE = Duration.ofMinutes(5);

    private final UserRepository users;
    private final RoomRepository rooms;
    private final Clock clock;

    BotAccountCleaner(UserRepository users, RoomRepository rooms, Clock clock) {
        this.users = users;
        this.rooms = rooms;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void sweep() {
        deleteBotsWithoutARoom(clock.instant().minus(GRACE));
    }

    /** Deletes bot accounts created before {@code createdBefore} that aren't in a room; returns how many. */
    int deleteBotsWithoutARoom(Instant createdBefore) {
        int deleted = 0;
        for (User bot : users.findByBotTrueAndCreatedAtBefore(createdBefore)) {
            boolean seated = rooms.currentRoom(bot.id()).filter(code -> rooms.isMember(code, bot.id())).isPresent();
            if (!seated) {
                users.deleteById(bot.id());
                deleted++;
            }
        }
        if (deleted > 0) {
            log.info("Deleted bot accounts without a room count={}", deleted);
        }
        return deleted;
    }
}
