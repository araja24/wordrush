package dev.wordrush.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

import dev.wordrush.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class AuthIT extends IntegrationTest {

    @Test
    void guestSignInReturnsATokenThatAuthenticatesMe() {
        AuthResponse auth = signIn("alex");

        assertThat(auth.tokenType()).isEqualTo("Bearer");
        assertThat(auth.user().nickname()).isEqualTo("alex");
        assertThat(auth.user().username()).matches("alex#\\d{4}");
        assertThat(auth.user().guest()).isTrue();

        ResponseEntity<Map<String, Object>> me = asMap(http(auth.accessToken()).get().uri("/api/v1/me"));
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).containsEntry("id", auth.user().id().toString())
                .containsEntry("nickname", "alex");
    }

    @Test
    void sameNicknameGetsDifferentUsernames() {
        assertThat(signIn("sam").user().username()).isNotEqualTo(signIn("sam").user().username());
    }

    @Test
    void missingTokenIs401ProblemJson() {
        ResponseEntity<Map<String, Object>> response = asMap(http().get().uri("/api/v1/me"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).startsWith("Bearer");
        assertThat(response.getBody()).containsEntry("code", "UNAUTHENTICATED").containsEntry("status", 401);
    }

    @Test
    void tamperedTokenIs401() {
        String token = signIn("mallory").accessToken();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        ResponseEntity<Map<String, Object>> response = asMap(http(tampered).get().uri("/api/v1/me"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidNicknameIs400WithFieldErrors() {
        ResponseEntity<Map<String, Object>> response = asMap(http().post().uri("/api/v1/auth/guest")
                .body(Map.of("nickname", "x!")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_FAILED");
        assertThat(response.getBody().get("errors")).asInstanceOf(LIST).singleElement()
                .asInstanceOf(MAP).containsEntry("field", "nickname");
    }

    @Test
    void malformedJsonIs400ProblemJson() {
        ResponseEntity<Map<String, Object>> response = asMap(http().post().uri("/api/v1/auth/guest")
                .contentType(MediaType.APPLICATION_JSON)
                .body("{not json"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "BAD_REQUEST");
    }

    @Test
    void metricsEndpointsArePrivate() {
        String token = signIn("curious").accessToken();

        assertThat(asMap(http(token).get().uri("/actuator/prometheus")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(asMap(http().get().uri("/actuator/health")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void openApiDocsArePublic() {
        ResponseEntity<Map<String, Object>> docs = asMap(http().get().uri("/v3/api-docs"));

        assertThat(docs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(docs.getBody().get("paths")).asInstanceOf(MAP).containsKey("/api/v1/auth/guest");
    }
}
