package dev.wordrush.realtime;

import dev.wordrush.event.RoomBroadcaster;
import dev.wordrush.event.RoomEvent;
import dev.wordrush.event.RoomEvent.Countdown;
import dev.wordrush.event.RoomEvent.GameOver;
import dev.wordrush.event.RoomEvent.HostChanged;
import dev.wordrush.event.RoomEvent.LeaderboardUpdated;
import dev.wordrush.event.RoomEvent.PlayerJoined;
import dev.wordrush.event.RoomEvent.PlayerLeft;
import dev.wordrush.event.RoomEvent.PlayerProgress;
import dev.wordrush.event.RoomEvent.ReturnedToLobby;
import dev.wordrush.event.RoomEvent.RoundEnded;
import dev.wordrush.event.RoomEvent.RoundStarted;
import dev.wordrush.room.RoomRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Delivers room events through the STOMP broker. */
@Component
class StompRoomBroadcaster implements RoomBroadcaster {

    private final SimpMessagingTemplate template;
    private final RoomRepository rooms;
    private final Clock clock;

    StompRoomBroadcaster(SimpMessagingTemplate template, RoomRepository rooms, Clock clock) {
        this.template = template;
        this.rooms = rooms;
        this.clock = clock;
    }

    @Override
    public void broadcast(String roomCode, RoomEvent event) {
        long seq = rooms.nextSeq(roomCode);
        if (seq < 0) {
            return; // room closed; nobody left to tell
        }
        template.convertAndSend("/topic/rooms/" + roomCode,
                new RoomMessage(typeOf(event), roomCode, seq, clock.millis(), event));
    }

    @Override
    public void sendToUser(UUID userId, String destination, Object payload) {
        template.convertAndSendToUser(userId.toString(), destination, payload);
    }

    /** Exhaustive over the sealed hierarchy: adding an event without a type fails to compile. */
    static String typeOf(RoomEvent event) {
        return switch (event) {
            case PlayerJoined e -> "PLAYER_JOINED";
            case PlayerLeft e -> "PLAYER_LEFT";
            case HostChanged e -> "HOST_CHANGED";
            case Countdown e -> "COUNTDOWN";
            case RoundStarted e -> "ROUND_STARTED";
            case PlayerProgress e -> "PLAYER_PROGRESS";
            case RoundEnded e -> "ROUND_ENDED";
            case LeaderboardUpdated e -> "LEADERBOARD";
            case GameOver e -> "GAME_OVER";
            case ReturnedToLobby e -> "RETURNED_TO_LOBBY";
        };
    }
}
