import { useEffect } from 'react';
import { Game } from './screens/Game';
import { Home } from './screens/Home';
import { Lobby } from './screens/Lobby';
import { useApp } from './state/store';

export function App() {
  const { auth, game, resuming, resume, toast } = useApp();
  const signedIn = auth !== null;

  // After a refresh (or a fresh sign-in), go back to the room you were in, if any.
  useEffect(() => {
    if (signedIn) void resume();
  }, [signedIn, resume]);

  let screen;
  if (resuming && !game) screen = <p className="splash">Reconnecting to your room…</p>;
  else if (!game) screen = <Home />;
  else if (game.status === 'LOBBY') screen = <Lobby />;
  else screen = <Game />;

  return (
    <>
      {screen}
      <div className="toast-region" role="status" aria-live="polite">
        {toast && <p className="toast" key={toast.id}>{toast.text}</p>}
      </div>
    </>
  );
}
