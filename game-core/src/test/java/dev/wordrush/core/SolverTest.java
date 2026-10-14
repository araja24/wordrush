package dev.wordrush.core;

import static dev.wordrush.core.GameRules.MAX_ATTEMPTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SolverTest {

    private static final WordList WORDS = WordList.loadDefault();
    private static final FeedbackTable TABLE = FeedbackTable.of(WORDS.answers());

    /** Plays until solved (no attempt cap) and returns the number of guesses used. */
    private static int play(Difficulty difficulty, String answer, long seed) {
        Solver solver = new Solver(TABLE, difficulty, new SplittableRandom(seed));
        for (int attempt = 1; ; attempt++) {
            String guess = solver.nextGuess();
            Feedback feedback = FeedbackEvaluator.evaluate(answer, guess);
            if (feedback.isSolved()) {
                return attempt;
            }
            solver.observe(guess, feedback);
            assertThat(solver.remainingCandidates()).as("answer %s was ruled out", answer).isPositive();
        }
    }

    @Test
    void hardSolvesEveryAnswerWithinSixGuesses() {
        int[] distribution = new int[MAX_ATTEMPTS + 2]; // index MAX_ATTEMPTS + 1 = failed
        long total = 0;
        for (String answer : WORDS.answers()) {
            int attempts = play(Difficulty.HARD, answer, 7);
            distribution[Math.min(attempts, MAX_ATTEMPTS + 1)]++;
            total += attempts;
        }

        System.out.printf("HARD opener=%s avg=%.3f distribution(1..6,fail)=%s%n",
                TABLE.word(TABLE.bestOpener()), (double) total / WORDS.answers().size(),
                List.of(distribution[1], distribution[2], distribution[3], distribution[4],
                        distribution[5], distribution[6], distribution[7]));
        assertThat(distribution[MAX_ATTEMPTS + 1]).as("answers HARD failed in 6").isZero();
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void neverRulesOutTheRealAnswer(Difficulty difficulty) {
        SplittableRandom pick = new SplittableRandom(99);
        long total = 0;
        int games = 300;
        for (int i = 0; i < games; i++) {
            String answer = WORDS.answers().get(pick.nextInt(WORDS.answers().size()));
            total += play(difficulty, answer, i);
        }
        System.out.printf("%s avg=%.3f over %d games%n", difficulty, (double) total / games, games);
    }

    @Test
    void observesGuessesOutsideTheTable() {
        Solver solver = new Solver(TABLE, Difficulty.MEDIUM, new SplittableRandom(1));
        String outsider = "XYLYL"; // valid guess, not an answer
        assertThat(TABLE.indexOf(outsider)).isEqualTo(-1);

        solver.observe(outsider, FeedbackEvaluator.evaluate("CRANE", outsider));

        assertThat(solver.remainingCandidates()).isBetween(1, TABLE.size());
    }

    @Test
    void openerIsStableAcrossCalls() {
        assertThat(TABLE.bestOpener()).isEqualTo(TABLE.bestOpener());
    }
}
