package dev.wordrush.bot;

import dev.wordrush.auth.AuthService;
import dev.wordrush.auth.AuthenticatedUser;
import dev.wordrush.config.WordRushProperties;
import dev.wordrush.core.Difficulty;
import dev.wordrush.core.FeedbackTable;
import dev.wordrush.event.RoomEvent;
import dev.wordrush.event.RoomEventBroadcast;
import dev.wordrush.game.GameService;
import dev.wordrush.room.Player;
import dev.wordrush.room.Room;
import dev.wordrush.room.RoomCodeGenerator;
import dev.wordrush.room.RoomException;
import dev.wordrush.room.RoomRepository;
import dev.wordrush.room.RoomService;
import dev.wordrush.room.RoomSnapshot;
import dev.wordrush.room.RoomStatus;
import dev.wordrush.user.User;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Adds in-app bots to rooms and keeps them in step with the game. Bots live in this instance's
 * memory; they leave with their room (or when kicked), and a periodic sweep drops any whose room
 * has gone.
 */
@Service
public class BotService {

    private static final Logger log = LoggerFactory.getLogger(BotService.class);
    private static final List<String> NAMES =
            List.of("Ada", "Grace", "Alan", "Edsger", "Barbara", "Donald", "Margaret", "Ken");

    private final Map<UUID, BotPlayer> bots = new ConcurrentHashMap<>();
    private final RoomRepository rooms;
    private final RoomService roomService;
    private final AuthService authService;
    private final GameService games;
    private final ObjectProvider<FeedbackTable> feedbackTable;
    private final double thinkTimeScale;

    public BotService(RoomRepository rooms, RoomService roomService, AuthService authService, GameService games,
                      ObjectProvider<FeedbackTable> feedbackTable, WordRushProperties properties) {
        this.rooms = rooms;
        this.roomService = roomService;
        this.authService = authService;
        this.games = games;
        this.feedbackTable = feedbackTable;
        this.thinkTimeScale = properties.bots().thinkTimeScale();
    }

    /** Host only, lobby only. The bot takes a normal seat, so room capacity applies. */
    public RoomSnapshot addBot(AuthenticatedUser caller, String rawCode, Difficulty difficulty) {
        String code = RoomCodeGenerator.normalize(rawCode);
        Room room = rooms.find(code).orElseThrow(() -> RoomException.notFound(code));
        if (!room.isMember(caller.id())) {
            throw RoomException.notMember(code);
        }
        if (!room.isHost(caller.id())) {
            throw RoomException.notHost(code);
        }
        // Checked up front so we don't create accounts for bots that could never join.
        if (room.status() != RoomStatus.LOBBY) {
            throw RoomException.gameInProgress(code);
        }
        if (room.players().size() >= room.settings().maxPlayers()) {
            throw RoomException.full(code);
        }

        User account = authService.registerBot(nickname(room, difficulty));
        AuthenticatedUser bot = new AuthenticatedUser(account.id(), account.nickname(), true);
        bots.put(bot.id(), new BotPlayer(bot, code, difficulty, feedbackTable.getObject(), games, thinkTimeScale));
        try {
            RoomSnapshot snapshot = roomService.join(bot, code);
            log.info("Bot added room={} bot={} difficulty={}", code, bot.id(), difficulty);
            return snapshot;
        } catch (RuntimeException e) {
            bots.remove(bot.id()); // lost a race for the last seat
            throw e;
        }
    }

    /** Fans every room event out to that room's bots. Runs on the broadcasting thread, so it only hands off. */
    @EventListener
    void onRoomEvent(RoomEventBroadcast broadcast) {
        if (broadcast.event() instanceof RoomEvent.PlayerLeft left) {
            BotPlayer removed = bots.remove(left.userId());
            if (removed != null) {
                removed.stop();
            }
        }
        for (BotPlayer bot : bots.values()) {
            if (bot.roomCode().equals(broadcast.roomCode())) {
                bot.onEvent(broadcast.event());
            }
        }
    }

    /** Rooms close without an event when the last human leaves; drop their bots. */
    @Scheduled(fixedDelay = 30_000)
    void dropBotsWithoutARoom() {
        bots.values().removeIf(bot -> {
            boolean gone = !rooms.isMember(bot.roomCode(), bot.user().id());
            if (gone) {
                bot.stop();
                log.info("Bot removed with its room room={} bot={}", bot.roomCode(), bot.user().id());
            }
            return gone;
        });
    }

    int activeBots() {
        return bots.size();
    }

    private static String nickname(Room room, Difficulty difficulty) {
        Set<String> taken = room.players().stream().map(Player::nickname).collect(Collectors.toSet());
        String suffix = " (" + difficulty.name().toLowerCase(Locale.ROOT) + ")";
        return NAMES.stream()
                .map(name -> name + suffix)
                .filter(name -> !taken.contains(name))
                .findFirst()
                .orElse("Bot" + (room.players().size() + 1) + suffix);
    }
}
