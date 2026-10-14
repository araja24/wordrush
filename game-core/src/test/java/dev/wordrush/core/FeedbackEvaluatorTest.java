package dev.wordrush.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FeedbackEvaluatorTest {

    @ParameterizedTest(name = "answer {0}, guess {1} -> {2}")
    @CsvSource({
            // exact and total misses
            "CRANE, CRANE, GGGGG",
            "CRANE, BUILT, BBBBB",
            // plain yellows
            "CRANE, NACRE, YYYYG",
            // guess repeats a letter the answer has once: only one copy is coloured
            "HELLO, LLAMA, YYBBB",
            "WATER, EERIE, YBYBB",
            "ABIDE, SPEED, BBYBY",
            // green takes priority over an earlier yellow (the two-pass case)
            "CRANE, NANNY, BYBGB",
            "ROBOT, FLOOR, BBYGY",
            "APPLE, PAPER, YYGYB",
            // answer has repeated letters
            "ABBEY, BABES, YYGGB",
            "SPEED, EERIE, YYBBB",
            "SPEED, ERASE, YBBYY",
            "LEVEL, EERIE, YGBBB"
    })
    void coloursRepeatedLettersLikeWordle(String answer, String guess, String expected) {
        assertThat(FeedbackEvaluator.evaluate(answer, guess)).hasToString(expected);
    }

    @Test
    void guessingTheAnswerIsSolved() {
        assertThat(FeedbackEvaluator.evaluate("WORDS", "WORDS").isSolved()).isTrue();
        assertThat(FeedbackEvaluator.evaluate("WORDS", "SWORD").isSolved()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "CRAN", "CRANES", "crane", "CR4NE", "CRÂNE"})
    void rejectsMalformedWords(String bad) {
        assertThatIllegalArgumentException().isThrownBy(() -> FeedbackEvaluator.evaluate("CRANE", bad));
        assertThatIllegalArgumentException().isThrownBy(() -> FeedbackEvaluator.evaluate(bad, "CRANE"));
    }

    @Test
    void rejectsNull() {
        assertThatIllegalArgumentException().isThrownBy(() -> FeedbackEvaluator.evaluate("CRANE", null));
    }
}
