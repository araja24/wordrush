package dev.wordrush;

import java.lang.reflect.Type;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** A real STOMP-over-WebSocket client for integration tests; messages land in blocking queues. */
public final class StompTestClient implements AutoCloseable {

    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final WebSocketStompClient client;
    private final StompSession session;
    private final BlockingQueue<String> errors;

    private StompTestClient(WebSocketStompClient client, StompSession session, BlockingQueue<String> errors) {
        this.client = client;
        this.session = session;
        this.errors = errors;
    }

    public static StompTestClient connect(int port, String token) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        StompHeaders connectHeaders = new StompHeaders();
        if (token != null) {
            connectHeaders.add("Authorization", "Bearer " + token);
        }
        BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                errors.add(String.valueOf(headers.getFirst("message"))); // ERROR frames
            }

            @Override
            public void handleException(StompSession s, StompCommand command, StompHeaders headers,
                                        byte[] payload, Throwable exception) {
                errors.add(exception.toString());
            }

            @Override
            public void handleTransportError(StompSession s, Throwable exception) {
                errors.add(exception.toString());
            }
        };
        StompSession session = client
                .connectAsync(URI.create("ws://localhost:" + port + "/ws"), (WebSocketHttpHeaders) null,
                        connectHeaders, handler)
                .get(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        return new StompTestClient(client, session, errors);
    }

    @SuppressWarnings("unchecked")
    public BlockingQueue<Map<String, Object>> subscribe(String destination) {
        BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.add((Map<String, Object>) payload);
            }
        });
        return messages;
    }

    public void send(String destination, Object payload) {
        session.send(destination, payload);
    }

    public boolean isConnected() {
        return session.isConnected();
    }

    /** Waits for the next ERROR frame (or transport error) message. */
    public String awaitError() throws InterruptedException {
        return errors.poll(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Returns the first message matching {@code match}, discarding earlier non-matching ones. */
    public static Map<String, Object> await(BlockingQueue<Map<String, Object>> queue,
                                            Predicate<Map<String, Object>> match) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        List<Map<String, Object>> skipped = new ArrayList<>();
        while (true) {
            long remaining = deadline - System.nanoTime();
            Map<String, Object> message = queue.poll(Math.max(remaining, 0), TimeUnit.NANOSECONDS);
            if (message == null) {
                throw new AssertionError("Timed out waiting for message; skipped: " + skipped);
            }
            if (match.test(message)) {
                return message;
            }
            skipped.add(message);
        }
    }

    public static Predicate<Map<String, Object>> ofType(String type) {
        return message -> type.equals(message.get("type"));
    }

    @Override
    public void close() {
        try {
            if (session.isConnected()) {
                session.disconnect();
            }
        } catch (RuntimeException alreadyClosedByServer) {
            // expected after the server sends an ERROR frame
        }
        client.stop();
    }
}
