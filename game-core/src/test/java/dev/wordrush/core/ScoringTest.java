package dev.wordrush.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScoringTest {

    @ParameterizedTest
    @CsvSource({"1, 200", "3, 160", "6, 100"})
    void fewerAttemptsScoreMore(int attempts, int points) {
        assertThat(Scoring.winnerPoints(attempts)).isEqualTo(points);
    }

    @Test
    void rejectsImpossibleAttemptCounts() {
        assertThatIllegalArgumentException().isThrownBy(() -> Scoring.winnerPoints(0));
        assertThatIllegalArgumentException().isThrownBy(() -> Scoring.winnerPoints(7));
    }
}
