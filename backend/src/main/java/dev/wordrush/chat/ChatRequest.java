package dev.wordrush.chat;

/** A chat message from a player. {@code reaction} marks one of the quick reactions ("gg", "so close", ...). */
public record ChatRequest(String text, boolean reaction) {
}
