package dev.wordrush.event;

import java.util.UUID;

/**
 * Port for pushing messages to clients. Services depend on this interface, not on STOMP, so the
 * transport can change (e.g. Redis pub/sub fan-out across instances, Stage 9) without touching
 * game logic, and unit tests can mock it.
 */
public interface RoomBroadcaster {

    /** Sends an event to everyone subscribed to the room, stamped with the next sequence number. */
    void broadcast(String roomCode, RoomEvent event);

    /** Sends a private message to one user, e.g. destination "/queue/guess-result". */
    void sendToUser(UUID userId, String destination, Object payload);

    /**
     * Stops a user who left or was kicked from receiving the room's broadcasts. Membership is
     * checked when they subscribe, so without this an ex-member's subscription would live on.
     */
    void revokeRoomAccess(String roomCode, UUID userId);
}
