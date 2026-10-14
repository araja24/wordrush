package dev.wordrush.event;

import java.util.UUID;

/** One leaderboard row. Equal scores share a rank (1, 1, 3). */
public record Standing(UUID userId, String nickname, int score, int rank) {
}
