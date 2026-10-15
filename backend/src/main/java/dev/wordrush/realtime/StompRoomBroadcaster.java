package dev.wordrush.realtime;

import dev.wordrush.event.RoomBroadcaster;
import dev.wordrush.event.RoomEvent;
import dev.wordrush.event.RoomEvent.ChatPosted;
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
import dev.wordrush.event.RoomEventBroadcast;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/** Delivers room events through the STOMP broker. */
@Component
class StompRoomBroadcaster implements RoomBroadcaster {

    private final SimpMessagingTemplate template;
    private final SimpUserRegistry userRegistry;
    private final MessageChannel clientInboundChannel;
    private final RoomRepository rooms;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    StompRoomBroadcaster(SimpMessagingTemplate template, SimpUserRegistry userRegistry,
                         @Qualifier("clientInboundChannel") MessageChannel clientInboundChannel,
                         RoomRepository rooms, ApplicationEventPublisher events, Clock clock) {
        this.template = template;
        this.userRegistry = userRegistry;
        this.clientInboundChannel = clientInboundChannel;
        this.rooms = rooms;
        this.events = events;
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
        events.publishEvent(new RoomEventBroadcast(roomCode, event)); // in-process listeners, e.g. bots
    }

    @Override
    public void sendToUser(UUID userId, String destination, Object payload) {
        template.convertAndSendToUser(userId.toString(), destination, payload);
    }

    /**
     * Feeds the broker a server-side UNSUBSCRIBE for each of the user's subscriptions to the room,
     * exactly as if their client had sent it. The registry only knows this instance's sessions;
     * Stage 9 (multiple instances) has to broadcast the revocation.
     */
    @Override
    public void revokeRoomAccess(String roomCode, UUID userId) {
        SimpUser user = userRegistry.getUser(userId.toString());
        if (user == null) {
            return;
        }
        String topic = "/topic/rooms/" + roomCode;
        user.getSessions().forEach(session -> session.getSubscriptions().stream()
                .filter(subscription -> topic.equals(subscription.getDestination()))
                .map(SimpSubscription::getId)
                .toList() // copy: unsubscribing mutates the registry
                .forEach(subscriptionId -> unsubscribe(session.getId(), subscriptionId)));
    }

    private void unsubscribe(String sessionId, String subscriptionId) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
        headers.setSessionId(sessionId);
        headers.setSubscriptionId(subscriptionId);
        headers.setLeaveMutable(true);
        clientInboundChannel.send(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
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
            case ChatPosted e -> "CHAT";
        };
    }
}
