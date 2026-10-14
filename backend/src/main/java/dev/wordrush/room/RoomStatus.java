package dev.wordrush.room;

/** Room lifecycle. Transitions are driven by the game engine (Stage 3); see PLAN.md section 4. */
public enum RoomStatus {
    LOBBY,
    COUNTDOWN,
    IN_ROUND,
    ROUND_RESULTS,
    GAME_OVER
}
