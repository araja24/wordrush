import { useEffect, useRef } from 'react';
import { HowToDialog } from './components/HowTo';
import { Countdown } from './screens/Countdown';
import { Final } from './screens/Final';
import { Game } from './screens/Game';
import { Home } from './screens/Home';
import { Lobby } from './screens/Lobby';
import { Removed } from './screens/Removed';
import { RoundResult } from './screens/RoundResult';
import { useApp } from './state/store';

export function App() {
  const { auth, game, resuming, resume, toast, clearToast, removedFrom, helpOpen, setHelpOpen } = useApp();
  const signedIn = auth !== null;

  // After a refresh (or a fresh sign-in), go back to the room you were in, if any.
  useEffect(() => {
    if (signedIn) void resume();
  }, [signedIn, resume]);

  // A toast belongs to the moment it was raised: moving to a new game phase starts clean.
  // (Not when leaving a room, which may itself raise a toast such as "session expired".)
  const status = game?.status;
  const previousStatus = useRef(status);
  useEffect(() => {
    if (previousStatus.current && status && previousStatus.current !== status) clearToast();
    previousStatus.current = status;
  }, [status, clearToast]);

  // "?" opens How to play from anywhere except while typing.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === '?' && !(event.target instanceof HTMLInputElement)) setHelpOpen(!useApp.getState().helpOpen);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [setHelpOpen]);

  let screen;
  if (removedFrom) screen = <Removed code={removedFrom} />;
  else if (resuming && !game) screen = <p className="splash">Reconnecting to your room…</p>;
  else if (!game) screen = <Home />;
  else if (game.status === 'LOBBY') screen = <Lobby />;
  else if (game.status === 'COUNTDOWN') screen = <Countdown />;
  else if (game.status === 'IN_ROUND') screen = <Game />;
  else if (game.status === 'ROUND_RESULTS') screen = <RoundResult />;
  else screen = <Final />;

  return (
    <>
      {screen}
      {helpOpen && <HowToDialog onClose={() => setHelpOpen(false)} />}
      <div className="toast-region" role="status" aria-live="polite">
        {toast && <p className="toast" key={toast.id}>{toast.text}</p>}
      </div>
    </>
  );
}
