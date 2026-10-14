import type { Standing } from '../api/types';

export function Leaderboard({ standings, me }: { standings: Standing[]; me: string }) {
  if (standings.length === 0) return null;
  return (
    <table className="leaderboard">
      <caption>Scores</caption>
      <tbody>
        {standings.map((row) => (
          <tr key={row.userId} className={row.userId === me ? 'leaderboard__me' : undefined}>
            <td className="leaderboard__rank">{row.rank}</td>
            <td>{row.userId === me ? 'You' : row.nickname}</td>
            <td className="leaderboard__score">{row.score}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
