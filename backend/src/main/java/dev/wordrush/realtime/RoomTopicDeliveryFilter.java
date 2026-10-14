package dev.wordrush.realtime;

import dev.wordrush.room.RoomRepository;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Last line of defence for room privacy, on the outbound channel: a room broadcast is delivered
 * to a session only if that session's user is a member of the room right now.
 *
 * <p>Checking only at SUBSCRIBE time is a time-of-check/time-of-use race: a player can leave (or
 * be kicked) between the check and the broker registering the subscription, or a revocation can
 * miss a subscription the registry hasn't recorded yet. Checking at delivery closes every such
 * window, for one Redis HEXISTS per delivered message.
 */
@Component
class RoomTopicDeliveryFilter implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RoomTopicDeliveryFilter.class);
    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/rooms/([A-Z0-9]{6})$");

    private final StompSessions sessions;
    private final RoomRepository rooms;

    RoomTopicDeliveryFilter(StompSessions sessions, RoomRepository rooms) {
        this.sessions = sessions;
        this.rooms = rooms;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) {
            return message;
        }
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (destination == null) {
            return message;
        }
        Matcher topic = ROOM_TOPIC.matcher(destination);
        if (!topic.matches()) {
            return message;
        }
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        UUID userId = sessions.userOf(sessionId);
        if (userId == null || !rooms.isMember(topic.group(1), userId)) {
            log.debug("Dropped room message for non-member session={} user={} dest={}", sessionId, userId, destination);
            return null; // null = do not deliver
        }
        return message;
    }
}
