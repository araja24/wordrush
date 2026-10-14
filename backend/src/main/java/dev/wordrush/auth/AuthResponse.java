package dev.wordrush.auth;

import dev.wordrush.user.User;
import dev.wordrush.user.UserDto;
import java.time.Instant;

public record AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserDto user) {

    static AuthResponse of(TokenService.IssuedToken token, User user) {
        return new AuthResponse(token.value(), "Bearer", token.expiresAt(), UserDto.from(user));
    }
}
