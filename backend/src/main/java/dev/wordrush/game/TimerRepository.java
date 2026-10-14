package dev.wordrush.game;

import dev.wordrush.room.RoomKeys;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Durable phase timers: one Redis sorted set, member "CODE|TYPE|token", score = due time (epoch ms).
 * Timers survive an app restart, and any instance can fire them: each due timer is claimed with
 * ZREM, which returns 1 for exactly one caller, so it fires once without a leader or a lock.
 * The token (round number or id) lets a handler ignore a timer that belongs to an earlier phase.
 */
@Repository
class TimerRepository {

    enum TimerType { COUNTDOWN_END, ROUND_END, RESULTS_END }

    record Timer(String roomCode, TimerType type, String token) {

        String member() {
            return roomCode + "|" + type + "|" + token;
        }

        static Timer parse(String member) {
            String[] parts = member.split("\\|", 3);
            return new Timer(parts[0], TimerType.valueOf(parts[1]), parts[2]);
        }
    }

    private final StringRedisTemplate redis;

    TimerRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    void schedule(Timer timer, long dueAtMillis) {
        redis.opsForZSet().add(RoomKeys.TIMERS, timer.member(), dueAtMillis);
    }

    /** Removes and returns up to {@code limit} timers due by {@code nowMillis} that this caller won. */
    List<Timer> claimDue(long nowMillis, int limit) {
        Set<String> due = redis.opsForZSet().rangeByScore(RoomKeys.TIMERS, 0, nowMillis, 0, limit);
        List<Timer> claimed = new ArrayList<>();
        if (due == null) {
            return claimed;
        }
        for (String member : due) {
            Long removed = redis.opsForZSet().remove(RoomKeys.TIMERS, member);
            if (removed != null && removed == 1L) {
                claimed.add(Timer.parse(member));
            }
        }
        return claimed;
    }
}
