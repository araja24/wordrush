import type { PlayerView } from '../api/types';

interface Props {
  players: PlayerView[];
  rowsByPlayer: Record<string, string[]>;
  me: string;
  winnerId: string | null;
}

/** Everyone's progress as colour rows filling a lane, left to right. Letters are never shown. */
export function RaceLanes({ players, rowsByPlayer, me, winnerId }: Props) {
  return (
    <ol className="lanes" aria-label="Race">
      {players.map((player) => {
        const rows = rowsByPlayer[player.userId] ?? [];
        const solved = winnerId === player.userId;
        return (
          <li key={player.userId} className={`lane${player.userId === me ? ' lane--me' : ''}${solved ? ' lane--winner' : ''}`}>
            <span className="lane__name">
              {player.userId === me ? 'You' : player.nickname}
              {player.bot && <span className="badge">bot</span>}
            </span>
            <span className="lane__track" aria-label={`${rows.length} of 6 guesses used${solved ? ', solved' : ''}`}>
              {Array.from({ length: 6 }, (_, i) => (
                <span key={i} className={`lane__row${rows[i] ? '' : ' lane__row--empty'}`}>
                  {rows[i] && [...rows[i]].map((tile, j) => <span key={j} className={`pip pip--${tile}`} />)}
                </span>
              ))}
            </span>
          </li>
        );
      })}
    </ol>
  );
}
