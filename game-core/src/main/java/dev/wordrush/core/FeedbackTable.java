package dev.wordrush.core;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Precomputed feedback for every (guess, answer) pair over one word list, so solvers look
 * patterns up instead of recomputing them. n words need n^2 bytes (2,300 answers ~ 5.3 MB),
 * built once and shared read-only between all bots.
 */
public final class FeedbackTable {

    private final List<String> words;
    private final Map<String, Integer> indexByWord;
    private final byte[] patterns; // patterns[guess * n + answer], unsigned 0..242
    private final int n;
    private volatile int bestOpener = -1;

    private FeedbackTable(List<String> words) {
        this.words = List.copyOf(words);
        this.n = words.size();
        this.indexByWord = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            indexByWord.put(words.get(i), i);
        }
        this.patterns = new byte[n * n];
        for (int g = 0; g < n; g++) {
            String guess = words.get(g);
            int row = g * n;
            for (int a = 0; a < n; a++) {
                patterns[row + a] = (byte) FeedbackEvaluator.evaluateCode(words.get(a), guess);
            }
        }
    }

    public static FeedbackTable of(List<String> words) {
        return new FeedbackTable(words);
    }

    public int size() {
        return n;
    }

    public String word(int index) {
        return words.get(index);
    }

    /** Index of the word, or -1 if it is not in the table. */
    public int indexOf(String word) {
        return indexByWord.getOrDefault(word, -1);
    }

    public int pattern(int guess, int answer) {
        return patterns[guess * n + answer] & 0xFF;
    }

    /**
     * The guess (over all table words) that minimises the expected number of candidates left.
     * Expected size = sum(bucket^2) / count, so we minimise sum of squares. Ties prefer a word
     * that is itself a candidate, since that guess might win outright.
     */
    public int bestGuess(int[] candidates, int count) {
        boolean[] isCandidate = new boolean[n];
        for (int k = 0; k < count; k++) {
            isCandidate[candidates[k]] = true;
        }
        int[] buckets = new int[Feedback.PATTERNS];
        int best = -1;
        long bestScore = Long.MAX_VALUE;

        for (int g = 0; g < n; g++) {
            Arrays.fill(buckets, 0);
            int row = g * n;
            long score = 0;
            for (int k = 0; k < count && score <= bestScore; k++) {
                int p = patterns[row + candidates[k]] & 0xFF;
                score += 2L * buckets[p] + 1; // (b+1)^2 - b^2
                buckets[p]++;
            }
            if (score < bestScore || (score == bestScore && isCandidate[g] && !isCandidate[best])) {
                best = g;
                bestScore = score;
            }
        }
        return best;
    }

    /** {@link #bestGuess} over the full list; identical for every game, so computed once. */
    public int bestOpener() {
        int opener = bestOpener;
        if (opener < 0) {
            int[] all = new int[n];
            Arrays.setAll(all, i -> i);
            opener = bestGuess(all, n);
            bestOpener = opener; // benign race: concurrent callers compute the same value
        }
        return opener;
    }
}
