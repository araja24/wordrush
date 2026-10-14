package dev.wordrush.room;

import dev.wordrush.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** HTTP only: parse, validate, delegate, map the result. Rules live in {@link RoomService}. */
@Tag(name = "Rooms")
@RestController
@RequestMapping("/api/v1")
class RoomController {

    private final RoomService roomService;

    RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Operation(summary = "Create a room", description = "The caller becomes host. Leaves any room the caller was in.")
    @PostMapping("/rooms")
    ResponseEntity<RoomSnapshot> create(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody(required = false) CreateRoomRequest request) {
        CreateRoomRequest settings = request != null ? request : new CreateRoomRequest(null, null, null);
        RoomSnapshot room = roomService.create(caller, settings.toSettings());
        return ResponseEntity.created(URI.create("/api/v1/rooms/" + room.code())).body(room);
    }

    @Operation(summary = "Join a room by code", description = "Idempotent: rejoining a room you're in is fine.")
    @PostMapping("/rooms/{code}/join")
    RoomSnapshot join(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String code) {
        return roomService.join(caller, code);
    }

    @Operation(summary = "Leave a room")
    @PostMapping("/rooms/{code}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String code) {
        roomService.leave(caller, code);
    }

    @Operation(summary = "Get a room (members only)")
    @GetMapping("/rooms/{code}")
    RoomSnapshot get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String code) {
        return roomService.get(caller, code);
    }

    @Operation(summary = "Kick a player (host only)")
    @DeleteMapping("/rooms/{code}/players/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void kick(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String code,
              @PathVariable UUID userId) {
        roomService.kick(caller, code, userId);
    }

    @Operation(summary = "The room you're currently in", description = "204 if none. Used to resume after a refresh.")
    @GetMapping("/me/room")
    ResponseEntity<RoomSnapshot> currentRoom(@AuthenticationPrincipal AuthenticatedUser caller) {
        return roomService.current(caller)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
