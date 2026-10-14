package dev.wordrush.room;

/** Sent privately to /user/queue/kicked: a kicked player no longer receives the room's broadcasts. */
public record KickedNotice(String roomCode) {
}
