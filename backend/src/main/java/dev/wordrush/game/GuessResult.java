package dev.wordrush.game;

import dev.wordrush.core.Feedback;
import java.util.UUID;

/** Private reply to the guesser on /user/queue/guess-result. {@code feedback} is null when not counted. */
public record GuessResult(
        UUID guessId,
        UUID roundId,
        String word,
        String feedback,
        int attempt,
        GuessOutcome outcome,
        int points,
        long msBehind) {

    static GuessResult rejected(GuessRequest request, String word, GuessOutcome outcome) {
        return new GuessResult(request.guessId(), request.roundId(), word, null, 0, outcome, 0, 0);
    }

    static GuessResult of(GuessRequest request, String word, Feedback feedback,
                          GameRepository.GuessCommit commit, GuessOutcome outcome) {
        boolean counted = outcome == GuessOutcome.WON || outcome == GuessOutcome.WRONG;
        return new GuessResult(request.guessId(), request.roundId(), word,
                counted ? feedback.toString() : null, commit.attempt(), outcome, commit.points(), commit.msBehind());
    }
}
