package dev.wordrush.game;

import dev.wordrush.auth.AuthenticatedUser;
import dev.wordrush.config.WordRushProperties;
import dev.wordrush.core.Feedback;
import dev.wordrush.core.FeedbackEvaluator;
import dev.wordrush.core.WordList;
import dev.wordrush.event.RoomBroadcaster;
import dev.wordrush.event.RoomEvent;
import dev.wordrush.event.RoundEndReason;
import dev.wordrush.event.Standing;
import dev.wordrush.game.GameRepository.GuessCommit;
import dev.wordrush.game.GameRepository.GuessEntry;
import dev.wordrush.game.GameRepository.Phase;
import dev.wordrush.game.GameRepository.RoundState;
import dev.wordrush.game.GameRepository.Score;
import dev.wordrush.game.TimerRepository.Timer;
import dev.wordrush.game.TimerRepository.TimerType;
import dev.wordrush.room.Player;
import dev.wordrush.room.Room;
import dev.wordrush.room.RoomCodeGenerator;
import dev.wordrush.room.RoomException;
import dev.wordrush.room.RoomRepository;
import dev.wordrush.room.RoomSnapshot;
import dev.wordrush.room.RoomStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The room state machine: LOBBY → COUNTDOWN → IN_ROUND → ROUND_RESULTS → (COUNTDOWN … | GAME_OVER) → LOBBY.
 *
 * <p>Every transition is a compare-and-set in Redis, so a transition can only happen once no matter
 * how many triggers race (a timer and a winning guess, a double-clicked Start, two instances).
 * The service is stateless; any instance can handle any message or timer.
 */
