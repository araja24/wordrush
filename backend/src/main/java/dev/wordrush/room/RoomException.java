package dev.wordrush.room;

import dev.wordrush.common.error.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class RoomException extends DomainException {

    private RoomException(HttpStatus status, String code, String message) {
        super(status, code, message);
    }

    public static RoomException notFound(String roomCode) {
        return new RoomException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "Room " + roomCode + " does not exist");
    }

    public static RoomException full(String roomCode) {
        return new RoomException(HttpStatus.CONFLICT, "ROOM_FULL", "Room " + roomCode + " is full");
    }

    public static RoomException gameInProgress(String roomCode) {
        return new RoomException(HttpStatus.CONFLICT, "GAME_IN_PROGRESS",
                "Room " + roomCode + " has already started");
    }

    public static RoomException notMember(String roomCode) {
        return new RoomException(HttpStatus.FORBIDDEN, "NOT_A_MEMBER", "You are not in room " + roomCode);
    }

    public static RoomException notHost(String roomCode) {
        return new RoomException(HttpStatus.FORBIDDEN, "NOT_HOST", "Only the host of room " + roomCode + " can do that");
    }

    public static RoomException playerNotFound(String roomCode, UUID userId) {
        return new RoomException(HttpStatus.NOT_FOUND, "PLAYER_NOT_FOUND",
                "Player " + userId + " is not in room " + roomCode);
    }

    public static RoomException cannotKickSelf() {
        return new RoomException(HttpStatus.BAD_REQUEST, "CANNOT_KICK_SELF", "Use leave to remove yourself");
    }
}
