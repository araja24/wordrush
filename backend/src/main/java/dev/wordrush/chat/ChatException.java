package dev.wordrush.chat;

import dev.wordrush.common.error.DomainException;
import org.springframework.http.HttpStatus;

public final class ChatException extends DomainException {

    private ChatException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    static ChatException invalid(String reason) {
        return new ChatException(HttpStatus.BAD_REQUEST, "INVALID_CHAT", reason);
    }

    static ChatException lockedDuringRound() {
        return new ChatException(HttpStatus.CONFLICT, "CHAT_LOCKED", "Only quick reactions while a round is on");
    }

    static ChatException rateLimited() {
        return new ChatException(HttpStatus.TOO_MANY_REQUESTS, "CHAT_RATE_LIMITED", "Slow down a little");
    }
}
