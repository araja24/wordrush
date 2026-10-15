package dev.wordrush.event;

import java.util.List;
import java.util.UUID;

/**
 * Everything broadcast to a room's {@code /topic/rooms/{code}}. Sealed: the compiler knows every
 * subtype, so a {@code switch} over events must handle all of them (see the STOMP broadcaster).
 * Times are epoch milliseconds; clients correct for clock skew using the envelope's serverTime.
 * Never put the answer in an event before {@link RoundEnded}.
 */
public sealed interface RoomEvent {

    record PlayerJoined(UUID userId, String nickname, boolean bot) implements RoomEvent {
    }

    record PlayerLeft(UUID userId, boolean kicked) implements RoomEvent {
    }

    record HostChanged(UUID hostId) implements RoomEvent {
    }

    record Countdown(int roundNo, int totalRounds, long startsAt) implements RoomEvent {
    }

    record RoundStarted(UUID roundId, int roundNo, int totalRounds, long endsAt) implements RoomEvent {
    }

    /** An opponent's guess: colours only, never letters. */
    record PlayerProgress(UUID userId, int attempt, String feedback) implements RoomEvent {
    }

    record RoundEnded(
            UUID roundId,
            int roundNo,
            String answer,
            RoundEndReason reason,
            UUID winnerId,
            Integer winnerAttempts,
            int points,
            Long solveMillis,
            long nextPhaseAt) implements RoomEvent {
    }

    record LeaderboardUpdated(List<Standing> standings) implements RoomEvent {
    }

    record GameOver(List<Standing> standings) implements RoomEvent {
    }

    record ReturnedToLobby() implements RoomEvent {
    }

    /** A chat line or quick reaction. Text is already cleaned and limited by the chat service. */
    record ChatPosted(UUID userId, String nickname, String text, boolean reaction) implements RoomEvent {
    }
}
