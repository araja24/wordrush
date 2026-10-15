package dev.wordrush.bot;

import static dev.wordrush.StompTestClient.await;
import static dev.wordrush.StompTestClient.ofType;
import static org.assertj.core.api.Assertions.assertThat;

import dev.wordrush.IntegrationTest;
import dev.wordrush.StompTestClient;
import dev.wordrush.auth.AuthResponse;
import dev.wordrush.room.RoomKeys;
import dev.wordrush.room.RoomSnapshot;
import dev.wordrush.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class BotIT extends IntegrationTest {

    @Autowired
    BotService botService;

    @Autowired
    BotAccountCleaner botAccounts;

    @Autowired
    UserRepository users;

    @Autowired
    StringRedisTemplate redis;

    private String createRoom(AuthResponse host, Map<String, Object> settings) {
        return http(host.accessToken()).post().uri("/api/v1/rooms").body(settings)
                .retrieve().body(RoomSnapshot.class).code();
    }

    private ResponseEntity<Map<String, Object>> addBot(AuthResponse caller, String code, String difficulty) {
        return asMap(http(caller.accessToken()).post().uri("/api/v1/rooms/{code}/bots", code)
                .body(Map.of("difficulty", difficulty)));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(Map<String, Object> message) {
        return (Map<String, Object>) message.get("payload");
    }

    @Test
    void aHardBotSolvesTheRoundWhileTheHostWatches() throws Exception {
        AuthResponse host = signIn("watcher");
        String code = createRoom(host, Map.of("totalRounds", 1));

        ResponseEntity<Map<String, Object>> added = addBot(host, code, "HARD");
        assertThat(added.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> players = (List<Map<String, Object>>) added.getBody().get("players");
        Map<String, Object> bot = players.stream().filter(p -> Boolean.TRUE.equals(p.get("bot"))).findFirst().orElseThrow();
        assertThat(bot).containsEntry("nickname", "Ada (hard)");

        try (StompTestClient client = StompTestClient.connect(port, host.accessToken())) {
            BlockingQueue<Map<String, Object>> room = client.subscribe("/topic/rooms/" + code);
            TimeUnit.MILLISECONDS.sleep(150);
            client.send("/app/rooms/" + code + "/start", Map.of());

            List<Map<String, Object>> beforeEnd = new ArrayList<>();
            Map<String, Object> ended = await(room, message -> {
                beforeEnd.add(message);
                return "ROUND_ENDED".equals(message.get("type"));
            });

            assertThat(payload(ended)).containsEntry("reason", "SOLVED").containsEntry("winnerId", bot.get("userId"));
            assertThat(beforeEnd).as("the bot's guesses show up as progress")
                    .anyMatch(m -> "PLAYER_PROGRESS".equals(m.get("type")) && bot.get("userId").equals(payload(m).get("userId")));
            // Bots receive exactly these events; none carried the answer before the round ended.
            String answer = (String) payload(ended).get("answer");
            assertThat(beforeEnd.subList(0, beforeEnd.size() - 1).toString()).doesNotContain(answer);
            assertThat(payload(await(room, ofType("GAME_OVER"))).get("standings").toString())
                    .contains((String) bot.get("userId"));
        }
    }

    @Test
    void eachBotGetsItsOwnFirstName() {
        AuthResponse host = signIn("host");
        String code = createRoom(host, Map.of());
        addBot(host, code, "HARD");

        ResponseEntity<Map<String, Object>> second = addBot(host, code, "MEDIUM");

        assertThat(second.getBody().get("players").toString()).contains("Ada (hard)", "Grace (medium)");
    }

    @Test
    void onlyTheHostCanAddBots() {
        AuthResponse host = signIn("host");
        String code = createRoom(host, Map.of());
        AuthResponse guest = signIn("guest");
        http(guest.accessToken()).post().uri("/api/v1/rooms/{code}/join", code).retrieve().toBodilessEntity();

        assertThat(addBot(guest, code, "EASY").getBody()).containsEntry("code", "NOT_HOST");
    }

    @Test
    void botsTakeSeatsAndRespectCapacity() {
        AuthResponse host = signIn("host");
        String code = createRoom(host, Map.of("maxPlayers", 2));

        assertThat(addBot(host, code, "EASY").getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(addBot(host, code, "EASY").getBody()).containsEntry("code", "ROOM_FULL");
    }

    @Test
    void invalidDifficultyIs400() {
        AuthResponse host = signIn("host");
        String code = createRoom(host, Map.of());

        assertThat(addBot(host, code, "IMPOSSIBLE").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void kickedBotsAndBotsInClosedRoomsAreDropped() {
        AuthResponse host = signIn("host");
        String code = createRoom(host, Map.of());
        int before = botService.activeBots();
        addBot(host, code, "MEDIUM");
        ResponseEntity<Map<String, Object>> withTwo = addBot(host, code, "EASY");
        assertThat(botService.activeBots()).isEqualTo(before + 2);

        @SuppressWarnings("unchecked")
        String easyBot = ((List<Map<String, Object>>) withTwo.getBody().get("players")).stream()
                .filter(p -> String.valueOf(p.get("nickname")).endsWith("(easy)"))
                .map(p -> (String) p.get("userId")).findFirst().orElseThrow();
        http(host.accessToken()).delete().uri("/api/v1/rooms/{code}/players/{id}", code, easyBot)
                .retrieve().toBodilessEntity();
        assertThat(botService.activeBots()).isEqualTo(before + 1);

        // Last human leaves: the room closes and the sweep drops the remaining bot.
        http(host.accessToken()).post().uri("/api/v1/rooms/{code}/leave", code).retrieve().toBodilessEntity();
        botService.dropBotsWithoutARoom();
        assertThat(botService.activeBots()).isEqualTo(before);
    }

    /** Bot accounts belong to one room: once it's gone (closed or expired), so is the account. */
    @Test
    void botAccountsAreDeletedOnceTheirRoomIsGone() {
        AuthResponse host = signIn("host");
        String code = createRoom(host, Map.of());
        @SuppressWarnings("unchecked")
        UUID botId = UUID.fromString(((List<Map<String, Object>>) addBot(host, code, "EASY").getBody().get("players"))
                .stream().filter(p -> Boolean.TRUE.equals(p.get("bot")))
                .map(p -> (String) p.get("userId")).findFirst().orElseThrow());

        // A human joining keeps every seat's pointer alive, so a long lobby can't orphan the bot.
        redis.expire(RoomKeys.userRoom(botId), Duration.ofSeconds(30));
        http(signIn("friend").accessToken()).post().uri("/api/v1/rooms/{code}/join", code)
                .retrieve().toBodilessEntity();
        assertThat(redis.getExpire(RoomKeys.userRoom(botId))).isGreaterThan(60);

        Instant everyoneIsOldEnough = Instant.now().plusSeconds(1);
        botAccounts.deleteBotsWithoutARoom(everyoneIsOldEnough);
        assertThat(users.existsById(botId)).as("still seated: kept").isTrue();

        for (String key : List.of(RoomKeys.room(code), RoomKeys.players(code))) {
            redis.delete(key); // the room expires
        }
        botService.dropBotsWithoutARoom(); // stop the in-app bot now, not on the 30 s sweep mid-way through another test
        botAccounts.deleteBotsWithoutARoom(everyoneIsOldEnough);
        assertThat(users.existsById(botId)).as("room gone: deleted").isFalse();
        assertThat(users.existsById(host.user().id())).as("humans are never touched").isTrue();
    }
}