@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);
    static final String GUESS_RESULT_QUEUE = "/queue/guess-result";

    private final RoomRepository rooms;
    private final GameRepository games;
    private final TimerRepository timers;
    private final RoomBroadcaster broadcaster;
    private final WordList words;
    private final Clock clock;
    private final WordRushProperties.Game config;
    private final Duration roomTtl;

    public GameService(RoomRepository rooms, GameRepository games, TimerRepository timers,
                       RoomBroadcaster broadcaster, WordList words, Clock clock, WordRushProperties properties) {
        this.rooms = rooms;
        this.games = games;
        this.timers = timers;
        this.broadcaster = broadcaster;
        this.words = words;
        this.clock = clock;
        this.config = properties.game();
        this.roomTtl = properties.room().ttl();
    }

    // ---- commands from players -------------------------------------------------------------

    /** Host only: LOBBY → COUNTDOWN for round 1. */
    public void start(AuthenticatedUser caller, String rawCode) {
        String code = RoomCodeGenerator.normalize(rawCode);
        Room room = requireMember(caller, code);
        if (!room.isHost(caller.id())) {
            throw RoomException.notHost(code);
        }
        long startsAt = clock.millis() + config.countdown().toMillis();
        boolean started = games.transition(code, RoomStatus.LOBBY, null, Map.of(
                "status", RoomStatus.COUNTDOWN.name(),
                "roundNo", "0",
                "gameId", UUID.randomUUID().toString(),
                "phaseEndsAt", String.valueOf(startsAt)));
        if (!started) {
            throw GameException.alreadyStarted(code);
        }
        games.resetForNewGame(code, room.players().stream().map(Player::userId).toList(), roomTtl);
        timers.schedule(new Timer(code, TimerType.COUNTDOWN_END, "1"), startsAt);
        log.info("Game started room={} players={} rounds={}", code, room.players().size(),
                room.settings().totalRounds());
        broadcaster.broadcast(code, new RoomEvent.Countdown(1, room.settings().totalRounds(), startsAt));
    }

    /**
     * Scores a guess. The reply goes privately to the guesser first; then the room sees the
     * colours (never the letters) and, if the guess ended the round, the result.
     */
    public GuessResult guess(AuthenticatedUser caller, String rawCode, GuessRequest request) {
        String code = RoomCodeGenerator.normalize(rawCode);
        if (request == null || request.guessId() == null || request.roundId() == null) {
            throw GameException.invalidGuess("guessId and roundId are required");
        }
        String word = WordList.normalize(request.word());
        if (!words.isAllowed(word)) {
            return reply(caller, GuessResult.rejected(request, word, GuessOutcome.NOT_A_WORD));
        }
        RoundState round = games.round(code).filter(r -> r.roundId().equals(request.roundId())).orElse(null);
        if (round == null) {
            return reply(caller, GuessResult.rejected(request, word, GuessOutcome.ROUND_OVER));
        }

        Feedback feedback = FeedbackEvaluator.evaluate(round.answer(), word);
        long now = clock.millis();
        long resultsEndsAt = now + config.resultsPause().toMillis();
        GuessCommit commit = games.submitGuess(code, caller.id(), round.roundId(), request.guessId(), word,
                feedback, now, resultsEndsAt, config.idempotencyTtl());
        if ("NOT_A_MEMBER".equals(commit.outcome())) {
            throw RoomException.notMember(code);
        }
        if (commit.replay() && !commit.word().isEmpty() && !commit.word().equals(word)) {
            // Same guessId, different word: not a retry. Answering would leak free feedback.
            throw GameException.invalidGuess("guessId " + request.guessId() + " was already used for another word");
        }
        GuessOutcome outcome = GuessOutcome.valueOf(commit.outcome());
        GuessResult result = reply(caller, GuessResult.of(request, word, commit, outcome));

        boolean counted = outcome == GuessOutcome.WON || outcome == GuessOutcome.WRONG;
        if (!counted || commit.replay()) {
            return result; // nothing changed for anyone else
        }
        broadcaster.broadcast(code, new RoomEvent.PlayerProgress(caller.id(), commit.attempt(), commit.feedback()));
        if (commit.roundEnded()) {
            boolean won = outcome == GuessOutcome.WON;
            announceRoundEnd(code, round, won ? RoundEndReason.SOLVED : RoundEndReason.ALL_FAILED,
                    won ? caller.id() : null, won ? commit.attempt() : null, commit.points(),
                    won ? now - round.startedAt() : null, resultsEndsAt);
        }
        return result;
    }

    /** Host only: GAME_OVER → LOBBY so the same group can play again. */
    public void playAgain(AuthenticatedUser caller, String rawCode) {
        String code = RoomCodeGenerator.normalize(rawCode);
        Room room = requireMember(caller, code);
        if (!room.isHost(caller.id())) {
            throw RoomException.notHost(code);
        }
        boolean reset = games.transition(code, RoomStatus.GAME_OVER, null,
                Map.of("status", RoomStatus.LOBBY.name(), "roundNo", "0", "phaseEndsAt", "0"));
        if (!reset) {
            throw GameException.notOver(code);
        }
        games.clearRound(code);
        broadcaster.broadcast(code, new RoomEvent.ReturnedToLobby());
    }

    // ---- timers ------------------------------------------------------------------------------

    void onTimer(Timer timer) {
        switch (timer.type()) {
            case COUNTDOWN_END -> startRound(timer.roomCode(), Integer.parseInt(timer.token()));
            case ROUND_END -> endRoundOnTimeout(timer.roomCode(), UUID.fromString(timer.token()));
            case RESULTS_END -> afterResults(timer.roomCode(), Integer.parseInt(timer.token()));
        }
    }

    /** COUNTDOWN → IN_ROUND. */
    private void startRound(String code, int roundNo) {
        Room room = rooms.find(code).orElse(null);
        if (room == null || room.players().isEmpty()) {
            return;
        }
        long now = clock.millis();
        long endsAt = now + room.settings().roundSeconds() * 1000L;
        String answer = words.randomAnswer(ThreadLocalRandom.current(), games.usedAnswers(code));
        UUID roundId = UUID.randomUUID();
        if (!games.startRound(code, roundNo - 1, roundNo, roundId, answer, now, endsAt, roomTtl)) {
            return; // not counting down to this round any more
        }
        timers.schedule(new Timer(code, TimerType.ROUND_END, roundId.toString()), endsAt);
        // start_round.lua kept the room alive; keep everyone's resume-after-refresh pointer alive with it.
        room.players().forEach(player -> rooms.refreshCurrentRoom(player.userId(), code, roomTtl));
        log.info("Round started room={} round={}/{} roundId={}", code, roundNo, room.settings().totalRounds(), roundId);
        broadcaster.broadcast(code, new RoomEvent.RoundStarted(roundId, roundNo, room.settings().totalRounds(), endsAt));
    }

    /** IN_ROUND → ROUND_RESULTS when time runs out (a no-op if a guess already ended the round). */
    private void endRoundOnTimeout(String code, UUID roundId) {
        RoundState round = games.round(code).filter(r -> r.roundId().equals(roundId)).orElse(null);
        if (round == null) {
            return;
        }
        long resultsEndsAt = clock.millis() + config.resultsPause().toMillis();
        if (games.endRoundOnTimeout(code, roundId, resultsEndsAt)) {
            announceRoundEnd(code, round, RoundEndReason.TIMEOUT, null, null, 0, null, resultsEndsAt);
        }
    }

    /** ROUND_RESULTS → COUNTDOWN for the next round, or GAME_OVER after the last one. */
    private void afterResults(String code, int roundNo) {
        Room room = rooms.find(code).orElse(null);
        Phase phase = games.phase(code).orElse(null);
        if (room == null || phase == null || phase.status() != RoomStatus.ROUND_RESULTS || phase.roundNo() != roundNo) {
            return;
        }
        int totalRounds = room.settings().totalRounds();
        if (roundNo < totalRounds) {
            long startsAt = clock.millis() + config.countdown().toMillis();
            if (games.transition(code, RoomStatus.ROUND_RESULTS, roundNo,
                    Map.of("status", RoomStatus.COUNTDOWN.name(), "phaseEndsAt", String.valueOf(startsAt)))) {
                timers.schedule(new Timer(code, TimerType.COUNTDOWN_END, String.valueOf(roundNo + 1)), startsAt);
                broadcaster.broadcast(code, new RoomEvent.Countdown(roundNo + 1, totalRounds, startsAt));
            }
        } else if (games.transition(code, RoomStatus.ROUND_RESULTS, roundNo,
                Map.of("status", RoomStatus.GAME_OVER.name(), "phaseEndsAt", "0"))) {
            List<Standing> standings = standings(code, room);
            log.info("Game over room={} winner={}", code, standings.isEmpty() ? null : standings.getFirst().userId());
            broadcaster.broadcast(code, new RoomEvent.GameOver(standings));
        }
    }

    private void announceRoundEnd(String code, RoundState round, RoundEndReason reason, UUID winnerId,
                                  Integer winnerAttempts, int points, Long solveMillis, long resultsEndsAt) {
        // Schedule before broadcasting so the game keeps moving even if a broadcast fails.
        timers.schedule(new Timer(code, TimerType.RESULTS_END, String.valueOf(round.roundNo())), resultsEndsAt);
        log.info("Round ended room={} round={} reason={} winner={} attempts={}",
                code, round.roundNo(), reason, winnerId, winnerAttempts);
        broadcaster.broadcast(code, new RoomEvent.RoundEnded(round.roundId(), round.roundNo(), round.answer(),
                reason, winnerId, winnerAttempts, points, solveMillis, resultsEndsAt));
        rooms.find(code).ifPresent(room ->
                broadcaster.broadcast(code, new RoomEvent.LeaderboardUpdated(standings(code, room))));
    }

    // ---- reads -------------------------------------------------------------------------------

    /** Full state for (re)drawing the game; members only. */
    public GameSnapshot snapshot(AuthenticatedUser caller, String rawCode) {
        String code = RoomCodeGenerator.normalize(rawCode);
        Room room = requireMember(caller, code);
        Phase phase = games.phase(code).orElseThrow(() -> RoomException.notFound(code));
        GameSnapshot.CurrentRound current = null;
        if (phase.status() == RoomStatus.IN_ROUND || phase.status() == RoomStatus.ROUND_RESULTS) {
            current = games.round(code).map(round -> currentRound(code, caller, round)).orElse(null);
        }
        return new GameSnapshot(RoomSnapshot.from(room), phase.roundNo(), phase.phaseEndsAt(), current,
                standings(code, room), clock.millis());
    }

    private GameSnapshot.CurrentRound currentRound(String code, AuthenticatedUser caller, RoundState round) {
        Map<UUID, List<GuessEntry>> guesses = games.guesses(code);
        List<GameSnapshot.GuessView> mine = guesses.getOrDefault(caller.id(), List.of()).stream()
                .map(guess -> new GameSnapshot.GuessView(guess.word(), guess.feedback()))
                .toList();
        Map<UUID, List<String>> opponents = new HashMap<>();
        guesses.forEach((userId, entries) -> {
            if (!userId.equals(caller.id())) {
                opponents.put(userId, entries.stream().map(GuessEntry::feedback).toList()); // colours only
            }
        });
        GameSnapshot.Result result = round.endReason() == null
                ? null // the answer stays secret until the round is over
                : new GameSnapshot.Result(round.answer(), round.endReason(), round.winnerId(), round.winnerAttempts(),
                        round.solveMillis());
        return new GameSnapshot.CurrentRound(round.roundId(), round.roundNo(), round.endsAt(), mine, opponents, result);
    }

    private Room requireMember(AuthenticatedUser caller, String code) {
        Room room = rooms.find(code).orElseThrow(() -> RoomException.notFound(code));
        if (!room.isMember(caller.id())) {
            throw RoomException.notMember(code);
        }
        return room;
    }

    /** Current members ranked by points; equal points share a rank (1, 1, 3). */
    private List<Standing> standings(String code, Room room) {
        Map<UUID, Player> members = room.players().stream()
                .collect(Collectors.toMap(Player::userId, Function.identity()));
        List<Standing> standings = new ArrayList<>();
        int rank = 0;
        int previousPoints = Integer.MIN_VALUE;
        for (Score score : games.scores(code)) {
            Player player = members.get(score.userId());
            if (player == null) {
                continue; // left the room
            }
            if (score.points() != previousPoints) {
                rank = standings.size() + 1;
                previousPoints = score.points();
            }
            standings.add(new Standing(player.userId(), player.nickname(), score.points(), rank));
        }
        return standings;
    }

    private GuessResult reply(AuthenticatedUser caller, GuessResult result) {
        broadcaster.sendToUser(caller.id(), GUESS_RESULT_QUEUE, result);
        return result;
    }
}
