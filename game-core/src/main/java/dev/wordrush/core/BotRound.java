package dev.wordrush.core;

import java.util.function.BooleanSupplier;
import java.util.random.RandomGenerator;

/**
 * A bot's play for one round, independent of how guesses reach the server. The in-app bot submits
 * through the game service; the CLI bot over WebSocket. Both plug in a {@link Submitter}.
 */
public final class BotRound {

    /** Sends one guess and waits for the verdict. */
    @FunctionalInterface
    public interface Submitter {

        /**
         * @return the feedback if the guess counted and was wrong (keep playing), or {@code null} to
         *     stop (won, too late, round over, out of guesses).
         */
        Feedback submit(String word) throws InterruptedException;
    }

    private BotRound() {
    }

    /** Think, guess, learn from the colours, repeat. Returns early once {@code active} turns false. */
    public static void play(FeedbackTable table, Difficulty difficulty, double thinkScale, RandomGenerator rng,
                            BooleanSupplier active, Submitter submitter) throws InterruptedException {
        Solver solver = new Solver(table, difficulty, rng);
        for (int attempt = 0; attempt < GameRules.MAX_ATTEMPTS; attempt++) {
            Thread.sleep(BotTiming.thinkMillis(difficulty, rng, thinkScale));
            if (!active.getAsBoolean()) {
                return;
            }
            String word = solver.nextGuess();
            Feedback feedback = submitter.submit(word);
            if (feedback == null) {
                return;
            }
            solver.observe(word, feedback);
        }
    }
}
