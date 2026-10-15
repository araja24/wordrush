import { useEffect } from 'react';
import { useApp } from '../state/store';
import { displayName } from './Game';

/** Final standings, the words of the game, and (for the host) Play again. */
export function Final() {
  const { game, playAgain, leaveRoom } = useApp();
  const isHost = game?.room?.hostId === game?.me;

  useEffect(() => {
    if (!isHost) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Enter' && !(event.target instanceof HTMLButtonElement) && !document.querySelector('[role="dialog"]')) {
        playAgain();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [isHost, playAgain]);

  if (!game?.room) return null;
  const { room, me, leaderboard, history } = game;
  const byId = new Map(room.players.map((p) => [p.userId, p]));
  const standings = leaderboard.filter((s) => byId.has(s.userId));
  const top = standings[0];
  const tie = standings.filter((s) => s.rank === 1).length > 1;
  const host = room.players.find((p) => p.host);
  const nameOf = (userId: string | null) => {
    if (userId === me) return 'You';
    const player = userId ? byId.get(userId) : undefined;
    return player ? displayName(player) : 'Someone';
  };

  return (
    <main className="final">
      <div className="final__inner">
        <section className="final__head">
          <div>
            <p className="final__eyebrow">Game over</p>
            <h1 className="final__headline">
              {!top || tie ? 'It’s a tie' : top.userId === me ? <>You <em>win</em></> : <>{nameOf(top.userId)} <em>wins</em></>}
            </h1>
          </div>
          {isHost ? (
            <button type="button" className="btn btn--primary" style={{ height: 58, padding: '0 28px', fontSize: 20 }} onClick={playAgain}>
              Play again<span aria-hidden="true" className="kbd-hint">Enter</span>
            </button>
          ) : (
            <p className="waiting">Waiting for {host ? displayName(host) : 'the host'} to start the next game</p>
          )}
        </section>

        <div className="final__body">
          <ol className="standings" aria-label="Standings">
            {standings.map((s) => (
              <li key={s.userId}
                  className={`standing${s.rank === 1 ? ' standing--first' : ''}${s.userId === me ? ' is-me' : ''}`}>
                <span className="standing__place">{s.rank}</span>
                <span className="standing__name">{nameOf(s.userId)}</span>
                <span className={`score${s.score > 0 ? ' score--scored' : ''}`}>{s.score}</span>
              </li>
            ))}
          </ol>

          <section className="words" aria-label="Words this game">
            {history.map((round) => {
              const solved = round.reason === 'SOLVED';
              return (
                <div key={round.roundNo} className="words__row">
                  <span className={`words__tiles${solved ? ' words__tiles--solved' : ''}`} role="img" aria-label={round.answer}>
                    {[...round.answer].map((letter, i) => <span key={i}>{letter}</span>)}
                  </span>
                  <span className={`words__who${solved ? '' : ' words__who--none'}`}>
                    {solved ? nameOf(round.winnerId) : round.reason === 'TIMEOUT' ? 'time ran out' : 'nobody got it'}
                  </span>
                </div>
              );
            })}
            <button type="button" className="link-btn" style={{ alignSelf: 'flex-start', marginTop: history.length ? 10 : 0 }}
                    onClick={() => void leaveRoom()}>
              Leave room
            </button>
          </section>
        </div>
      </div>
    </main>
  );
}
