import { useEffect, useRef, useState, type KeyboardEvent, type MouseEvent } from 'react';
import type { ChatLine } from '../state/reducer';
import { useApp } from '../state/store';
import { ChatIcon, ChevronDownIcon, SendIcon } from './Icons';

/** Mid-round the server accepts only these: they carry no letters, so nobody can pass hints. */
const RACE_REACTIONS = ['gg', 'so close', 'nooo', 'how?!', 'lol'];
const CALM_REACTIONS = ['gg', 'again', 'so close', 'lol'];
const MAX_LENGTH = 200;
const PREVIEW_MS = 5_000;
const PHONE = '(max-width: 700px)';
const INPUT_ID = 'chat-input';

export function useIsPhone(): boolean {
  const [phone, setPhone] = useState(() => window.matchMedia(PHONE).matches);
  useEffect(() => {
    const query = window.matchMedia(PHONE);
    const onChange = () => setPhone(query.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);
  return phone;
}

/** Messages and reactions from other players that arrived since the chat was last on screen. */
function useUnread(): { count: number; latest: ChatLine | null } {
  const { game, chatReadId } = useApp();
  const unread = (game?.chat ?? []).filter((l) => l.kind !== 'system' && l.userId !== game?.me && l.id > chatReadId);
  return { count: unread.length, latest: unread.at(-1) ?? null };
}

const isTyping = (target: EventTarget | null) =>
  target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement;

/**
 * Room chat. On a desktop it floats bottom-right (a launcher that opens a panel), or sits docked
 * in the lobby. On a phone it opens as a bottom sheet; screens with a header use {@link ChatButton}
 * there instead of the floating launcher. "/" opens it, Esc closes it.
 */
export function Chat({ docked = false, headerButtonOnPhone = false }: { docked?: boolean; headerButtonOnPhone?: boolean }) {
  const phone = useIsPhone();
  const { game, chatOpen, setChatOpen } = useApp();
  const pinned = docked && !phone;

  // Listens on the window, not the panel: mid-round focus deliberately leaves the panel so keys reach the board.
  useEffect(() => {
    const onKey = (event: globalThis.KeyboardEvent) => {
      if (document.querySelector('[role="dialog"]')) return;
      if (event.key === 'Escape' && chatOpen && !pinned) {
        if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
        setChatOpen(false);
        return;
      }
      if (event.key !== '/' || isTyping(event.target)) return;
      event.preventDefault();
      if (pinned) document.getElementById(INPUT_ID)?.focus();
      else setChatOpen(true);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [pinned, chatOpen, setChatOpen]);

  if (!game?.room) return null;
  const race = game.status === 'IN_ROUND';

  if (pinned) return <ChatPanel variant="docked" race={race} />;
  if (chatOpen) {
    const close = () => setChatOpen(false);
    return phone ? (
      <>
        <div className="chat-scrim" aria-hidden="true" onClick={close} />
        <ChatPanel variant="sheet" race={race} onClose={close} />
      </>
    ) : (
      <div className="chat-float">
        <ChatPanel variant="open" race={race} onClose={close} />
      </div>
    );
  }
  if (phone && headerButtonOnPhone) return null;
  return <Launcher race={race} onOpen={() => setChatOpen(true)} />;
}

/** The phone header's chat button, with an unread badge. Renders nothing on wider screens. */
export function ChatButton() {
  const phone = useIsPhone();
  const setChatOpen = useApp((s) => s.setChatOpen);
  const { count } = useUnread();
  if (!phone) return null;
  return (
    <button type="button" className="icon-btn chat-btn" aria-label={launcherLabel(count)} onClick={() => setChatOpen(true)}>
      <ChatIcon size={21} />
      {count > 0 && <span className="chat-badge">{badge(count)}</span>}
    </button>
  );
}

function Launcher({ race, onOpen }: { race: boolean; onOpen(): void }) {
  const { count, latest } = useUnread();
  const [expiredId, setExpiredId] = useState(0);

  // The newest message shows above the launcher for a few seconds (not mid-race: eyes on the board).
  useEffect(() => {
    if (!latest) return;
    const timer = setTimeout(() => setExpiredId(latest.id), PREVIEW_MS);
    return () => clearTimeout(timer);
  }, [latest]);
  const preview = !race && latest && latest.id > expiredId ? latest : null;

  return (
    <div className="chat-float">
      {preview && (
        <div className="chat-preview" role="status">
          <strong>{preview.name}</strong> {preview.text}
        </div>
      )}
      {race ? (
        <button type="button" className="chat-launcher chat-launcher--race" aria-label={launcherLabel(count)}
                aria-keyshortcuts="/" onClick={onOpen}>
          <ChatIcon size={24} />
          {count > 0 && <span className="chat-badge">{badge(count)}</span>}
        </button>
      ) : (
        <button type="button" className="chat-launcher" aria-label={launcherLabel(count)} aria-keyshortcuts="/" onClick={onOpen}>
          <ChatIcon />
          Chat
          {count > 0 && <span className="chat-launcher__count">{badge(count)}</span>}
          <span className="chat-launcher__key" aria-hidden="true">/</span>
        </button>
      )}
    </div>
  );
}

function ChatPanel({ variant, race, onClose }: { variant: 'open' | 'docked' | 'sheet'; race: boolean; onClose?(): void }) {
  const { game, sendChat, markChatRead } = useApp();
  const [draft, setDraft] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const lines = game?.chat ?? [];
  const lastId = lines.at(-1)?.id ?? 0;

  // Everything on screen counts as read.
  useEffect(() => {
    if (lastId) markChatRead(lastId);
  }, [lastId, markChatRead]);

  // Opening the panel puts the cursor in the box. Not mid-race: a focused chip would catch the Enter meant for a guess.
  useEffect(() => {
    if (variant !== 'docked' && !race) inputRef.current?.focus();
  }, [variant, race]);

  if (!game?.room) return null;
  const me = game.me;

  const close = () => {
    if (race && document.activeElement instanceof HTMLElement) document.activeElement.blur(); // keys go back to the board
    onClose?.();
  };

  const send = () => {
    const text = draft.trim();
    if (!text) return;
    sendChat(text, false);
    setDraft('');
  };

  const onInputKey = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') send();
  };

  const onPanelKey = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key !== 'Escape') return;
    if (onClose) close();
    else inputRef.current?.blur();
  };

  const react = (text: string, event: MouseEvent<HTMLButtonElement>) => {
    sendChat(text, race); // between rounds a chip is just a quick message
    if (race) event.currentTarget.blur();
  };

  return (
    <section className={`chat-panel chat-panel--${variant}`} aria-label="Room chat" onKeyDown={onPanelKey}>
      <div className="chat-panel__head">
        <h2>Chat</h2>
        <span>{game.room.players.length} in room</span>
        {onClose && (
          <button type="button" className="chat-panel__close" aria-label="Minimise chat" onClick={close}>
            <ChevronDownIcon />
          </button>
        )}
      </div>

      <div className="chat-log" role="log" aria-live="polite" aria-label="Messages" tabIndex={0}>
        {lines.length === 0 && <p className="chat-line chat-line--system">Say hi to the room</p>}
        {[...lines].reverse().map((line) => <Line key={line.id} line={line} mine={line.userId === me} />)}
      </div>

      <div className="chat-panel__foot">
        <div className={`chat-chips${race ? ' chat-chips--race' : ''}`} role="group" aria-label="Quick reactions">
          {(race ? RACE_REACTIONS : CALM_REACTIONS).map((text) => (
            <button key={text} type="button" onClick={(event) => react(text, event)}>{text}</button>
          ))}
        </div>
        {race ? (
          <p className="chat-hint">
            Reactions only while the round is on
            {variant === 'open' && <> · <kbd>Esc</kbd> back to your board</>}
          </p>
        ) : (
          <div className="chat-compose">
            <label htmlFor={INPUT_ID} className="visually-hidden">Message the room</label>
            <input id={INPUT_ID} ref={inputRef} value={draft} maxLength={MAX_LENGTH} autoComplete="off"
                   placeholder="Message the room" onChange={(e) => setDraft(e.target.value)} onKeyDown={onInputKey} />
            <button type="button" className="chat-send" aria-label="Send" onClick={send}>
              <SendIcon />
            </button>
          </div>
        )}
      </div>
    </section>
  );
}

function Line({ line, mine }: { line: ChatLine; mine: boolean }) {
  if (line.kind === 'system') return <p className="chat-line chat-line--system">{line.text}</p>;
  if (line.kind === 'reaction') {
    return (
      <p className={`chat-line chat-line--reaction${mine ? ' is-mine' : ''}`}>
        <span className="chat-line__name">{mine ? 'you' : line.name}</span>
        <span className="chat-pill">{line.text}</span>
      </p>
    );
  }
  return (
    <p className={`chat-line chat-line--message${mine ? ' is-mine' : ''}`}>
      {mine ? <span className="visually-hidden">You: </span> : <span className="chat-line__name">{line.name}</span>}
      <span className="chat-bubble">{line.text}</span>
    </p>
  );
}

function badge(count: number): string {
  return count > 99 ? '99+' : String(count);
}

function launcherLabel(count: number): string {
  return count > 0 ? `Open chat, ${count} unread` : 'Open chat';
}
