import { useEffect, useState } from 'react';
import type { Difficulty, PlayerView } from '../api/types';
import { Chat, ChatButton } from '../components/Chat';
import { CloseIcon, CrownIcon, HelpIcon, LinkIcon, ShareIcon } from '../components/Icons';
import { LogoMini } from '../components/Logo';
import { botName } from '../state/reducer';
import { useApp } from '../state/store';

const BOT_LEVELS: { level: Difficulty; label: string }[] = [
  { level: 'EASY', label: 'Easy' },
  { level: 'MEDIUM', label: 'Medium' },
  { level: 'HARD', label: 'Hard' },
];

export function Lobby() {
  const { game, startGame, leaveRoom, kick, addBot, setHelpOpen } = useApp();
  const [copied, setCopied] = useState<'link' | 'code' | null>(null);
  const room = game?.room;
  const isHost = room?.hostId === game?.me;

  // Enter starts the game for the host (unless typing somewhere).
  useEffect(() => {
    if (!isHost) return;
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Enter' && !(event.target instanceof HTMLInputElement) && !(event.target instanceof HTMLButtonElement)) {
        startGame();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [isHost, startGame]);

  if (!game || !room) return null;
  const me = game.me;
  const full = room.players.length >= room.settings.maxPlayers;
  const emptySeats = Math.max(0, room.settings.maxPlayers - room.players.length);
  const host = room.players.find((p) => p.host);
  const inviteLink = `${window.location.origin}/?room=${room.code}`;
  const canShare = typeof navigator.share === 'function';

  async function copy(text: string, what: 'link' | 'code') {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(what);
      setTimeout(() => setCopied(null), 2000);
    } catch {
      setCopied(null);
    }
  }

  async function share() {
    try {
      await navigator.share({ title: 'WordRush', text: `Join my WordRush room ${room!.code}`, url: inviteLink });
    } catch {
      // dismissed
    }
  }

  return (
    <main className="page">
      <div className="page__inner">
        <header className="topbar">
          <LogoMini />
          <div className="topbar__actions">
            <ChatButton />
            <button type="button" className="btn btn--sm topbar__help" aria-label="How to play" onClick={() => setHelpOpen(true)}>
              <HelpIcon />
              <span>How to play</span>
            </button>
            <button type="button" className="btn btn--sm btn--outline btn--muted" onClick={() => void leaveRoom()}>
              Leave
            </button>
          </div>
        </header>

        <div className="lobby__layout">
          <section className="lobby__code">
            <h1>Room code</h1>
            <div className="code-tiles" role="img" aria-label={`Room code ${room.code.split('').join(' ')}`}>
              {[...room.code].map((letter, i) => <span key={i}>{letter}</span>)}
            </div>
            <div className="row-actions">
              {canShare ? (
                <button type="button" className="btn btn--cyan" onClick={() => void share()}>
                  <ShareIcon />
                  Share invite
                </button>
              ) : (
                <button type="button" className="btn btn--cyan" onClick={() => void copy(inviteLink, 'link')}>
                  <LinkIcon />
                  {copied === 'link' ? 'Link copied' : 'Copy invite link'}
                </button>
              )}
              <button type="button" className="btn btn--outline" onClick={() => void copy(room.code, 'code')}>
                {copied === 'code' ? 'Code copied' : 'Copy code'}
              </button>
            </div>
            <p className="room-settings">
              <strong>{room.settings.totalRounds}</strong> rounds · <strong>{room.settings.roundSeconds}s</strong> each ·{' '}
              <strong>{room.settings.maxPlayers}</strong> max
            </p>
            <Chat docked headerButtonOnPhone />
          </section>

          <section className="card players" aria-labelledby="players-heading">
            <div className="players__head">
              <h2 id="players-heading">Players</h2>
              <span className="count">{room.players.length}/{room.settings.maxPlayers}</span>
            </div>
            <ul className="roster">
              {room.players.map((player) => (
                <PlayerRow key={player.userId} player={player} isMe={player.userId === me}
                           canRemove={isHost && player.userId !== me} onRemove={() => void kick(player.userId)} />
              ))}
              {Array.from({ length: emptySeats }, (_, i) => (
                <li key={`empty-${i}`} className="roster__empty" aria-hidden="true" />
              ))}
            </ul>

            {isHost && (
              <div className="add-bot">
                <span id="bot-label">Add bot</span>
                <div className="add-bot__options" role="group" aria-labelledby="bot-label">
                  {BOT_LEVELS.map(({ level, label }) => (
                    <button key={level} type="button" disabled={full} onClick={() => void addBot(level)}>
                      {label}
                    </button>
                  ))}
                </div>
              </div>
            )}

            {isHost ? (
              <button type="button" className="btn btn--primary btn--big" style={{ marginTop: 6 }} onClick={startGame}>
                Start game<span aria-hidden="true" className="kbd-hint">Enter</span>
              </button>
            ) : (
              <p className="waiting">Waiting for {host?.nickname ?? 'the host'} to start the game.</p>
            )}
          </section>
        </div>
      </div>
    </main>
  );
}

function PlayerRow({ player, isMe, canRemove, onRemove }: {
  player: PlayerView;
  isMe: boolean;
  canRemove: boolean;
  onRemove(): void;
}) {
  const { name, level } = botName(player.nickname);
  const display = player.bot ? name : player.nickname;
  return (
    <li className="roster__row">
      <span className={`avatar${player.bot ? ' avatar--bot' : ''}`} aria-hidden="true">
        {display.charAt(0).toUpperCase()}
      </span>
      <span className="roster__name">{isMe ? `${display} (you)` : display}</span>
      {player.host && (
        <span className="roster__host">
          <CrownIcon />
          Host
        </span>
      )}
      {player.bot && <span className="roster__bot">{level ? `${level[0].toUpperCase()}${level.slice(1)} bot` : 'Bot'}</span>}
      {canRemove && (
        <button type="button" className="roster__remove" aria-label={`Remove ${display}`} onClick={onRemove}>
          <CloseIcon />
        </button>
      )}
    </li>
  );
}
