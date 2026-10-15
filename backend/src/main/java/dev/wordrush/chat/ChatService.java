package dev.wordrush.chat;

import dev.wordrush.auth.AuthenticatedUser;
import dev.wordrush.event.RoomBroadcaster;
import dev.wordrush.event.RoomEvent;
import dev.wordrush.room.Player;
import dev.wordrush.room.Room;
import dev.wordrush.room.RoomCodeGenerator;
import dev.wordrush.room.RoomException;
import dev.wordrush.room.RoomRepository;
import dev.wordrush.room.RoomStatus;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Room chat. Messages are broadcast like any other room event and not stored: chat is for the
 * moment, and the game state stays the only thing a snapshot has to rebuild.
 *
 * <p>While a round is on, only the fixed quick reactions are allowed. Free text would let players
 * pass each other hints, and the reactions carry no letters.
 */
@Service
public class ChatService {

    static final int MAX_LENGTH = 200;
    static final Set<String> REACTIONS = Set.of("gg", "again", "so close", "nooo", "how?!", "lol");

    /** Control characters (newlines included) and invisible format characters such as bidi overrides. */
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final RoomRepository rooms;
    private final RoomBroadcaster broadcaster;
    private final ChatRateLimiter limiter;

    ChatService(RoomRepository rooms, RoomBroadcaster broadcaster, ChatRateLimiter limiter) {
        this.rooms = rooms;
        this.broadcaster = broadcaster;
        this.limiter = limiter;
    }

    public void post(AuthenticatedUser caller, String rawCode, ChatRequest request) {
        String code = RoomCodeGenerator.normalize(rawCode);
        Room room = rooms.find(code).orElseThrow(() -> RoomException.notFound(code));
        Player author = room.players().stream()
                .filter(player -> player.userId().equals(caller.id()))
                .findFirst()
                .orElseThrow(() -> RoomException.notMember(code));

        String text = clean(request == null ? null : request.text());
        boolean reaction = request != null && request.reaction();
        if (text.isEmpty()) {
            throw ChatException.invalid("Message is empty");
        }
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            throw ChatException.invalid("Messages are at most " + MAX_LENGTH + " characters");
        }
        if (reaction && !REACTIONS.contains(text)) {
            throw ChatException.invalid("Not a quick reaction");
        }
        if (!reaction && room.status() == RoomStatus.IN_ROUND) {
            throw ChatException.lockedDuringRound();
        }
        if (!limiter.tryAcquire(caller.id())) {
            throw ChatException.rateLimited();
        }
        // The nickname comes from the room, never from the client.
        broadcaster.broadcast(code, new RoomEvent.ChatPosted(author.userId(), author.nickname(), text, reaction));
    }

    static String clean(String text) {
        if (text == null) {
            return "";
        }
        return WHITESPACE.matcher(INVISIBLE.matcher(text).replaceAll(" ")).replaceAll(" ").strip();
    }
}
