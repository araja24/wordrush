package dev.wordrush.chat;

import static dev.wordrush.StompTestClient.await;
import static dev.wordrush.StompTestClient.ofType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import dev.wordrush.IntegrationTest;
import dev.wordrush.StompTestClient;
import dev.wordrush.auth.AuthResponse;
import dev.wordrush.room.RoomSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChatIT extends IntegrationTest {

    private final List<StompTestClient> clients = new ArrayList<>();

    private record Seat(AuthResponse auth, StompTestClient client, BlockingQueue<Map<String, Object>> room,
                        BlockingQueue<Map<String, Object>> errors) {

        void say(String code, String text) {
            client.send("/app/rooms/" + code + "/chat", Map.of("text", text, "reaction", false));
        }

        void react(String code, String text) {
            client.send("/app/rooms/" + code + "/chat", Map.of("text", text, "reaction", true));
        }
    }

    @AfterEach
    void disconnect() {
        clients.forEach(StompTestClient::close);
    }

    private String createRoom(AuthResponse host) {
        return http(host.accessToken()).post().uri("/api/v1/rooms").body(Map.of())
                .retrieve().body(RoomSnapshot.class).code();
    }

    private Seat seat(AuthResponse auth, String code) throws Exception {
        StompTestClient client = StompTestClient.connect(port, auth.accessToken());
        clients.add(client);
        Seat seat = new Seat(auth, client, client.subscribe("/topic/rooms/" + code),
                client.subscribe("/user/queue/errors"));
        TimeUnit.MILLISECONDS.sleep(150); // let SUBSCRIBE frames reach the broker
        return seat;
    }

    private Seat join(String nickname, String code) throws Exception {
        AuthResponse auth = signIn(nickname);
        http(auth.accessToken()).post().uri("/api/v1/rooms/{code}/join", code).retrieve().toBodilessEntity();
        return seat(auth, code);
    }

    private static Map<String, Object> payload(Map<String, Object> message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        return payload;
    }

    @Test
    void membersSeeMessagesWithTheServersNicknameAndCleanedText() throws Exception {
        AuthResponse hostAuth = signIn("host");
        String code = createRoom(hostAuth);
        Seat host = seat(hostAuth, code);
        Seat guest = join("guest", code);

        guest.say(code, "  ready‮ when\nyou are  ");

        Map<String, Object> chat = payload(await(host.room(), ofType("CHAT")));
        assertThat(chat)
                .containsEntry("userId", guest.auth().user().id().toString())
                .containsEntry("nickname", "guest")
                .containsEntry("text", "ready when you are")
                .containsEntry("reaction", false);
    }

    @Test
    void emptyAndOverlongMessagesAreRejected() throws Exception {
        AuthResponse hostAuth = signIn("host");
        String code = createRoom(hostAuth);
        Seat host = seat(hostAuth, code);

        host.say(code, "   ");
        assertThat(await(host.errors(), m -> true)).containsEntry("code", "INVALID_CHAT");

        host.say(code, "x".repeat(ChatService.MAX_LENGTH + 1));
        assertThat(await(host.errors(), m -> true)).containsEntry("code", "INVALID_CHAT");
        assertThat(host.room().poll(300, TimeUnit.MILLISECONDS)).as("nothing broadcast").isNull();
    }

    @Test
    void reactionsMustComeFromTheFixedSet() throws Exception {
        AuthResponse hostAuth = signIn("host");
        String code = createRoom(hostAuth);
        Seat host = seat(hostAuth, code);

        host.react(code, "the word is CRANE");
        assertThat(await(host.errors(), m -> true)).containsEntry("code", "INVALID_CHAT");

        host.react(code, "gg");
        assertThat(payload(await(host.room(), ofType("CHAT"))))
                .containsEntry("text", "gg").containsEntry("reaction", true);
    }

    /** Free text mid-round would let players pass each other hints; reactions carry no letters. */
    @Test
    void duringARoundOnlyReactionsAreAllowed() throws Exception {
        AuthResponse hostAuth = signIn("host");
        String code = createRoom(hostAuth);
        Seat host = seat(hostAuth, code);
        Seat guest = join("guest", code);

        host.client().send("/app/rooms/" + code + "/start", Map.of());
        await(guest.room(), ofType("ROUND_STARTED"));

        guest.say(code, "starts with C");
        assertThat(await(guest.errors(), m -> true)).containsEntry("code", "CHAT_LOCKED");

        guest.react(code, "so close");
        assertThat(payload(await(host.room(), ofType("CHAT")))).containsEntry("text", "so close");
    }

    @Test
    void nonMembersCannotPost() throws Exception {
        AuthResponse hostAuth = signIn("host");
        String code = createRoom(hostAuth);
        Seat host = seat(hostAuth, code);
        // Not subscribed to the room topic: the server refuses that subscription outright.
        StompTestClient outsider = StompTestClient.connect(port, signIn("outsider").accessToken());
        clients.add(outsider);
        BlockingQueue<Map<String, Object>> outsiderErrors = outsider.subscribe("/user/queue/errors");
        TimeUnit.MILLISECONDS.sleep(150);

        outsider.send("/app/rooms/" + code + "/chat", Map.of("text", "hi", "reaction", false));

        assertThat(await(outsiderErrors, m -> true)).containsEntry("code", "NOT_A_MEMBER");
        assertThat(host.room().poll(300, TimeUnit.MILLISECONDS)).as("nothing broadcast").isNull();
    }

    @Test
    void floodingIsRateLimited() throws Exception {
        AuthResponse hostAuth = signIn("host");
        String code = createRoom(hostAuth);
        Seat host = seat(hostAuth, code);

        for (int i = 0; i <= ChatRateLimiter.MAX_PER_WINDOW; i++) {
            host.say(code, "spam " + i);
        }

        assertThat(await(host.errors(), m -> true)).containsEntry("code", "CHAT_RATE_LIMITED");
        for (int i = 0; i < ChatRateLimiter.MAX_PER_WINDOW; i++) {
            assertThat(payload(await(host.room(), ofType("CHAT")))).asInstanceOf(MAP).containsKey("text");
        }
        assertThat(host.room().poll(300, TimeUnit.MILLISECONDS)).as("over-limit message not broadcast").isNull();
    }
}
