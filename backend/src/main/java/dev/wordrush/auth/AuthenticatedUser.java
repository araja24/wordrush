package dev.wordrush.auth;

import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller, as read from a verified JWT; no database lookup per request.
 * {@link #getName()} returns the user id, which is what Spring uses to route
 * {@code /user/queue/...} WebSocket messages to this player.
 */
public record AuthenticatedUser(UUID id, String nickname, boolean bot) implements AuthenticatedPrincipal {

    static final String CLAIM_NICKNAME = "nickname";
    static final String CLAIM_BOT = "bot";

    public static AuthenticatedUser from(Jwt jwt) {
        Boolean bot = jwt.getClaim(CLAIM_BOT);
        return new AuthenticatedUser(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(CLAIM_NICKNAME),
                Boolean.TRUE.equals(bot));
    }

    @Override
    public String getName() {
        return id.toString();
    }
}
