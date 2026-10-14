package dev.wordrush.game;

import dev.wordrush.game.TimerRepository.Timer;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Fires due phase timers. Safe to run on every instance: each timer is claimed exactly once. */
@Component
class GameTimerPoller {

    private static final Logger log = LoggerFactory.getLogger(GameTimerPoller.class);
    private static final int BATCH = 100;

    private final TimerRepository timers;
    private final GameService games;
    private final Clock clock;

    GameTimerPoller(TimerRepository timers, GameService games, Clock clock) {
        this.timers = timers;
        this.games = games;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${wordrush.game.timer-poll-millis:250}")
    void fireDueTimers() {
        for (Timer timer : timers.claimDue(clock.millis(), BATCH)) {
            try {
                games.onTimer(timer);
            } catch (RuntimeException e) {
                // One broken room must not stall every other room's timers.
                log.error("Timer failed room={} type={} token={}", timer.roomCode(), timer.type(), timer.token(), e);
            }
        }
    }
}
