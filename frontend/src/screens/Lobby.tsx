import { useState } from 'react';
import type { Difficulty } from '../api/types';
import { LetterTiles } from '../components/LetterTiles';
import { useApp } from '../state/store';

export function Lobby() {
  const { game, startGame, leaveRoom, kick, addBot } = useApp();
  const [copied, setCopied] = useState(false);
  const [difficulty, setDifficulty] = useState<Difficulty>('MEDIUM');
  if (!game?.room) return null;
  const { room, me } = game;
  const isHost = room.hostId === me;
  const full = room.players.length >= room.settings.maxPlayers;
  const inviteLink = `${window.location.origin}/?room=${room.code}`;

  async function copyInvite() {
    try {
      await navigator.clipboard.writeText(inviteLink);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setCopied(false);
    }
  }

  return (
    <main className="lobby">
      <section className="lobby__code" aria-labelledby="room-code-label">
        <p id="room-code-label">Room code</p>
        <LetterTiles text={room.code} label={`Room code ${room.code.split('').join(' ')}`} size="lg" />
        <button type="button" className="button button--quiet" onClick={() => void copyInvite()}>
          {copied ? 'Link copied' : 'Copy invite link'}
        </button>
      </section>

      <section className="panel lobby__players" aria-labelledby="players-heading">
        <h2 id="players-heading">
          Players <span className="count">{room.players.length} of {room.settings.maxPlayers}</span>
        </h2>
        <ul className="roster">
          {room.players.map((player) => (
            <li key={player.userId}>
              <span>
                {player.userId === me ? `${player.nickname} (you)` : player.nickname}
                {player.host && <span className="badge badge--host">host</span>}
                {player.bot && <span className="badge">bot</span>}
              </span>
              {isHost && player.userId !== me && (
                <button type="button" className="link" onClick={() => void kick(player.userId)}>
                  Remove
                </button>
              )}
            </li>
          ))}
        </ul>
        {isHost && (
          <div className="add-bot">
            <label className="field">
              <span>Bot difficulty</span>
              <select value={difficulty} onChange={(e) => setDifficulty(e.target.value as Difficulty)}>
                <option value="EASY">Easy: slow, often guesses blind</option>
                <option value="MEDIUM">Medium: about a minute per word</option>
                <option value="HARD">Hard: about 30 seconds per word</option>
              </select>
            </label>
            <button type="button" className="button" disabled={full} onClick={() => void addBot(difficulty)}>
              Add bot
            </button>
          </div>
        )}
        <p className="hint">
          {room.settings.totalRounds} rounds, {room.settings.roundSeconds} seconds each.
        </p>
      </section>

      <div className="lobby__actions">
        {isHost ? (
          <button type="button" className="button button--primary" onClick={startGame}>
            Start game
          </button>
        ) : (
          <p className="waiting">Waiting for the host to start.</p>
        )}
        <button type="button" className="button button--quiet" onClick={() => void leaveRoom()}>
          Leave room
        </button>
      </div>
    </main>
  );
}
