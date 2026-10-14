package dev.wordrush.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class WordListTest {

    private static final WordList DEFAULT = WordList.loadDefault();

    @Test
    void bundledListsLoad() {
        assertThat(DEFAULT.answers()).hasSize(2300).allMatch(w -> w.matches("[A-Z]{5}"));
        assertThat(DEFAULT.allowedCount()).isGreaterThan(8000);
        assertThat(DEFAULT.answers()).allMatch(DEFAULT::isAllowed);
    }

    @Test
    void acceptsRealWordsAndRejectsGibberish() {
        assertThat(DEFAULT.isAllowed("CRANE")).isTrue();
        assertThat(DEFAULT.isAllowed("XQZVK")).isFalse();
        assertThat(DEFAULT.isAllowed("crane")).as("expects normalized input").isFalse();
    }

    @Test
    void normalizeTrimsAndUppercases() {
        assertThat(WordList.normalize("  crAne ")).isEqualTo("CRANE");
        assertThat(WordList.normalize(null)).isEmpty();
    }

    @Test
    void randomAnswerSkipsExcludedWords() {
        WordList words = new WordList(List.of("AAAAA", "BBBBB", "CCCCC"), List.of());
        SplittableRandom rng = new SplittableRandom(42);

        Set<String> picked = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            picked.add(words.randomAnswer(rng, Set.of("AAAAA", "BBBBB")));
        }

        assertThat(picked).containsExactly("CCCCC");
    }

    @Test
    void randomAnswerAllowsRepeatsOnceEverythingIsUsed() {
        WordList words = new WordList(List.of("AAAAA", "BBBBB"), List.of());

        String answer = words.randomAnswer(new SplittableRandom(1), Set.of("AAAAA", "BBBBB"));

        assertThat(answer).isIn("AAAAA", "BBBBB");
    }

    @Test
    void rejectsMalformedListEntries() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WordList(List.of("crane"), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new WordList(List.of(), List.of()));
    }
}
