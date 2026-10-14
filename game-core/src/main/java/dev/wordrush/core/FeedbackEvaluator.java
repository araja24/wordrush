package dev.wordrush.core;

import static dev.wordrush.core.GameRules.WORD_LENGTH;

import java.util.regex.Pattern;

/**
 * Scores a guess against the answer, Wordle rules.
 *
 * <p>Two passes are needed so repeated letters are coloured correctly:
 * <ol>
 *   <li>Mark exact matches GREEN and count the answer's leftover (unmatched) letters.</li>
 *   <li>Left to right, a non-green guess letter is YELLOW only while that letter still has
 *       unmatched copies in the answer; each yellow consumes one copy. Otherwise GRAY.</li>
 * </ol>
 * A single left-to-right pass gets {@code answer=CRANE, guess=NANNY} wrong: it would mark the
 * first N yellow even though the answer's only N is already used by the green N.
 */
public final class FeedbackEvaluator {

    private static final Pattern WORD = Pattern.compile("[A-Z]{" + WORD_LENGTH + "}");

    private FeedbackEvaluator() {
    }

    public static Feedback evaluate(String answer, String guess) {
        requireWord(answer, "answer");
        requireWord(guess, "guess");
        return new Feedback(evaluateCode(answer, guess));
    }

    /** Unchecked hot path: both words must already be validated uppercase A-Z of length 5. */
    static int evaluateCode(String answer, String guess) {
        int[] unmatched = new int[26];
        boolean[] green = new boolean[WORD_LENGTH];

        for (int i = 0; i < WORD_LENGTH; i++) {
            char a = answer.charAt(i);
            if (a == guess.charAt(i)) {
                green[i] = true;
            } else {
                unmatched[a - 'A']++;
            }
        }

        int code = 0;
        int weight = 1;
        for (int i = 0; i < WORD_LENGTH; i++) {
            Tile tile;
            if (green[i]) {
                tile = Tile.GREEN;
            } else {
                int letter = guess.charAt(i) - 'A';
                if (unmatched[letter] > 0) {
                    unmatched[letter]--;
                    tile = Tile.YELLOW;
                } else {
                    tile = Tile.GRAY;
                }
            }
            code += tile.ordinal() * weight;
            weight *= 3;
        }
        return code;
    }

    private static void requireWord(String word, String name) {
        if (word == null || !WORD.matcher(word).matches()) {
            throw new IllegalArgumentException(name + " must be " + WORD_LENGTH + " uppercase letters: " + word);
        }
    }
}
