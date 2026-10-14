package dev.wordrush.bot;

import dev.wordrush.auth.AuthenticatedUser;
import dev.wordrush.core.BotRound;
import dev.wordrush.core.Difficulty;
import dev.wordrush.core.Feedback;
import dev.wordrush.core.FeedbackTable;
import dev.wordrush.event.RoomEvent;
import dev.wordrush.game.GameService;
import dev.wordrush.game.GuessOutcome;
import dev.wordrush.game.GuessRequest;
import dev.wordrush.game.GuessResult;
import java.util.SplittableRandom;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One in-app bot. It plays exactly like a person: it hears the same room events, submits guesses
 * through {@link GameService#guess} (so validation, idempotency and anti-cheat apply), and learns
 * only from its own colours. It never reads the answer.
 *
 * <p>Each round runs on its own virtual thread, which mostly sleeps while "thinking", so hundreds
 * of bots cost almost nothing. The play itself is {@link BotRound}, shared with the CLI bot.
 */
class BotPlayer {

    private static final Logger log = LoggerFactory.getLogger(BotPlayer.class);

    private final AuthenticatedUser user;
    private final String roomCode;
    private final Difficulty difficulty;
    private final FeedbackTable table;
    private final GameService games;
    private final double thinkTimeScale;

    private volatile UUID currentRound;
    private volatile Thread worker;

    BotPlayer(AuthenticatedUser user, String roomCode, Difficulty difficulty, FeedbackTable table,
              GameService games, double thinkTimeScale) {
        this.user = user;
        this.roomCode = roomCode;
        this.difficulty = difficulty;
        this.table = table;
        this.games = games;
        this.thinkTimeScale = thinkTimeScale;
    }

    AuthenticatedUser user() {
        return user;
    }

    String roomCode() {
        return roomCode;
    }

    void onEvent(RoomEvent event) {
        switch (event) {
            case RoomEvent.RoundStarted started -> play(started.roundId());
            case RoomEvent.RoundEnded ended -> stop();
            case RoomEvent.ReturnedToLobby lobby -> stop();
            default -> {
                // other events don't change what a bot does
            }
        }
    }

    synchronized void stop() {
        currentRound = null;
        Thread running = worker;
        if (running != null && running != Thread.currentThread()) {
            running.interrupt(); // wakes it from its "thinking" sleep
        }
        worker = null;
    }

    private synchronized void play(UUID roundId) {
        stop();
        currentRound = roundId;
        worker = Thread.ofVirtual().name("bot-" + user.nickname()).start(() -> playRound(roundId));
    }

    private void playRound(UUID roundId) {
        try {
            BotRound.play(table, difficulty, thinkTimeScale, new SplittableRandom(),
                    () -> roundId.equals(currentRound),
                    word -> submit(roundId, word));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // round ended while thinking
        } catch (RuntimeException e) {
            if (roundId.equals(currentRound)) {
                log.warn("Bot stopped playing room={} bot={}: {}", roomCode, user.id(), e.toString());
            } else {
                log.debug("Bot interrupted as the round ended room={} bot={}", roomCode, user.id());
            }
        }
    }

    private Feedback submit(UUID roundId, String word) {
        GuessResult result = games.guess(user, roomCode, new GuessRequest(UUID.randomUUID(), roundId, word));
        return result.outcome() == GuessOutcome.WRONG ? Feedback.parse(result.feedback()) : null;
    }
}
