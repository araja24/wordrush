package dev.wordrush.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomCodeGeneratorTest {

    private final RoomCodeGenerator generator = new RoomCodeGenerator();

    @Test
    void codesAreSixUnambiguousCharacters() {
        for (int i = 0; i < 1_000; i++) {
            String code = generator.next();
            assertThat(code).hasSize(6).doesNotContainAnyWhitespaces();
            assertThat(code).doesNotContain("0", "O", "1", "I", "L");
            assertThat(RoomCodeGenerator.isValid(code)).isTrue();
        }
    }

    @Test
    void codesRarelyRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.next());
        }
        assertThat(seen).hasSizeGreaterThan(9_990);
    }

    @Test
    void rejectsMalformedCodes() {
        assertThat(RoomCodeGenerator.isValid("ABC12")).isFalse();
        assertThat(RoomCodeGenerator.isValid("abcdef")).isFalse();
        assertThat(RoomCodeGenerator.isValid("ABCDE0")).isFalse();
        assertThat(RoomCodeGenerator.isValid(null)).isFalse();
    }
}
