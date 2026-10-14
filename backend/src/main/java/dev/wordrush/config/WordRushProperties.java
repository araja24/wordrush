package dev.wordrush.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed, validated view of the {@code wordrush.*} properties. Bad config fails at startup, not at runtime. */
@Validated
@ConfigurationProperties("wordrush")
public record WordRushProperties(
        @Valid @NotNull Jwt jwt, @Valid @NotNull Cors cors, @Valid @NotNull Room room, @Valid @NotNull Game game,
        @Valid @NotNull Bots bots) {

    /** HS256 needs a key of at least 256 bits, hence 32 bytes minimum. */
    public record Jwt(@NotBlank @Size(min = 32) String secret, @NotNull Duration ttl, @NotBlank String issuer) {
    }

    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    public record Room(@NotNull Duration ttl) {
    }

    /** Pauses between phases, and how long a guess result is remembered for retries. */
    public record Game(@NotNull Duration countdown, @NotNull Duration resultsPause, @NotNull Duration idempotencyTtl) {
    }

    /** 1.0 = normal bot pace; smaller is faster (tests use 0.01). */
    public record Bots(@Positive double thinkTimeScale) {
    }
}
