package dev.wordrush.core;

import static dev.wordrush.core.GameRules.WORD_LENGTH;

/**
 * The colours for one guess, packed into a single int: each position is a base-3 digit
 * (position 0 is the least significant), so there are 3^5 = 243 possible patterns.
 * A small int makes feedback cheap to compare, store in a lookup table, and hash.
 */
public record Feedback(int code) {

    public static final int PATTERNS = 243;
    public static final Feedback SOLVED = new Feedback(PATTERNS - 1);

    public Feedback {
        if (code < 0 || code >= PATTERNS) {
            throw new IllegalArgumentException("Feedback code out of range: " + code);
        }
    }

    public Tile tile(int position) {
        if (position < 0 || position >= WORD_LENGTH) {
            throw new IndexOutOfBoundsException(position);
        }
        int digits = code;
        for (int i = 0; i < position; i++) {
            digits /= 3;
        }
        return Tile.values()[digits % 3];
    }

    public boolean isSolved() {
        return code == SOLVED.code;
    }

    /** Parses the wire form, e.g. {@code "GYBBG"}. */
    public static Feedback parse(String symbols) {
        if (symbols == null || symbols.length() != WORD_LENGTH) {
            throw new IllegalArgumentException("Feedback must be " + WORD_LENGTH + " symbols: " + symbols);
        }
        int code = 0;
        int weight = 1;
        for (int i = 0; i < WORD_LENGTH; i++) {
            code += Tile.fromSymbol(symbols.charAt(i)).ordinal() * weight;
            weight *= 3;
        }
        return new Feedback(code);
    }

    /** Wire form, e.g. {@code "GYBBG"}. */
    @Override
    public String toString() {
        char[] symbols = new char[WORD_LENGTH];
        for (int i = 0; i < WORD_LENGTH; i++) {
            symbols[i] = tile(i).symbol();
        }
        return new String(symbols);
    }
}
