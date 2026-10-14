package dev.wordrush.user;

import java.util.UUID;

public record UserDto(UUID id, String username, String nickname, boolean guest, boolean bot) {

    public static UserDto from(User user) {
        return new UserDto(user.id(), user.username(), user.nickname(), user.isGuest(), user.isBot());
    }
}
