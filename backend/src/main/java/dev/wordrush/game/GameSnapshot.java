package dev.wordrush.game;

import dev.wordrush.event.RoundEndReason;
import dev.wordrush.event.Standing;
import dev.wordrush.room.RoomSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a client needs to draw the game from scratch, e.g. after a refresh. The caller sees
 * their own letters; opponents appear as colours only. The answer is included only once the round
 * has ended.
 */
public record GameSnapshot(
        RoomSnapshot room,
        int roundNo,
        long phaseEndsAt,
        CurrentRound round,
        List<Standing> leaderboard,
        long serverTime) {

    public record CurrentRound(
            UUID roundId,
            int roundNo,
            long endsAt,
            List<GuessView> yourGuesses,
            Map<UUID, List<String>> opponentFeedback,
            Result result) {
    }

    public record GuessView(String word, String feedback) {
    }

    public record Result(String answer, RoundEndReason reason, UUID winnerId, Integer winnerAttempts,
                         Long solveMillis) {
    }
}
