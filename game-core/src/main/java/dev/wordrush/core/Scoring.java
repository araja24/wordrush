package dev.wordrush.core;

import static dev.wordrush.core.GameRules.MAX_ATTEMPTS;

/** Points awarded per round. Only the round winner scores. */
public final class Scoring {

    public static final int WIN_BASE = 100;
    public static final int PER_UNUSED_ATTEMPT = 20;

    private Scoring() {
    }

    /** 1 attempt = 200 points, 6 attempts = 100 points. */
    public static int winnerPoints(int attempts) {
        if (attempts < 1 || attempts > MAX_ATTEMPTS) {
            throw new IllegalArgumentException("attempts must be 1.." + MAX_ATTEMPTS + ": " + attempts);
        }
        return WIN_BASE + PER_UNUSED_ATTEMPT * (MAX_ATTEMPTS - attempts);
    }
}
