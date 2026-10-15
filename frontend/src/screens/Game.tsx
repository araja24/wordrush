import { useCallback, useEffect, useRef, useState } from 'react';
import type { PlayerView, Standing } from '../api/types';
import { Board } from '../components/Board';
import { Chat, ChatButton } from '../components/Chat';
import { HelpIcon, MenuIcon, SpinnerIcon } from '../components/Icons';
import { Keyboard } from '../components/Keyboard';
import { MiniBoard } from '../components/MiniBoard';
import { secondsUntil, useServerNow } from '../components/useServerNow';
import { bestGreens, botName, letterStates } from '../state/reducer';
import { useApp } from '../state/store';

const PENDING_TIMEOUT_MS = 5_000;

export function formatClock(seconds: number): string {
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

/** Display name for a player: bots drop their "(hard)" suffix. */
export function displayName(player: Pick<PlayerView, 'nickname' | 'bot'>): string {
  return player.bot ? botName(player.nickname).name : player.nickname;
}

export function Game() {
  const { game, guess, lastResult, showToast, connected, setHelpOpen, leaveRoom } = useApp();
  const now = useServerNow(game?.clockOffset ?? 0);
  const [input, setInput] = useState('');
  const [pending, setPending] = useState(false);
  const [invalid, setInvalid] = useState(false);
  const [shake, setShake] = useState(0);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const round = game?.round ?? null;
  const myGuesses = round?.myGuesses ?? [];
  const solved = myGuesses.at(-1)?.feedback === 'GGGGG';
  const canType = game?.status === 'IN_ROUND' && !solved && myGuesses.length < 6 && !pending;

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
        setInvalid(true);
        setShake((n) => n + 1);
        showToast('Not in the word list');
        break;
      case 'ROUND_OVER':
        showToast('That round is already over');
        break;
      case 'OUT_OF_GUESSES':
        showToast('You’ve used all six guesses');
        break;
      case 'CORRECT_LATE':
        break; // the round result screen tells this story
    }
  }, [lastResult, showToast]);

  useEffect(() => {
    if (!pending) return;
    const timer = setTimeout(() => setPending(false), PENDING_TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [pending]);

  // Close the menu on outside click or Escape.
  useEffect(() => {
    if (!menuOpen) return;
    const onClick = (event: MouseEvent) => {
      if (!menuRef.current?.contains(event.target as Node)) setMenuOpen(false);
    };
    const onKey = (event: KeyboardEvent) => event.key === 'Escape' && setMenuOpen(false);
    window.addEventListener('mousedown', onClick);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('mousedown', onClick);
      window.removeEventListener('keydown', onKey);
    };
  }, [menuOpen]);

  const submit = useCallback(() => {
    if (!canType) return;
    if (input.length < 5) {
      setShake((n) => n + 1);
      return;
    }
    setPending(true);
    guess(input);
  }, [canType, input, guess]);

  const type = useCallback((letter: string) => {
    if (!canType) return;
    setInvalid(false);
    setInput((s) => (s.length < 5 ? s + letter : s));
  }, [canType]);

  const erase = useCallback(() => {
    if (!canType) return;
    setInvalid(false);
    setInput((s) => s.slice(0, -1));
  }, [canType]);

  // Physical keyboard.
  useEffect(() => {
    function onKey(event: KeyboardEvent) {
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      if (event.target instanceof HTMLInputElement || document.querySelector('[role="dialog"]')) return;
      if (event.key === 'Enter') submit();
      else if (event.key === 'Backspace') erase();
      else if (/^[a-zA-Z]$/.test(event.key)) type(event.key.toUpperCase());
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [submit, erase, type]);

  if (!game?.room) return null;
  const { room, me, roundNo, phaseEndsAt, leaderboard } = game;
  const secondsLeft = secondsUntil(phaseEndsAt, now);
  const barWidth = Math.min(100, (secondsLeft / room.settings.roundSeconds) * 100);
  const opponents = room.players.filter((p) => p.userId !== me);
  const rowsOf = (userId: string) => round?.opponents[userId] ?? [];
  const hotId = leaderOf(opponents.map((p) => [p.userId, bestGreens(rowsOf(p.userId))] as const));

  return (
    <main className={`game${connected ? '' : ' is-offline'}`}>
      <header className="game-header">
        <div className="game-header__round">
          <ChatButton />
          <span className="round-long">Round <strong>{Math.max(roundNo, 1)}</strong> of {room.settings.totalRounds}</span>
          <span className="round-short" aria-hidden="true"><strong>{Math.max(roundNo, 1)}</strong>/{room.settings.totalRounds}</span>
        </div>
        <div className="timer" role="timer" aria-label={`${secondsLeft} seconds left`}>{formatClock(secondsLeft)}</div>
        <div className="game-header__tools" ref={menuRef}>
          <button type="button" className="icon-btn" aria-label="How to play" onClick={() => setHelpOpen(true)}>
            <HelpIcon />
          </button>
          <button type="button" className="icon-btn" aria-label="Menu" aria-expanded={menuOpen} onClick={() => setMenuOpen((o) => !o)}>
            <MenuIcon />
          </button>
          {menuOpen && (
            <div className="menu" role="menu">
              <button type="button" role="menuitem" onClick={() => { setMenuOpen(false); setHelpOpen(true); }}>How to play</button>
              <button type="button" role="menuitem" onClick={() => void leaveRoom()}>Leave room</button>
            </div>
          )}
        </div>
      </header>
      <div className="bar" aria-hidden="true"><div style={{ width: `${barWidth}%` }} /></div>

      {!connected && (
        <div className="reconnecting" role="status">
          <SpinnerIcon />
          <div>
            <strong>Reconnecting…</strong>
            <span>Your guesses are safe. The clock isn’t paused.</span>
          </div>
        </div>
      )}

      <div className="game-main">
        <Scoreboard standings={leaderboard} players={room.players} me={me} />

        <div className="opp-strip" aria-label="Opponents">
          {opponents.map((player) => (
            <div key={player.userId}
                 className={`opp-strip__item${player.userId === hotId ? ' is-hot' : ''}${player.connected ? '' : ' is-away'}`}>
              <MiniBoard rows={rowsOf(player.userId)} />
              <span className="opp-strip__name">{displayName(player)}</span>
            </div>
          ))}
        </div>

        <section className="play" aria-label="Your board">
          <Board guesses={myGuesses} current={input} shake={shake} invalid={invalid} />
          <Keyboard states={letterStates(myGuesses)} disabled={!canType} onLetter={type} onEnter={submit} onBackspace={erase} />
        </section>

        <aside className="opponents" aria-label="Opponents">
          {opponents.map((player) => {
            const rows = rowsOf(player.userId);
            const hot = player.userId === hotId;
            return (
              <div key={player.userId} className={`opp${hot ? ' opp--hot' : ''}${player.connected ? '' : ' opp--away'}`}>
                <MiniBoard rows={rows} />
                <div className="opp__text">
                  <span className="opp__name">{displayName(player)}</span>
                  <span className="opp__status">{opponentStatus(player, rows, hot)}</span>
                </div>
              </div>
            );
          })}
        </aside>
      </div>
      <Chat headerButtonOnPhone />
    </main>
  );
}

/** The opponent closest to solving (most greens, at least 3), if any. */
function leaderOf(entries: (readonly [string, number])[]): string | null {
  let best: string | null = null;
  let bestGreens = 2;
  for (const [userId, greens] of entries) {
    if (greens > bestGreens) {
      best = userId;
      bestGreens = greens;
    }
  }
  return best;
}

function opponentStatus(player: PlayerView, rows: string[], hot: boolean): string {
  let status: string;
  if (!player.connected) status = 'reconnecting';
  else if (hot) status = `${bestGreens(rows)} of 5`;
  else if (rows.length === 0) status = 'thinking';
  else if (rows.length >= 6) status = 'out of guesses';
  else status = `row ${rows.length}`;
  return player.bot && player.connected ? `bot · ${status}` : status;
}

/** Points so far; before anyone scores, everyone shows 0 in join order. */
function Scoreboard({ standings, players, me }: { standings: Standing[]; players: PlayerView[]; me: string }) {
  const byId = new Map(players.map((p) => [p.userId, p]));
  const rows = standings.length > 0
    ? standings.filter((s) => byId.has(s.userId)).map((s) => ({ userId: s.userId, score: s.score }))
    : players.map((p) => ({ userId: p.userId, score: 0 }));
  return (
    <ol className="scoreboard" aria-label="Scoreboard">
      {rows.map(({ userId, score }) => {
        const player = byId.get(userId)!;
        return (
          <li key={userId} className={userId === me ? 'is-me' : undefined}>
            <span>{displayName(player)}</span>
            <span className={`score${score > 0 ? ' score--scored' : ''}`}>{score}</span>
          </li>
        );
      })}
    </ol>
  );
}
