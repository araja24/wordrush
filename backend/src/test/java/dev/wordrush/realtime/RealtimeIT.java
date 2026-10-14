package dev.wordrush.realtime;

import static dev.wordrush.StompTestClient.await;
import static dev.wordrush.StompTestClient.ofType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import dev.wordrush.IntegrationTest;
import dev.wordrush.StompTestClient;
import dev.wordrush.auth.AuthResponse;
import dev.wordrush.room.RoomSnapshot;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

class RealtimeIT extends IntegrationTest {

    private String createRoom(AuthResponse host) {
        return http(host.accessToken()).post().uri("/api/v1/rooms").retrieve().body(RoomSnapshot.class).code();
    }

    private void join(AuthResponse user, String code) {
        http(user.accessToken()).post().uri("/api/v1/rooms/{code}/join", code).retrieve().toBodilessEntity();
    }

    @Test
    void connectWithoutTokenIsRefused() {
        assertThatThrownBy(() -> StompTestClient.connect(port, null)).isNotNull();
    }

    @Test
    void connectWithGarbageTokenIsRefused() {
        assertThatThrownBy(() -> StompTestClient.connect(port, "not-a-jwt")).isNotNull();
    }

    @Test
    void membersSeeJoinsAndLeavesInSequence() throws Exception {
        AuthResponse host = signIn("host");
        AuthResponse guest = signIn("guest");
        String code = createRoom(host);

        try (StompTestClient hostClient = StompTestClient.connect(port, host.accessToken())) {
            BlockingQueue<Map<String, Object>> room = hostClient.subscribe("/topic/rooms/" + code);
            TimeUnit.MILLISECONDS.sleep(200); // let the SUBSCRIBE reach the broker before events fire

            join(guest, code);
            Map<String, Object> joined = await(room, ofType("PLAYER_JOINED"));
            assertThat(joined).containsEntry("roomCode", code).containsEntry("seq", 1);
            assertThat(joined.get("payload")).asInstanceOf(MAP)
                    .containsEntry("userId", guest.user().id().toString())
                    .containsEntry("nickname", "guest");

            http(guest.accessToken()).post().uri("/api/v1/rooms/{code}/leave", code).retrieve().toBodilessEntity();
            Map<String, Object> left = await(room, ofType("PLAYER_LEFT"));
            assertThat(left).containsEntry("seq", 2);
        }
    }

    @Test
    void hostLeavingAnnouncesTheNewHost() throws Exception {
        AuthResponse host = signIn("host");
        AuthResponse guest = signIn("guest");
        String code = createRoom(host);
        join(guest, code);

        try (StompTestClient guestClient = StompTestClient.connect(port, guest.accessToken())) {
            BlockingQueue<Map<String, Object>> room = guestClient.subscribe("/topic/rooms/" + code);
            TimeUnit.MILLISECONDS.sleep(200);

            http(host.accessToken()).post().uri("/api/v1/rooms/{code}/leave", code).retrieve().toBodilessEntity();

            assertThat(await(room, ofType("HOST_CHANGED")).get("payload")).asInstanceOf(MAP)
                    .containsEntry("hostId", guest.user().id().toString());
        }
    }

    @Test
    void kickedPlayerStopsReceivingRoomEvents() throws Exception {
        AuthResponse host = signIn("host");
        AuthResponse victim = signIn("victim");
        String code = createRoom(host);
        join(victim, code);

        try (StompTestClient hostClient = StompTestClient.connect(port, host.accessToken());
             StompTestClient victimClient = StompTestClient.connect(port, victim.accessToken())) {
            BlockingQueue<Map<String, Object>> hostRoom = hostClient.subscribe("/topic/rooms/" + code);
            BlockingQueue<Map<String, Object>> victimRoom = victimClient.subscribe("/topic/rooms/" + code);
            BlockingQueue<Map<String, Object>> victimKicked = victimClient.subscribe("/user/queue/kicked");
            TimeUnit.MILLISECONDS.sleep(200);

            http(host.accessToken()).delete().uri("/api/v1/rooms/{code}/players/{id}", code, victim.user().id())
                    .retrieve().toBodilessEntity();
            assertThat(await(hostRoom, ofType("PLAYER_LEFT")).get("payload")).asInstanceOf(MAP)
                    .containsEntry("kicked", true);
            assertThat(await(victimKicked, message -> true)).containsEntry("roomCode", code);

            join(signIn("newcomer"), code);
            await(hostRoom, ofType("PLAYER_JOINED"));
            assertThat(victimRoom.poll(500, TimeUnit.MILLISECONDS)).as("ex-member still receiving").isNull();
        }
    }

    /** STOMP's "STOMP" command is an alias for CONNECT and must be authenticated the same way. */
    @Test
    void stompAliasFrameWithoutTokenIsRefused() throws Exception {
        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        WebSocketSession raw = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                frames.add(message.getPayload());
            }
        }, "ws://localhost:" + port + "/ws").get(5, TimeUnit.SECONDS);
        try {
            raw.sendMessage(new TextMessage("STOMP\naccept-version:1.2\nhost:localhost\n\n\0"));

            assertThat(frames.poll(5, TimeUnit.SECONDS)).startsWith("ERROR");
        } finally {
            if (raw.isOpen()) {
                raw.close();
            }
        }
    }

    @Test
    void cannotSubscribeToSomeoneElsesRoom() throws Exception {
        String code = createRoom(signIn("host"));

        try (StompTestClient stranger = StompTestClient.connect(port, signIn("stranger").accessToken())) {
            stranger.subscribe("/topic/rooms/" + code);

            assertThat(stranger.awaitError()).isNotNull();
        }
    }

    @Test
    void cannotForgeBroadcastsBySendingToATopic() throws Exception {
        AuthResponse host = signIn("host");
        String code = createRoom(host);

        try (StompTestClient attacker = StompTestClient.connect(port, host.accessToken())) {
            attacker.send("/topic/rooms/" + code, Map.of("type", "ROUND_ENDED", "payload", Map.of("winnerId", "me")));

            assertThat(attacker.awaitError()).isNotNull();
        }
    }
}
