import { useCallback, useEffect, useState } from 'react';
import { Board } from '../components/Board';
import { Keyboard } from '../components/Keyboard';
import { Leaderboard } from '../components/Leaderboard';
import { RaceLanes } from '../components/RaceLanes';
import { secondsUntil, useServerNow } from '../components/useServerNow';
import { letterStates } from '../state/reducer';
import { useApp } from '../state/store';

const PENDING_TIMEOUT_MS = 5_000;

export function Game() {
  const { game, guess, lastResult, showToast, connected } = useApp();
  const now = useServerNow(game?.clockOffset ?? 0);
  const [input, setInput] = useState('');
  const [pending, setPending] = useState(false);
  const [shake, setShake] = useState(0);

  const round = game?.round ?? null;
  const myGuesses = round?.myGuesses ?? [];
  const solved = myGuesses.at(-1)?.feedback === 'GGGGG';
  const canType = game?.status === 'IN_ROUND' && !solved && myGuesses.length < 6 && !pending;

  // A new round clears whatever was half-typed.
  useEffect(() => setInput(''), [round?.roundId]);

  // React to the server's verdict on our guess.
  useEffect(() => {
    if (!lastResult) return;
    setPending(false);
    switch (lastResult.outcome) {
      case 'WRONG':
      case 'WON':
        setInput('');
        break;
      case 'NOT_A_WORD':
        setShake((n) => n + 1);
        showToast(`${lastResult.word} isn't in the word list.`);
        break;
      case 'CORRECT_LATE':
        showToast(`Right word, but ${lastResult.msBehind} ms too late.`);
        break;
      case 'ROUND_OVER':
        showToast('That round is already over.');
        break;
      case 'OUT_OF_GUESSES':
        showToast("You've used all six guesses.");
        break;
    }
  }, [lastResult, showToast]);

  useEffect(() => {
    if (!pending) return;
    const timer = setTimeout(() => setPending(false), PENDING_TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [pending]);

  const submit = useCallback(() => {
    if (!canType) return;
    if (input.length < 5) {
      setShake((n) => n + 1);
      return;
    }
    setPending(true);
    guess(input);
  }, [canType, input, guess]);

  const type = useCallback((letter: string) => canType && setInput((s) => (s.length < 5 ? s + letter : s)), [canType]);
  const erase = useCallback(() => canType && setInput((s) => s.slice(0, -1)), [canType]);

  // Physical keyboard.
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      if (event.target instanceof HTMLInputElement) return;
      if (event.key === 'Enter') submit();
      else if (event.key === 'Backspace') erase();
      else if (/^[a-zA-Z]$/.test(event.key)) type(event.key.toUpperCase());
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submit, erase, type]);

  if (!game?.room) return null;
  const { room, me, status, roundNo, phaseEndsAt, leaderboard } = game;
  const secondsLeft = secondsUntil(phaseEndsAt, now);
  const rowsByPlayer = { ...(round?.opponents ?? {}), [me]: myGuesses.map((g) => g.feedback) };

  return (
    <main className="game">
      <header className="game__bar">
        <span className="game__round">
          Round {Math.max(roundNo, 1)} of {room.settings.totalRounds}
        </span>
        {status === 'IN_ROUND' && (
          <span className={`clock${secondsLeft <= 10 ? ' clock--alarm' : ''}`} role="timer" aria-live="off">
            {formatClock(secondsLeft)}
          </span>
        )}
        {!connected && <span className="offline" role="status">Reconnecting…</span>}
      </header>

      <section className="game__play" aria-label="Your board">
        <Board guesses={myGuesses} current={input} shake={shake} />
        <Keyboard states={letterStates(myGuesses)} disabled={!canType} onLetter={type} onEnter={submit} onBackspace={erase} />
      </section>

      <aside className="game__side">
        <RaceLanes players={room.players} rowsByPlayer={rowsByPlayer} me={me} winnerId={round?.result?.winnerId ?? null} />
        <Leaderboard standings={leaderboard} me={me} />
      </aside>

      {status === 'COUNTDOWN' && <CountdownOverlay roundNo={roundNo} seconds={secondsLeft} />}
      {status === 'ROUND_RESULTS' && <RoundResultsOverlay seconds={secondsLeft} isLastRound={roundNo >= room.settings.totalRounds} />}
      {status === 'GAME_OVER' && <GameOverOverlay />}
    </main>
  );
}

function formatClock(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

function CountdownOverlay({ roundNo, seconds }: { roundNo: number; seconds: number }) {
  return (
    <div className="overlay" role="status">
      <p className="overlay__label">Round {roundNo} starts in</p>
      <p className="overlay__big" key={seconds}>{Math.max(seconds, 1)}</p>
    </div>
  );
}

function RoundResultsOverlay({ seconds, isLastRound }: { seconds: number; isLastRound: boolean }) {
  const game = useApp((s) => s.game);
  const result = game?.round?.result;
  if (!game || !result) return null;
  const winner = game.room?.players.find((p) => p.userId === result.winnerId);
  const headline =
    result.reason === 'SOLVED'
      ? result.winnerId === game.me
        ? `You got it in ${result.winnerAttempts}`
        : `${winner?.nickname ?? 'Someone'} got it in ${result.winnerAttempts}`
      : result.reason === 'TIMEOUT'
        ? "Time's up"
        : 'Nobody got it';

  return (
    <div className="overlay" role="status">
      <p className="overlay__label">{headline}</p>
      <p className="overlay__answer" aria-label={`The word was ${result.answer}`}>
        {[...result.answer].map((letter, i) => (
          <span key={i} className="tile tile--G">{letter}</span>
        ))}
      </p>
      <p className="hint">{isLastRound ? 'Final scores' : 'Next round'} in {seconds}</p>
    </div>
  );
}

function GameOverOverlay() {
  const { game, playAgain, leaveRoom } = useApp();
  if (!game?.room) return null;
  const isHost = game.room.hostId === game.me;
  const top = game.leaderboard[0];
  const headline = !top ? 'Game over' : top.userId === game.me ? 'You won' : `${top.nickname} won`;

  return (
    <div className="overlay overlay--final" role="dialog" aria-labelledby="final-heading">
      <h2 id="final-heading" className="overlay__headline">{headline}</h2>
      <Leaderboard standings={game.leaderboard} me={game.me} />
      <div className="overlay__actions">
        {isHost ? (
          <button type="button" className="button button--primary" onClick={playAgain}>
            Play again
          </button>
        ) : (
          <p className="waiting">Waiting for the host to start another game.</p>
        )}
        <button type="button" className="button button--quiet" onClick={() => void leaveRoom()}>
          Leave room
        </button>
      </div>
    </div>
  );
}
