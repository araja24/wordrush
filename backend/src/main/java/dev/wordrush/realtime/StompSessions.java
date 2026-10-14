package dev.wordrush.realtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Which user owns each live STOMP session on this instance. Outbound broker messages carry only a
 * session id, so delivery-time checks look the user up here.
 */
@Component
class StompSessions {

    private final Map<String, UUID> userBySession = new ConcurrentHashMap<>();

    void register(String sessionId, UUID userId) {
        userBySession.put(sessionId, userId);
    }

    void remove(String sessionId) {
        userBySession.remove(sessionId);
    }

    UUID userOf(String sessionId) {
        return sessionId == null ? null : userBySession.get(sessionId);
    }
}
