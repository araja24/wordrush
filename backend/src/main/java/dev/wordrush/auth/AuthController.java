package dev.wordrush.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Play as a guest", description = "Creates a guest account and returns a bearer token.")
    @SecurityRequirements // public: overrides the global bearer requirement in the docs
    @PostMapping("/guest")
    @ResponseStatus(HttpStatus.CREATED)
    AuthResponse guest(@Valid @RequestBody GuestLoginRequest request) {
        return authService.loginAsGuest(request.nickname(), Boolean.TRUE.equals(request.bot()));
    }
}
