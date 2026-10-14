package dev.wordrush.room;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** All fields optional; omitted ones take the defaults in {@link RoomSettings}. */
public record CreateRoomRequest(
        @Schema(example = "5") @Min(1) @Max(10) Integer totalRounds,
        @Schema(example = "90") @Min(30) @Max(180) Integer roundSeconds,
        @Schema(example = "6") @Min(2) @Max(8) Integer maxPlayers) {

    RoomSettings toSettings() {
        return new RoomSettings(
                totalRounds != null ? totalRounds : RoomSettings.DEFAULT_TOTAL_ROUNDS,
                roundSeconds != null ? roundSeconds : RoomSettings.DEFAULT_ROUND_SECONDS,
                maxPlayers != null ? maxPlayers : RoomSettings.DEFAULT_MAX_PLAYERS);
    }
}
