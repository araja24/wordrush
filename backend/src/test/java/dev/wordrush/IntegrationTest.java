package dev.wordrush;

import dev.wordrush.auth.AuthResponse;
import java.util.Map;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/**
 * Base for full-stack tests: real HTTP on a random port, real Postgres and Redis containers.
 * All subclasses share one Spring context (same config = Spring's test context cache), so the
 * containers start once per test run.
 */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

    protected static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };

    @LocalServerPort
    protected int port;

    /** A client that returns 4xx/5xx responses instead of throwing, so tests can assert on them. */
    protected RestClient http() {
        return RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    protected RestClient http(String token) {
        return http().mutate().defaultHeader("Authorization", "Bearer " + token).build();
    }

    protected AuthResponse signIn(String nickname) {
        return http().post().uri("/api/v1/auth/guest")
                .body(Map.of("nickname", nickname))
                .retrieve().body(AuthResponse.class);
    }

    protected static ResponseEntity<Map<String, Object>> asMap(RestClient.RequestHeadersSpec<?> request) {
        return request.retrieve().toEntity(JSON_MAP);
    }
}
