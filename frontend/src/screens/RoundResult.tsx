import { secondsUntil, useServerNow } from '../components/useServerNow';
import { bestGreens } from '../state/reducer';
import { useApp } from '../state/store';
import { displayName } from './Game';

const seconds = (ms: number) => `${(ms / 1000).toFixed(3)}s`;

interface Row {
  userId: string;
  name: string;
  detail: string;
  kind: 'win' | 'late' | 'plain';
  rank: number;
}

/** The answer, who won (and by how much), everyone's round, the scores, and the next phase. */
export function RoundResult() {
  const { game, lastResult } = useApp();
  const now = useServerNow(game?.clockOffset ?? 0);
  const round = game?.round;
  const result = round?.result;
  if (!game?.room || !round || !result) return null;

  const { room, me, roundNo, leaderboard } = game;
  const solved = result.reason === 'SOLVED';
  const winner = room.players.find((p) => p.userId === result.winnerId);
  const byId = new Map(room.players.map((p) => [p.userId, p]));
  const late = lastResult?.outcome === 'CORRECT_LATE' && lastResult.roundId === round.roundId ? lastResult : null;
  const isLastRound = roundNo >= room.settings.totalRounds;
  const left = secondsUntil(game.phaseEndsAt, now);
  const pause = Math.max(1, (game.phaseEndsAt - game.phaseStartedAt) / 1000);

  const headline = solved
    ? result.winnerId === me
      ? `You win round ${roundNo}`
      : `${winner ? displayName(winner) : 'Someone'} wins round ${roundNo}`
    : result.reason === 'TIMEOUT'
      ? 'Time’s up'
      : 'Nobody got it';
  const attempts = result.winnerAttempts ?? 0;
  const sub = solved
    ? `${attempts} ${attempts === 1 ? 'guess' : 'guesses'}${result.solveMillis != null ? ` · ${seconds(result.solveMillis)}` : ''}`
    : result.reason === 'TIMEOUT'
      ? `Nobody solved it in ${room.settings.roundSeconds} seconds`
      : 'Everyone used all six guesses';

  const rowsOf = (userId: string) =>
    userId === me ? round.myGuesses.map((g) => g.feedback) : (round.opponents[userId] ?? []);
  const closest = Math.max(0, ...room.players.map((p) => bestGreens(rowsOf(p.userId))));

  const rows: Row[] = room.players.map((player) => {
    const used = rowsOf(player.userId);
    const greens = bestGreens(used);
    const name = player.userId === me ? `${displayName(player)} (you)` : displayName(player);
    if (player.userId === result.winnerId) {
      return { userId: player.userId, name, detail: result.solveMillis != null ? seconds(result.solveMillis) : 'solved', kind: 'win', rank: 0 };
    }
    if (player.userId === me && late && result.solveMillis != null) {
      return { userId: player.userId, name, detail: seconds(result.solveMillis + late.msBehind), kind: 'late', rank: 1 };
    }
    if (!player.connected) return { userId: player.userId, name, detail: 'disconnected', kind: 'plain', rank: 9 };
    if (result.reason === 'ALL_FAILED') return { userId: player.userId, name, detail: '6 / 6', kind: 'plain', rank: 5 };
    if (result.reason === 'TIMEOUT' && used.length > 0) {
      const best = greens === closest && closest > 0;
      return { userId: player.userId, name, detail: `${greens} of 5 · row ${used.length}`, kind: best ? 'win' : 'plain', rank: 5 - greens };
    }
    return { userId: player.userId, name, detail: used.length ? `row ${used.length}` : 'no guesses', kind: 'plain', rank: 8 - used.length / 10 };
  });
  rows.sort((a, b) => a.rank - b.rank);

  return (
    <main className="result">
      <section className="result__head" aria-live="polite">
        <div className={`answer${solved ? ' answer--solved' : ''}`} role="img" aria-label={`The word was ${result.answer}`}>
          {[...result.answer].map((letter, i) => <span key={i}>{letter}</span>)}
        </div>
        <h1 className="headline">{headline}</h1>
        <p className="subline">{sub}</p>
        {late && <p className="late-line">You solved it {seconds(late.msBehind)} later.</p>}
      </section>

      <div className="result__body">
        <section className="round-rows" aria-label="This round">
          {rows.map((row) => (
            <div key={row.userId}
                 className={`round-row${row.kind === 'win' ? ' round-row--win' : ''}${row.kind === 'late' ? ' round-row--late' : ''}${row.userId === me ? ' round-row--me' : ''}`}>
              <span className="round-row__name">{row.name}</span>
              <span className="round-row__detail">{row.detail}</span>
            </div>
          ))}
        </section>
        <section className="score-card" aria-label="Scores">
          <h2>Points</h2>
          {leaderboard.filter((s) => byId.has(s.userId)).map((s) => (
            <div key={s.userId} className={`score-card__row${s.userId === me ? ' is-me' : ''}`}>
              <span>{displayName(byId.get(s.userId)!)}</span>
              <span className={`score${s.score > 0 ? ' score--scored' : ''}`}>{s.score}</span>
            </div>
          ))}
        </section>
      </div>

      <div className="next-phase" role="timer">
        <span>
          {isLastRound ? 'Final standings in' : `Round ${roundNo + 1} in`} <strong>{left}</strong>
        </span>
        <div className="bar bar--light" aria-hidden="true">
          <div style={{ width: `${Math.min(100, (left / pause) * 100)}%` }} />
        </div>
      </div>
    </main>
  );
}
