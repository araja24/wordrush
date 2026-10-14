package dev.wordrush.realtime;

import dev.wordrush.auth.AuthenticatedUser;
import dev.wordrush.room.RoomRepository;
import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

/**
 * Authentication and authorization for STOMP frames.
 *
 * <p>Browsers can't add an Authorization header to the WebSocket handshake, so the HTTP upgrade
 * at /ws is public and the JWT travels in the STOMP CONNECT frame instead. Once CONNECT sets the
 * user, Spring attaches it to every later frame of that session.
 *
 * <p>Authorization is per destination: you may only subscribe to rooms you're in, and you may
 * only SEND to /app. The second rule matters: with a simple broker, a SEND to /topic/rooms/X
 * would otherwise be relayed to the whole room as a forged event.
 */
@Component
class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";
    private static final Pattern ROOM_TOPIC = Pattern.compile("^/topic/rooms/([A-Z0-9]{6})$");
    private static final Pattern ROOM_APP = Pattern.compile("^/app/rooms/([A-Z0-9]{6})/[A-Za-z]+$");

    private final JwtDecoder jwtDecoder;
    private final RoomRepository rooms;

    StompAuthChannelInterceptor(JwtDecoder jwtDecoder, RoomRepository rooms) {
        this.jwtDecoder = jwtDecoder;
        this.rooms = rooms;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
            case SUBSCRIBE -> authorizeSubscribe(user(accessor), accessor.getDestination());
            case SEND -> authorizeSend(user(accessor), accessor.getDestination());
            default -> {
            }
        }
        return message;
    }

    private Authentication authenticate(String header) {
        if (header == null || !header.startsWith(BEARER)) {
            throw new BadCredentialsException("Missing bearer token in CONNECT");
        }
        try {
            AuthenticatedUser user = AuthenticatedUser.from(jwtDecoder.decode(header.substring(BEARER.length())));
            var authority = new SimpleGrantedAuthority(user.bot() ? "ROLE_BOT" : "ROLE_PLAYER");
            return UsernamePasswordAuthenticationToken.authenticated(user, null, List.of(authority));
        } catch (JwtException e) {
            throw new BadCredentialsException("Invalid token", e);
        }
    }

    private void authorizeSubscribe(AuthenticatedUser user, String destination) {
        if (destination == null) {
            throw new AccessDeniedException("Missing destination");
        }
        if (destination.startsWith("/user/queue/")) {
            return; // always private to this session's user
        }
        Matcher topic = ROOM_TOPIC.matcher(destination);
        Matcher app = ROOM_APP.matcher(destination);
        if (topic.matches()) {
            requireMember(user, topic.group(1));
        } else if (app.matches()) {
            requireMember(user, app.group(1)); // @SubscribeMapping request/reply, e.g. /state
        } else {
            throw new AccessDeniedException("Cannot subscribe to " + destination);
        }
    }

    private static void authorizeSend(AuthenticatedUser user, String destination) {
        if (destination == null || !ROOM_APP.matcher(destination).matches()) {
            throw new AccessDeniedException("Cannot send to " + destination);
        }
        // Membership is checked by the game service itself, which needs the room anyway.
    }

    private void requireMember(AuthenticatedUser user, String code) {
        boolean member = rooms.find(code).map(room -> room.isMember(user.id())).orElse(false);
        if (!member) {
            throw new AccessDeniedException("Not a member of room " + code);
        }
    }

    private static AuthenticatedUser user(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AccessDeniedException("Not authenticated");
    }

    /** Used by message handlers to get the caller from a STOMP principal. */
    static AuthenticatedUser caller(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AccessDeniedException("Not authenticated");
    }
}
