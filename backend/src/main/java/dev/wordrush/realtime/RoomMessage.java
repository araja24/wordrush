package dev.wordrush.realtime;

import dev.wordrush.event.RoomEvent;

/**
 * Envelope for every room broadcast. {@code seq} increases by one per message in a room, so a
 * client that sees a gap knows it missed something and resyncs. {@code serverTime} lets the
 * client correct its clock for countdowns.
 */
record RoomMessage(String type, String roomCode, long seq, long serverTime, RoomEvent payload) {
}
