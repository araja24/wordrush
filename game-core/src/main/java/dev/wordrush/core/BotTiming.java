package dev.wordrush.core;

import java.util.random.RandomGenerator;

/** How long a bot "thinks" before each guess, so bots feel like players rather than instant oracles. */
public final class BotTiming {

    private BotTiming() {
    }

    /**
     * A random think time for one guess. {@code scale} speeds bots up (tests use ~0.01) or slows
     * them down; 1.0 is the normal pace.
     */
    public static long thinkMillis(Difficulty difficulty, RandomGenerator rng, double scale) {
        long millis = switch (difficulty) {
            case EASY -> rng.nextLong(6_000, 10_001);
            case MEDIUM -> rng.nextLong(3_000, 6_001);
            case HARD -> rng.nextLong(1_500, 3_001);
        };
        return Math.round(millis * scale);
    }
}
