import { Chat } from '../components/Chat';
import { secondsUntil, useServerNow } from '../components/useServerNow';
import { useApp } from '../state/store';

const TILE_COLOUR: Record<number, string> = { 3: 'bg-amber', 2: 'bg-cyan', 1: 'bg-white' };

/** Full-screen 3-2-1 before each round. */
export function Countdown() {
  const game = useApp((s) => s.game);
  const now = useServerNow(game?.clockOffset ?? 0);
  if (!game?.room) return null;
  const seconds = Math.max(1, secondsUntil(game.phaseEndsAt, now));
  const { totalRounds, roundSeconds } = game.room.settings;

  return (
    <main className="center-screen">
      <p className="center-screen__label">
        Round <strong>{game.roundNo}</strong> of {totalRounds}
      </p>
      <div key={seconds} className={`countdown-tile ${TILE_COLOUR[seconds] ?? 'bg-amber'}`}
           role="timer" aria-live="assertive" aria-label={`Starting in ${seconds}`}>
        {seconds}
      </div>
      <p className="center-screen__sub">6 guesses · {roundSeconds} seconds</p>
      <Chat />
    </main>
  );
}
