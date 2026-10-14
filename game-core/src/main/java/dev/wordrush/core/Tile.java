package dev.wordrush.core;

/** Colour of one letter in a guess. Ordinal order matters: it is the base-3 digit in {@link Feedback#code()}. */
public enum Tile {
    GRAY('B'),
    YELLOW('Y'),
    GREEN('G');

    private final char symbol;

    Tile(char symbol) {
        this.symbol = symbol;
    }

    /** One-letter form used on the wire and in the database ("B" for black/gray). */
    public char symbol() {
        return symbol;
    }

    public static Tile fromSymbol(char symbol) {
        for (Tile tile : values()) {
            if (tile.symbol == symbol) {
                return tile;
            }
        }
        throw new IllegalArgumentException("Unknown tile symbol: " + symbol);
    }
}
