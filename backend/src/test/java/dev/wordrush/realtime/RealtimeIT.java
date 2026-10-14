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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

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
