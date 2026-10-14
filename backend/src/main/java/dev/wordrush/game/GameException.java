package dev.wordrush.game;

import dev.wordrush.common.error.DomainException;
import org.springframework.http.HttpStatus;

public final class GameException extends DomainException {

    private GameException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    static GameException alreadyStarted(String roomCode) {
        return new GameException(HttpStatus.CONFLICT, "GAME_ALREADY_STARTED", "Room " + roomCode + " is already playing");
    }

    static GameException notOver(String roomCode) {
        return new GameException(HttpStatus.CONFLICT, "GAME_NOT_OVER", "Room " + roomCode + " has not finished its game");
    }

    static GameException invalidGuess(String reason) {
        return new GameException(HttpStatus.BAD_REQUEST, "INVALID_GUESS", reason);
    }
}
