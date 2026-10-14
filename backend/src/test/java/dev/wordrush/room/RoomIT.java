package dev.wordrush.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;

import dev.wordrush.IntegrationTest;
import dev.wordrush.auth.AuthResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

class RoomIT extends IntegrationTest {

    @Autowired
    StringRedisTemplate redis;

    private RoomSnapshot createRoom(AuthResponse host, Map<String, Object> settings) {
        return http(host.accessToken()).post().uri("/api/v1/rooms").body(settings)
                .retrieve().body(RoomSnapshot.class);
    }

    private ResponseEntity<Map<String, Object>> join(AuthResponse user, String code) {
        return asMap(http(user.accessToken()).post().uri("/api/v1/rooms/{code}/join", code));
    }

    private ResponseEntity<Map<String, Object>> get(AuthResponse user, String code) {
        return asMap(http(user.accessToken()).get().uri("/api/v1/rooms/{code}", code));
    }

    @Test
    void createAppliesDefaultsAndMakesCallerHost() {
        AuthResponse host = signIn("host");

        ResponseEntity<RoomSnapshot> created = http(host.accessToken()).post().uri("/api/v1/rooms")
                .retrieve().toEntity(RoomSnapshot.class);

        RoomSnapshot room = created.getBody();
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getHeaders().getLocation()).hasPath("/api/v1/rooms/" + room.code());
        assertThat(room.status()).isEqualTo(RoomStatus.LOBBY);
        assertThat(room.hostId()).isEqualTo(host.user().id());
        assertThat(room.settings()).isEqualTo(new RoomSettings(5, 90, 6));
        assertThat(room.players()).singleElement().satisfies(p -> {
            assertThat(p.host()).isTrue();
            assertThat(p.nickname()).isEqualTo("host");
        });
    }

    @Test
    void secondPlayerJoinsCaseInsensitivelyAndRejoinIsIdempotent() {
        AuthResponse host = signIn("host");
        AuthResponse guest = signIn("guest");
        String code = createRoom(host, Map.of()).code();

        assertThat(join(guest, code.toLowerCase()).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map<String, Object>> again = join(guest, code);

        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody().get("players")).asInstanceOf(LIST).hasSize(2);
        assertThat(asMap(http(guest.accessToken()).get().uri("/api/v1/me/room")).getBody())
                .containsEntry("code", code);
    }

    @Test
    void fullRoomRejectsWith409() {
        AuthResponse host = signIn("host");
        String code = createRoom(host, Map.of("maxPlayers", 2)).code();
        join(signIn("second"), code);

        ResponseEntity<Map<String, Object>> third = join(signIn("third"), code);

        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(third.getBody()).containsEntry("code", "ROOM_FULL");
    }

    @Test
    void cannotJoinAGameInProgress() {
        AuthResponse host = signIn("host");
        String code = createRoom(host, Map.of()).code();
        redis.opsForHash().put(RoomKeys.room(code), "status", RoomStatus.IN_ROUND.name());

        ResponseEntity<Map<String, Object>> late = join(signIn("late"), code);

        assertThat(late.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(late.getBody()).containsEntry("code", "GAME_IN_PROGRESS");
    }

    @Test
    void onlyMembersCanSeeARoom() {
        AuthResponse host = signIn("host");
        String code = createRoom(host, Map.of()).code();

        assertThat(get(signIn("stranger"), code).getBody()).containsEntry("code", "NOT_A_MEMBER");
        assertThat(get(host, "ZZZZZZ").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(host, "bad!").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void hostLeavingPassesHostToEarliestRemainingPlayerAndLastOneOutClosesTheRoom() {
        AuthResponse host = signIn("host");
        AuthResponse second = signIn("second");
        AuthResponse third = signIn("third");
        String code = createRoom(host, Map.of()).code();
        join(second, code);
        join(third, code);

        http(host.accessToken()).post().uri("/api/v1/rooms/{code}/leave", code).retrieve().toBodilessEntity();
        assertThat(get(second, code).getBody()).containsEntry("hostId", second.user().id().toString());

        http(second.accessToken()).post().uri("/api/v1/rooms/{code}/leave", code).retrieve().toBodilessEntity();
        http(third.accessToken()).post().uri("/api/v1/rooms/{code}/leave", code).retrieve().toBodilessEntity();
        assertThat(get(third, code).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(redis.hasKey(RoomKeys.players(code))).isFalse();
    }

    @Test
    void onlyTheHostCanKick() {
        AuthResponse host = signIn("host");
        AuthResponse victim = signIn("victim");
        String code = createRoom(host, Map.of()).code();
        join(victim, code);

        ResponseEntity<Map<String, Object>> byGuest = asMap(http(victim.accessToken()).delete()
                .uri("/api/v1/rooms/{code}/players/{id}", code, host.user().id()));
        assertThat(byGuest.getBody()).containsEntry("code", "NOT_HOST");

        ResponseEntity<Map<String, Object>> byHost = asMap(http(host.accessToken()).delete()
                .uri("/api/v1/rooms/{code}/players/{id}", code, victim.user().id()));
        assertThat(byHost.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(get(victim, code).getBody()).containsEntry("code", "NOT_A_MEMBER");
    }

    @Test
    void joiningAnotherRoomLeavesTheFirst() {
        AuthResponse player = signIn("hopper");
        String first = createRoom(signIn("hostA"), Map.of()).code();
        String second = createRoom(signIn("hostB"), Map.of()).code();
        join(player, first);

        join(player, second);

        assertThat(get(player, first).getBody()).containsEntry("code", "NOT_A_MEMBER");
        assertThat(get(player, second).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void invalidSettingsAre400WithFieldErrors() {
        AuthResponse host = signIn("host");

        ResponseEntity<Map<String, Object>> response = asMap(http(host.accessToken()).post().uri("/api/v1/rooms")
                .body(Map.of("totalRounds", 50, "maxPlayers", 1)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        List<Object> fields = ((List<?>) response.getBody().get("errors")).stream()
                .map(error -> ((Map<?, ?>) error).get("field"))
                .map(Object.class::cast)
                .toList();
        assertThat(fields).containsExactlyInAnyOrder("totalRounds", "maxPlayers");
    }

    @Test
    void noCurrentRoomIs204() {
        assertThat(http(signIn("loner").accessToken()).get().uri("/api/v1/me/room")
                .retrieve().toBodilessEntity().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    /** The Lua join script makes "check capacity, then add" atomic: no overfilled rooms under contention. */
    @Test
    void concurrentJoinsNeverOverfillARoom() throws Exception {
        AuthResponse host = signIn("host");
        String code = createRoom(host, Map.of("maxPlayers", 3)).code();
        List<AuthResponse> racers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            racers.add(signIn("racer" + i));
        }

        CountDownLatch start = new CountDownLatch(1);
        List<Future<HttpStatusCode>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (AuthResponse racer : racers) {
                results.add(pool.submit(() -> {
                    start.await();
                    return join(racer, code).getStatusCode();
                }));
            }
            start.countDown();
        }

        long joined = 0;
        long full = 0;
        for (Future<HttpStatusCode> result : results) {
            HttpStatusCode status = result.get();
            if (status.equals(HttpStatus.OK)) {
                joined++;
            } else if (status.equals(HttpStatus.CONFLICT)) {
                full++;
            }
        }
        assertThat(joined).isEqualTo(2);
        assertThat(full).isEqualTo(8);
        assertThat(get(host, code).getBody().get("players")).asInstanceOf(LIST).hasSize(3);
    }
}
