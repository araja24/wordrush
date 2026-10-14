package dev.wordrush.event;

/**
 * Published as a Spring application event whenever a room event is broadcast, so in-process
 * players (bots) see exactly what connected clients see, without depending on the transport.
 */
public record RoomEventBroadcast(String roomCode, RoomEvent event) {
}
