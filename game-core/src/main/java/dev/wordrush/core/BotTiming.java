package dev.wordrush.core;

import java.util.random.RandomGenerator;

/**
 * How long a bot "thinks" before each guess, tuned to human pace (people typically need a minute or
 * more per word). Expected time to solve, at about 4-6 guesses for easy and 3.5 for hard:
 * easy 100-180 s (often misses a 90 s round), medium 45-80 s, hard 20-40 s.
 */
public final class BotTiming {

    private BotTiming() {
    }

    /**
     * A random think time for one guess. {@code scale} speeds bots up (tests use ~0.01) or slows
     * them down; 1.0 is the normal pace.
     */
    public static long thinkMillis(Difficulty difficulty, RandomGenerator rng, double scale) {
        long millis = switch (difficulty) {
            case EASY -> rng.nextLong(18_000, 30_001);
            case MEDIUM -> rng.nextLong(10_000, 20_001);
            case HARD -> rng.nextLong(6_000, 12_001);
        };
        return Math.round(millis * scale);
    }
}
