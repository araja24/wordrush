package dev.wordrush.game;

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

    /** Built only from what was committed, so a replay can never reveal colours for a new word. */
    static GuessResult of(GuessRequest request, String requestedWord, GameRepository.GuessCommit commit,
                          GuessOutcome outcome) {
        boolean counted = outcome == GuessOutcome.WON || outcome == GuessOutcome.WRONG;
        return new GuessResult(request.guessId(), request.roundId(), counted ? commit.word() : requestedWord,
                counted ? commit.feedback() : null, commit.attempt(), outcome, commit.points(), commit.msBehind());
    }
}
