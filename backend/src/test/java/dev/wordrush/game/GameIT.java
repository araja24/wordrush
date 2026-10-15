package dev.wordrush.game;

import static dev.wordrush.StompTestClient.await;
import static dev.wordrush.StompTestClient.ofType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import dev.wordrush.IntegrationTest;
import dev.wordrush.StompTestClient;
import dev.wordrush.auth.AuthResponse;
import dev.wordrush.auth.AuthenticatedUser;
import dev.wordrush.room.RoomKeys;
import dev.wordrush.room.RoomSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class GameIT extends IntegrationTest {

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    GameService gameService;

    private final List<StompTestClient> clients = new ArrayList<>();

    /** A signed-in player with a live STOMP connection and queues for each destination. */
    private record Seat(AuthResponse auth, StompTestClient client, BlockingQueue<Map<String, Object>> room,
                        BlockingQueue<Map<String, Object>> results, BlockingQueue<Map<String, Object>> errors) {

        UUID id() {
            return auth.user().id();
        }

        void guess(String code, Object roundId, String word) {
            guess(code, roundId, word, UUID.randomUUID());
        }

        void guess(String code, Object roundId, String word, UUID guessId) {
            client.send("/app/rooms/" + code + "/guess",
                    Map.of("guessId", guessId.toString(), "roundId", roundId.toString(), "word", word));
        }
    }

    @AfterEach
    void disconnect() {
        clients.forEach(StompTestClient::close);
    }

    private Seat seat(String nickname, String code) throws Exception {
        AuthResponse auth = signIn(nickname);
        if (code != null) {
            http(auth.accessToken()).post().uri("/api/v1/rooms/{code}/join", code).retrieve().toBodilessEntity();
        }
        return connect(auth, code);
    }

    private Seat connect(AuthResponse auth, String code) throws Exception {
        StompTestClient client = StompTestClient.connect(port, auth.accessToken());
        clients.add(client);
        Seat seat = new Seat(auth, client, client.subscribe("/topic/rooms/" + code),
                client.subscribe("/user/queue/guess-result"), client.subscribe("/user/queue/errors"));
        TimeUnit.MILLISECONDS.sleep(150); // let SUBSCRIBE frames reach the broker
        return seat;
    }

    private String createRoom(AuthResponse host, Map<String, Object> settings) {
        return http(host.accessToken()).post().uri("/api/v1/rooms").body(settings)
                .retrieve().body(RoomSnapshot.class).code();
    }

    private String answer(String code) {
        return (String) redis.opsForHash().get(RoomKeys.round(code), "answer");
    }

    private static String wrongWord(String answer) {
        return answer.equals("CRANE") ? "SLATE" : "CRANE";
    }

    private static Map<String, Object> payload(Map<String, Object> message) {
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        return payload;
    }

    @Test
    void twoPlayersPlayAFullTwoRoundGame() throws Exception {
        AuthResponse hostAuth = signIn("host");
        String code = createRoom(hostAuth, Map.of("totalRounds", 2));
        Seat host = connect(hostAuth, code);
        Seat guest = seat("guest", code);

        host.client().send("/app/rooms/" + code + "/start", Map.of());
        assertThat(payload(await(guest.room(), ofType("COUNTDOWN")))).containsEntry("roundNo", 1);
        Map<String, Object> started = payload(await(guest.room(), ofType("ROUND_STARTED")));
        Object roundId = started.get("roundId");
        String answer = answer(code);

        // Guest guesses wrong: they see letters + colours, the host sees colours only.
        guest.guess(code, roundId, wrongWord(answer));
        Map<String, Object> wrong = await(guest.results(), m -> true);
        assertThat(wrong).containsEntry("outcome", "WRONG").containsEntry("attempt", 1);
        assertThat((String) wrong.get("feedback")).hasSize(5);
        Map<String, Object> progress = payload(await(host.room(), ofType("PLAYER_PROGRESS")));
        assertThat(progress).containsEntry("userId", guest.id().toString()).doesNotContainKey("word");

        // Host solves it on their first guess: 200 points.
        host.guess(code, roundId, answer);
        assertThat(await(host.results(), m -> true)).containsEntry("outcome", "WON").containsEntry("points", 200);
        Map<String, Object> ended = payload(await(guest.room(), ofType("ROUND_ENDED")));
        assertThat(ended).containsEntry("answer", answer).containsEntry("reason", "SOLVED")
                .containsEntry("winnerId", host.id().toString());
        assertThat(((Number) ended.get("solveMillis")).longValue()).as("time from round start to the win").isPositive();
        assertThat(payload(await(guest.room(), ofType("LEADERBOARD"))).get("standings")).asInstanceOf(LIST)
                .first().asInstanceOf(MAP).containsEntry("userId", host.id().toString()).containsEntry("rank", 1);

        // Round 2 starts by itself; the guest wins it.
        assertThat(payload(await(guest.room(), ofType("COUNTDOWN")))).containsEntry("roundNo", 2);
        Object round2 = payload(await(guest.room(), ofType("ROUND_STARTED"))).get("roundId");
        assertThat(round2).isNotEqualTo(roundId);
        String answer2 = answer(code);
        assertThat(answer2).as("answers don't repeat within a game").isNotEqualTo(answer);
        guest.guess(code, round2, answer2);
        assertThat(await(guest.results(), m -> "WON".equals(m.get("outcome")))).containsEntry("points", 200);

        // Tied on 200 each: both rank 1.
        Map<String, Object> gameOver = payload(await(host.room(), ofType("GAME_OVER")));
        assertThat(gameOver.get("standings")).asInstanceOf(LIST).hasSize(2)
                .allSatisfy(row -> assertThat(row).asInstanceOf(MAP).containsEntry("score", 200).containsEntry("rank", 1));

        // Host can bring everyone back to the lobby.
        host.client().send("/app/rooms/" + code + "/playAgain", Map.of());
        await(guest.room(), ofType("RETURNED_TO_LOBBY"));
    }

    @Test
    void wordsNotInTheDictionaryDontCostAnAttempt() throws Exception {
        AuthResponse hostAuth = signIn("solo");
        String code = createRoom(hostAuth, Map.of());
        Seat host = connect(hostAuth, code);
        host.client().send("/app/rooms/" + code + "/start", Map.of());
        Object roundId = payload(await(host.room(), ofType("ROUND_STARTED"))).get("roundId");

        host.guess(code, roundId, "xqzvk");

        assertThat(await(host.results(), m -> true)).containsEntry("outcome", "NOT_A_WORD").containsEntry("attempt", 0);
        assertThat(redis.opsForHash().get(RoomKeys.attempts(code), host.id().toString())).isNull();
    }

    @Test
    void resendingTheSameGuessIdIsIdempotent() throws Exception {
        AuthResponse hostAuth = signIn("retry");
        String code = createRoom(hostAuth, Map.of());
        Seat host = connect(hostAuth, code);
        host.client().send("/app/rooms/" + code + "/start", Map.of());
        Object roundId = payload(await(host.room(), ofType("ROUND_STARTED"))).get("roundId");
        String word = wrongWord(answer(code));
        UUID guessId = UUID.randomUUID();

        host.guess(code, roundId, word, guessId);
        host.guess(code, roundId, word, guessId); // e.g. client retried after a network blip

        Map<String, Object> first = await(host.results(), m -> true);
        Map<String, Object> second = await(host.results(), m -> true);
        assertThat(second).isEqualTo(first).containsEntry("attempt", 1);
        assertThat(redis.opsForHash().get(RoomKeys.attempts(code), host.id().toString())).isEqualTo("1");
        await(host.room(), ofType("PLAYER_PROGRESS"));
        assertThat(host.room().poll(300, TimeUnit.MILLISECONDS)).as("no second PLAYER_PROGRESS").isNull();
    }

    /**
     * Regression: replaying a guessId with a different word used to return colours computed for the
     * new word without spending an attempt, i.e. unlimited free feedback (an answer oracle).
     */
    @Test
    void replayingAGuessIdWithADifferentWordRevealsNothing() throws Exception {
        AuthResponse hostAuth = signIn("cheater");
        String code = createRoom(hostAuth, Map.of());
        AuthenticatedUser cheater = new AuthenticatedUser(hostAuth.user().id(), "cheater", false);
        gameService.start(cheater, code);
        UUID roundId = awaitRound(code);
        String answer = answer(code);
        UUID guessId = UUID.randomUUID();

        GuessResult honest = gameService.guess(cheater, code, new GuessRequest(guessId, roundId, wrongWord(answer)));
        assertThat(honest.outcome()).isEqualTo(GuessOutcome.WRONG);

        assertThatThrownBy(() -> gameService.guess(cheater, code, new GuessRequest(guessId, roundId, answer)))
                .isInstanceOf(GameException.class)
                .hasMessageContaining("already used");
        assertThat(redis.opsForHash().get(RoomKeys.attempts(code), cheater.id().toString())).isEqualTo("1");
        assertThat(redis.opsForHash().get(RoomKeys.round(code), "winnerId")).isNull();

        GuessResult retry = gameService.guess(cheater, code, new GuessRequest(guessId, roundId, wrongWord(answer)));
        assertThat(retry).isEqualTo(honest); // a genuine retry still gets the original result
    }

    @Test
    void onlyTheHostCanStart() throws Exception {
        String code = createRoom(signIn("host"), Map.of());
        Seat guest = seat("guest", code);

        guest.client().send("/app/rooms/" + code + "/start", Map.of());

        assertThat(await(guest.errors(), m -> true)).containsEntry("code", "NOT_HOST");
    }

    @Test
    void roundEndsOnTimeout() throws Exception {
        AuthResponse hostAuth = signIn("slow");
        String code = createRoom(hostAuth, Map.of());
        redis.opsForHash().put(RoomKeys.room(code), "roundSeconds", "1"); // below the API minimum, test only
        Seat host = connect(hostAuth, code);

        host.client().send("/app/rooms/" + code + "/start", Map.of());

        Map<String, Object> ended = payload(await(host.room(), ofType("ROUND_ENDED")));
        assertThat(ended).containsEntry("reason", "TIMEOUT").containsEntry("winnerId", null).containsEntry("solveMillis", null)
                .containsEntry("answer", answer(code));
    }

    @Test
    void roundEndsWhenEveryoneIsOutOfGuesses() throws Exception {
        AuthResponse hostAuth = signIn("unlucky");
        String code = createRoom(hostAuth, Map.of());
        Seat host = connect(hostAuth, code);
        host.client().send("/app/rooms/" + code + "/start", Map.of());
        Object roundId = payload(await(host.room(), ofType("ROUND_STARTED"))).get("roundId");
        String wrong = wrongWord(answer(code));

        for (int i = 0; i < 6; i++) {
            host.guess(code, roundId, wrong);
            await(host.results(), m -> true);
        }
        host.guess(code, roundId, wrong);

        assertThat(payload(await(host.room(), ofType("ROUND_ENDED")))).containsEntry("reason", "ALL_FAILED");
        assertThat(await(host.results(), m -> true)).containsEntry("outcome", "ROUND_OVER");
    }

    @Test
    void snapshotHidesOpponentLettersAndTheAnswer() throws Exception {
        AuthResponse hostAuth = signIn("host");
        String code = createRoom(hostAuth, Map.of());
        Seat host = connect(hostAuth, code);
        Seat guest = seat("guest", code);
        host.client().send("/app/rooms/" + code + "/start", Map.of());
        Object roundId = payload(await(host.room(), ofType("ROUND_STARTED"))).get("roundId");
        String answer = answer(code);
        String wrong = wrongWord(answer);
        guest.guess(code, roundId, wrong);
        await(guest.results(), m -> true);

        BlockingQueue<Map<String, Object>> snapshots = host.client().subscribe("/app/rooms/" + code + "/state");
        Map<String, Object> snapshot = await(snapshots, m -> true);

        assertThat(snapshot.toString()).doesNotContain(answer).doesNotContain(wrong);
        assertThat(snapshot.get("round")).asInstanceOf(MAP)
                .containsEntry("result", null)
                .extractingByKey("opponentFeedback").asInstanceOf(MAP).containsKey(guest.id().toString());

        BlockingQueue<Map<String, Object>> guestSnapshots = guest.client().subscribe("/app/rooms/" + code + "/state");
        assertThat(await(guestSnapshots, m -> true).get("round")).asInstanceOf(MAP)
                .extractingByKey("yourGuesses").asInstanceOf(LIST).singleElement().asInstanceOf(MAP)
                .containsEntry("word", wrong);
    }

    /**
     * Tie-breaking: eight players submit the correct word at the same instant. The submit_guess
     * Lua script is the linearization point, so exactly one WON is possible.
     */
    @Test
    void simultaneousCorrectGuessesProduceExactlyOneWinner() throws Exception {
        for (int trial = 0; trial < 10; trial++) {
            AuthResponse hostAuth = signIn("host");
            String code = createRoom(hostAuth, Map.of("maxPlayers", 8));
            List<AuthenticatedUser> players = new ArrayList<>();
            players.add(new AuthenticatedUser(hostAuth.user().id(), "host", false));
            for (int i = 1; i < 8; i++) {
                AuthResponse auth = signIn("p" + i);
                http(auth.accessToken()).post().uri("/api/v1/rooms/{code}/join", code).retrieve().toBodilessEntity();
                players.add(new AuthenticatedUser(auth.user().id(), "p" + i, false));
            }
            gameService.start(players.getFirst(), code);
            UUID roundId = awaitRound(code);
            String answer = answer(code);

            CountDownLatch go = new CountDownLatch(1);
            List<Future<GuessResult>> results = new ArrayList<>();
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                for (AuthenticatedUser player : players) {
                    results.add(pool.submit(() -> {
                        go.await();
                        return gameService.guess(player, code, new GuessRequest(UUID.randomUUID(), roundId, answer));
                    }));
                }
                go.countDown();
            }

            int winners = 0;
            for (Future<GuessResult> result : results) {
                GuessOutcome outcome = result.get().outcome();
                assertThat(outcome).isIn(GuessOutcome.WON, GuessOutcome.CORRECT_LATE, GuessOutcome.ROUND_OVER);
                if (outcome == GuessOutcome.WON) {
                    winners++;
                }
            }
            assertThat(winners).as("trial %d", trial).isEqualTo(1);
        }
    }

    private UUID awaitRound(String code) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Object roundId = redis.opsForHash().get(RoomKeys.round(code), "roundId");
            if (roundId != null) {
                return UUID.fromString((String) roundId);
            }
            TimeUnit.MILLISECONDS.sleep(20);
        }
        throw new AssertionError("Round never started in room " + code);
    }
}
