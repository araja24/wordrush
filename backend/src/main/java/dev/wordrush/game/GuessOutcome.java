package dev.wordrush.game;

public enum GuessOutcome {
    /** Counted, not the word. */
    WRONG,
    /** Counted, and first correct guess of the round. */
    WON,
    /** Correct, but another player's correct guess was committed first. Not counted. */
    CORRECT_LATE,
    /** The round already ended (or the guess targeted an old round). Not counted. */
    ROUND_OVER,
    /** All six guesses used. Not counted. */
    OUT_OF_GUESSES,
    /** Not in the dictionary. Not counted, like Wordle. */
    NOT_A_WORD
}
