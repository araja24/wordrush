package dev.wordrush.bot;

import dev.wordrush.auth.AuthenticatedUser;
import dev.wordrush.core.Difficulty;
import dev.wordrush.room.RoomSnapshot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bots")
@RestController
@RequestMapping("/api/v1/rooms/{code}/bots")
class BotController {

    record AddBotRequest(@Schema(example = "MEDIUM") @NotNull Difficulty difficulty) {
    }

    private final BotService botService;

    BotController(BotService botService) {
        this.botService = botService;
    }

    @Operation(summary = "Add a bot (host only, in the lobby)", description = "The bot takes a seat and plays every round.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RoomSnapshot add(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable String code,
                     @Valid @RequestBody AddBotRequest request) {
        return botService.addBot(caller, code, request.difficulty());
    }
}
