package dev.wordrush.event;

public enum RoundEndReason {
    /** Someone guessed the word; the first correct guess committed in Redis wins. */
    SOLVED,
    /** The round timer ran out. */
    TIMEOUT,
    /** Every player used all six guesses without solving. */
    ALL_FAILED
}
