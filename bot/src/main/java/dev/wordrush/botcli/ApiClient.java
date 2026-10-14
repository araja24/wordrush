package dev.wordrush.botcli;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The two REST calls a bot needs: sign in as a (bot-labelled) guest, and join a room. */
final class ApiClient {

    record Session(String token, String userId, String nickname) {
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();
    private final String server;

    ApiClient(String server) {
        this.server = server.replaceAll("/+$", "");
    }

    Session signIn(String nickname) throws IOException, InterruptedException {
        JsonNode body = post("/api/v1/auth/guest", null, Map.of("nickname", nickname, "bot", true));
        JsonNode user = body.get("user");
        return new Session(body.get("accessToken").asString(), user.get("id").asString(), user.get("nickname").asString());
    }

    void join(Session session, String code) throws IOException, InterruptedException {
        post("/api/v1/rooms/" + code + "/join", session.token(), null);
    }

    private JsonNode post(String path, String token, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server + path))
                .header("Content-Type", "application/json")
                .POST(body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("POST " + path + " failed (" + response.statusCode() + "): " + response.body());
        }
        return json.readTree(response.body());
    }
}
