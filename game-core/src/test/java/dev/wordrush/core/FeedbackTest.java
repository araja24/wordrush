package dev.wordrush.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class FeedbackTest {

    @Test
    void everyCodeRoundTripsThroughItsWireForm() {
        for (int code = 0; code < Feedback.PATTERNS; code++) {
            Feedback feedback = new Feedback(code);
            assertThat(Feedback.parse(feedback.toString())).isEqualTo(feedback);
        }
    }

    @Test
    void positionZeroIsTheLeastSignificantDigit() {
        Feedback feedback = Feedback.parse("GYBBB");

        assertThat(feedback.code()).isEqualTo(2 + 1 * 3);
        assertThat(feedback.tile(0)).isEqualTo(Tile.GREEN);
        assertThat(feedback.tile(1)).isEqualTo(Tile.YELLOW);
        assertThat(feedback.tile(4)).isEqualTo(Tile.GRAY);
    }

    @Test
    void solvedIsAllGreen() {
        assertThat(Feedback.SOLVED).hasToString("GGGGG");
        assertThat(Feedback.parse("GGGGB").isSolved()).isFalse();
    }

    @Test
    void rejectsBadInput() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Feedback(243));
        assertThatIllegalArgumentException().isThrownBy(() -> new Feedback(-1));
        assertThatIllegalArgumentException().isThrownBy(() -> Feedback.parse("GGGG"));
        assertThatIllegalArgumentException().isThrownBy(() -> Feedback.parse("GGGGX"));
    }
}
