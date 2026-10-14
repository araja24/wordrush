package dev.wordrush.core;

import java.util.random.RandomGenerator;

/**
 * Plays one round as a bot. It never sees the answer: it keeps the set of answers still
 * consistent with every (guess, feedback) it has observed, exactly what a human can deduce.
 * Not thread-safe; one instance per bot per round.
 */
public final class Solver {

    static final double EASY_RANDOM_GUESS_RATE = 0.2;

    private final FeedbackTable table;
    private final Difficulty difficulty;
    private final RandomGenerator rng;
    private final int[] candidates;
    private int count;
    private boolean observedAny;

    public Solver(FeedbackTable table, Difficulty difficulty, RandomGenerator rng) {
        this.table = table;
        this.difficulty = difficulty;
        this.rng = rng;
        this.candidates = new int[table.size()];
        for (int i = 0; i < candidates.length; i++) {
            candidates[i] = i;
        }
        this.count = candidates.length;
    }

    public String nextGuess() {
        if (count == 0) {
            throw new IllegalStateException("No candidates left: feedback was inconsistent with the word list");
        }
        int guess = switch (difficulty) {
            case EASY -> rng.nextDouble() < EASY_RANDOM_GUESS_RATE ? rng.nextInt(table.size()) : randomCandidate();
            case MEDIUM -> randomCandidate();
            case HARD -> hardGuess();
        };
        return table.word(guess);
    }

    /** Narrows the candidates to answers that would have produced this feedback for this guess. */
    public void observe(String guess, Feedback feedback) {
        int g = table.indexOf(guess);
        int kept = 0;
        for (int k = 0; k < count; k++) {
            int c = candidates[k];
            int pattern = g >= 0
                    ? table.pattern(g, c)
                    : FeedbackEvaluator.evaluateCode(table.word(c), guess);
            if (pattern == feedback.code()) {
                candidates[kept++] = c;
            }
        }
        count = kept;
        observedAny = true;
    }

    public int remainingCandidates() {
        return count;
    }

    private int randomCandidate() {
        return candidates[rng.nextInt(count)];
    }

    private int hardGuess() {
        if (!observedAny) {
            return table.bestOpener();
        }
        if (count <= 2) {
            return candidates[0]; // guessing a candidate is at least as good as any split
        }
        return table.bestGuess(candidates, count);
    }
}
