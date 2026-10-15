package dev.wordrush.realtime;

import static dev.wordrush.realtime.StompAuthChannelInterceptor.caller;

import dev.wordrush.chat.ChatRequest;
import dev.wordrush.chat.ChatService;
import dev.wordrush.common.error.DomainException;
import dev.wordrush.game.GameService;
import dev.wordrush.game.GameSnapshot;
import dev.wordrush.game.GuessRequest;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

/**
 * STOMP commands under /app/rooms/{code}/... The WebSocket equivalent of a REST controller:
 * unwrap the frame, delegate to the service, and turn exceptions into a private error message.
 */
@Controller
class GameMessageController {

    private static final Logger log = LoggerFactory.getLogger(GameMessageController.class);

    record ErrorMessage(String code, String message) {
    }

    private final GameService games;
    private final ChatService chat;

    GameMessageController(GameService games, ChatService chat) {
        this.games = games;
        this.chat = chat;
    }

    @MessageMapping("/rooms/{code}/start")
    void start(@DestinationVariable String code, Principal principal) {
        games.start(caller(principal), code);
    }

    /** The result is sent to the guesser by the service, before the room hears about it. */
    @MessageMapping("/rooms/{code}/guess")
    void guess(@DestinationVariable String code, @Payload GuessRequest request, Principal principal) {
        games.guess(caller(principal), code, request);
    }

    @MessageMapping("/rooms/{code}/playAgain")
    void playAgain(@DestinationVariable String code, Principal principal) {
        games.playAgain(caller(principal), code);
    }

    @MessageMapping("/rooms/{code}/chat")
    void chat(@DestinationVariable String code, @Payload ChatRequest request, Principal principal) {
        chat.post(caller(principal), code, request);
    }

    /** Request/reply: subscribing to /app/rooms/{code}/state returns the snapshot to this client only. */
    @SubscribeMapping("/rooms/{code}/state")
    GameSnapshot state(@DestinationVariable String code, Principal principal) {
        return games.snapshot(caller(principal), code);
    }

    @MessageExceptionHandler(DomainException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    ErrorMessage onDomainError(DomainException e) {
        return new ErrorMessage(e.code(), e.getMessage());
    }

    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    ErrorMessage onBadMessage(MessageConversionException e) {
        return new ErrorMessage("INVALID_MESSAGE", "Message body could not be read");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    ErrorMessage onUnexpected(Exception e) {
        log.error("Unhandled STOMP error", e);
        return new ErrorMessage("INTERNAL_ERROR", "Something went wrong");
    }
}
