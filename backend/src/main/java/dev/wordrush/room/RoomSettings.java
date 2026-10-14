package dev.wordrush.room;

public record RoomSettings(int totalRounds, int roundSeconds, int maxPlayers) {

    public static final int DEFAULT_TOTAL_ROUNDS = 5;
    public static final int DEFAULT_ROUND_SECONDS = 90;
    public static final int DEFAULT_MAX_PLAYERS = 6;
}
