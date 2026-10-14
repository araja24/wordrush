package dev.wordrush.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class BotRoundTest {

    private static final FeedbackTable TABLE = FeedbackTable.of(WordList.loadDefault().answers());

    /** A fake server: scores guesses against an answer the bot itself never sees. */
    private static BotRound.Submitter server(String answer, List<String> guesses) {
        return word -> {
            guesses.add(word);
            Feedback feedback = FeedbackEvaluator.evaluate(answer, word);
            return feedback.isSolved() ? null : feedback;
        };
    }

    @Test
    void solvesUsingOnlyTheFeedbackItIsGiven() throws InterruptedException {
        List<String> guesses = new ArrayList<>();

        BotRound.play(TABLE, Difficulty.HARD, 0, new SplittableRandom(3), () -> true, server("GLOBE", guesses));

        assertThat(guesses).hasSizeLessThanOrEqualTo(6).last().isEqualTo("GLOBE");
    }

    @Test
    void stopsAsSoonAsTheRoundIsNoLongerActive() throws InterruptedException {
        List<String> guesses = new ArrayList<>();

        BotRound.play(TABLE, Difficulty.MEDIUM, 0, new SplittableRandom(3), () -> guesses.size() < 2, server("GLOBE", guesses));

        assertThat(guesses).hasSize(2);
    }
}
