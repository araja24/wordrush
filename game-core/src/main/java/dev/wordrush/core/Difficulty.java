package dev.wordrush.core;

/** How a bot picks its next guess. Think-time delays live in the bot runtime, not here. */
public enum Difficulty {
    /** Often (40% of guesses) ignores the clues and tries a random word. */
    EASY,
    /** A random word that fits every clue so far. */
    MEDIUM,
    /** The guess that minimises the expected number of words left. */
    HARD
}
