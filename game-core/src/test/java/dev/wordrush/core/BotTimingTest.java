package dev.wordrush.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class BotTimingTest {

    @Test
    void harderBotsThinkFaster() {
        SplittableRandom rng = new SplittableRandom(1);
        for (int i = 0; i < 1_000; i++) {
            assertThat(BotTiming.thinkMillis(Difficulty.HARD, rng, 1.0)).isBetween(1_500L, 3_000L);
            assertThat(BotTiming.thinkMillis(Difficulty.MEDIUM, rng, 1.0)).isBetween(3_000L, 6_000L);
            assertThat(BotTiming.thinkMillis(Difficulty.EASY, rng, 1.0)).isBetween(6_000L, 10_000L);
        }
    }

    @Test
    void scaleSpeedsBotsUp() {
        assertThat(BotTiming.thinkMillis(Difficulty.EASY, new SplittableRandom(1), 0.01)).isBetween(60L, 100L);
    }
}
