package dev.wordrush.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.wordrush.room.RoomRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

/**
 * The delivery filter is what makes room privacy race-free: whatever happened at SUBSCRIBE time,
 * a message reaches a session only if its user is a member when the message is sent.
 */
class RoomTopicDeliveryFilterTest {

    private static final String CODE = "ABC234";

    private final RoomRepository rooms = mock(RoomRepository.class);
    private final StompSessions sessions = new StompSessions();
    private final RoomTopicDeliveryFilter filter = new RoomTopicDeliveryFilter(sessions, rooms);
    private final MessageChannel channel = mock(MessageChannel.class);
    private final UUID user = UUID.randomUUID();

    private static Message<byte[]> outbound(String sessionId, String destination) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(sessionId);
        headers.setDestination(destination);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void deliversRoomMessagesToCurrentMembers() {
        sessions.register("s1", user);
        when(rooms.isMember(CODE, user)).thenReturn(true);

        assertThat(filter.preSend(outbound("s1", "/topic/rooms/" + CODE), channel)).isNotNull();
    }

    @Test
    void dropsRoomMessagesForSomeoneWhoLeftAfterSubscribing() {
        sessions.register("s1", user);
        when(rooms.isMember(CODE, user)).thenReturn(false); // left between SUBSCRIBE and this message

        assertThat(filter.preSend(outbound("s1", "/topic/rooms/" + CODE), channel)).isNull();
    }

    @Test
    void dropsRoomMessagesForUnknownSessions() {
        assertThat(filter.preSend(outbound("ghost", "/topic/rooms/" + CODE), channel)).isNull();
    }

    @Test
    void leavesPrivateQueuesAlone() {
        assertThat(filter.preSend(outbound("s1", "/user/queue/guess-result"), channel)).isNotNull();
    }
}
