package dev.wordrush.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GuestLoginRequest(
        @Schema(example = "alex")
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_]{2,16}", message = "must be 2-16 letters, digits or underscores")
        String nickname) {
}
