package dev.wordrush.botcli;

import dev.wordrush.core.BotRound;
import dev.wordrush.core.Difficulty;
import dev.wordrush.core.Feedback;
import dev.wordrush.core.FeedbackTable;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * One bot over a real STOMP connection, seeing exactly what a browser sees: room events on
 * /topic/rooms/{code} and its own results on /user/queue/guess-result.
 */
final class BotClient {

    private static final long RESULT_TIMEOUT_SECONDS = 5;

    private final ApiClient.Session session;
    private final String code;
    private final Difficulty difficulty;
    private final double thinkScale;
    private final FeedbackTable table;
    private final boolean exitAfterGame;
    private final CountDownLatch finished;
    private final AtomicBoolean done = new AtomicBoolean();
    private final Map<String, CompletableFuture<Map<String, Object>>> pending = new ConcurrentHashMap<>();

    private volatile String currentRound;
    private StompSession stomp;

    BotClient(ApiClient.Session session, String code, Difficulty difficulty, double thinkScale, FeedbackTable table,
              boolean exitAfterGame, CountDownLatch finished) {
        this.session = session;
        this.code = code;
        this.difficulty = difficulty;
        this.thinkScale = thinkScale;
        this.table = table;
        this.exitAfterGame = exitAfterGame;
        this.finished = finished;
    }

    void connect(WebSocketStompClient client, String wsUrl) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + session.token());
        stomp = client.connectAsync(wsUrl, (WebSocketHttpHeaders) null, headers, new StompSessionHandlerAdapter() {
            @Override
            public void handleTransportError(StompSession s, Throwable exception) {
                finish("connection lost: " + exception.getMessage());
            }
        }).get(10, TimeUnit.SECONDS);

        subscribe("/topic/rooms/" + code, this::onRoomMessage);
        subscribe("/user/queue/guess-result", this::onGuessResult);
        subscribe("/user/queue/kicked", message -> finish("removed by the host"));
        subscribe("/user/queue/errors", message -> say("server error: " + message.get("message")));
        say("joined " + code + " as " + difficulty.name().toLowerCase());
    }

    private void onRoomMessage(Map<String, Object> message) {
        Map<?, ?> payload = (Map<?, ?>) message.get("payload");
        switch (String.valueOf(message.get("type"))) {
            case "ROUND_STARTED" -> startRound(String.valueOf(payload.get("roundId")), payload.get("roundNo"));
            case "ROUND_ENDED" -> {
                currentRound = null;
                boolean won = session.userId().equals(payload.get("winnerId"));
                say("round over, word was " + payload.get("answer") + (won ? " (I won)" : ""));
            }
            case "GAME_OVER" -> {
                say("game over: " + payload.get("standings"));
                if (exitAfterGame) {
                    finish("done");
                }
            }
            default -> {
                // lobby events don't change what a bot does
            }
        }
    }

    private void startRound(String roundId, Object roundNo) {
        currentRound = roundId;
        say("round " + roundNo + " started");
        Thread.ofVirtual().name("bot-" + session.nickname()).start(() -> {
            try {
                BotRound.play(table, difficulty, thinkScale, new SplittableRandom(),
                        () -> roundId.equals(currentRound), word -> submit(roundId, word));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException e) {
                say("stopped playing: " + e.getMessage());
            }
        });
    }

    /**
     * Sends a guess and waits for its result. If no result arrives in time, resends with the SAME
     * guessId: the server treats it as a retry, so the guess can never be counted twice.
     */
    private Feedback submit(String roundId, String word) throws InterruptedException {
        String guessId = UUID.randomUUID().toString();
        Map<String, Object> request = Map.of("guessId", guessId, "roundId", roundId, "word", word);
        for (int attempt = 1; attempt <= 2; attempt++) {
            CompletableFuture<Map<String, Object>> result = new CompletableFuture<>();
            pending.put(guessId, result);
            stomp.send("/app/rooms/" + code + "/guess", request);
            try {
                Map<String, Object> verdict = result.get(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                String outcome = String.valueOf(verdict.get("outcome"));
                say(word + " -> " + (verdict.get("feedback") != null ? verdict.get("feedback") : outcome));
                return "WRONG".equals(outcome) ? Feedback.parse(String.valueOf(verdict.get("feedback"))) : null;
            } catch (TimeoutException e) {
                say("no result for " + word + ", retrying with the same guessId");
            } catch (java.util.concurrent.ExecutionException e) {
                return null;
            } finally {
                pending.remove(guessId);
            }
        }
        return null;
    }

    private void onGuessResult(Map<String, Object> result) {
        CompletableFuture<Map<String, Object>> waiting = pending.get(String.valueOf(result.get("guessId")));
        if (waiting != null) {
            waiting.complete(result);
        }
    }

    void finish(String reason) {
        if (done.compareAndSet(false, true)) {
            currentRound = null;
            say("leaving (" + reason + ")");
            if (stomp != null && stomp.isConnected()) {
                stomp.disconnect();
            }
            finished.countDown();
        }
    }

    private void subscribe(String destination, Consumer<Map<String, Object>> handler) {
        stomp.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                handler.accept((Map<String, Object>) payload);
            }
        });
    }

    private void say(String text) {
        System.out.printf("[%s] %s%n", session.nickname(), text);
    }
}
