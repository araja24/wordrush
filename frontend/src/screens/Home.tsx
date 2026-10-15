import { useRef, useState, type ClipboardEvent, type FormEvent, type KeyboardEvent } from 'react';
import { HowTo } from '../components/HowTo';
import { Logo } from '../components/Logo';
import { useApp } from '../state/store';

const CODE_LENGTH = 6;
const SECOND_OPTIONS = [60, 90, 120, 180];
const NICKNAME = /^[A-Za-z0-9_]{2,16}$/;

function inviteCode(): string[] {
  const code = (new URLSearchParams(window.location.search).get('room') ?? '').toUpperCase().slice(0, CODE_LENGTH);
  return Array.from({ length: CODE_LENGTH }, (_, i) => code[i] ?? '');
}

export function Home() {
  const { auth, signIn, joinRoom, createRoom } = useApp();
  const [nickname, setNickname] = useState(auth?.user.nickname ?? '');
  const [mode, setMode] = useState<'join' | 'create'>('join');
  const [code, setCode] = useState<string[]>(inviteCode);
  const [rounds, setRounds] = useState(5);
  const [seconds, setSeconds] = useState(90);
  const [maxPlayers, setMaxPlayers] = useState(8);
  const [busy, setBusy] = useState(false);
  const boxes = useRef<(HTMLInputElement | null)[]>([]);

  const nicknameOk = NICKNAME.test(nickname);
  const codeText = code.join('');
  const canJoin = nicknameOk && codeText.length === CODE_LENGTH;

  /** Sign in (or re-sign in under a new nickname) only when we have to, then do the action. */
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!nicknameOk || (mode === 'join' && !canJoin) || busy) return;
    setBusy(true);
    const signedIn = auth?.user.nickname === nickname || (await signIn(nickname));
    if (signedIn) {
      if (mode === 'join') await joinRoom(codeText);
      else await createRoom({ totalRounds: rounds, roundSeconds: seconds, maxPlayers });
    }
    setBusy(false);
  }

  function setBox(index: number, value: string) {
    const letter = value.replace(/[^A-Za-z0-9]/g, '').slice(-1).toUpperCase();
    setCode((current) => current.map((c, i) => (i === index ? letter : c)));
    if (letter && index < CODE_LENGTH - 1) boxes.current[index + 1]?.focus();
  }

  function onBoxKey(index: number, event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Backspace' && !code[index] && index > 0) boxes.current[index - 1]?.focus();
  }

  function onBoxPaste(event: ClipboardEvent<HTMLInputElement>) {
    const pasted = event.clipboardData.getData('text').replace(/[^A-Za-z0-9]/g, '').toUpperCase().slice(0, CODE_LENGTH);
    if (!pasted) return;
    event.preventDefault();
    setCode(Array.from({ length: CODE_LENGTH }, (_, i) => pasted[i] ?? ''));
    boxes.current[Math.min(pasted.length, CODE_LENGTH - 1)]?.focus();
  }

  return (
    <main className="home">
      <div className="home__inner">
        <Logo />
        <p className="home__pitch">Same five-letter word for everyone. First to solve it takes the round.</p>

        <div className="home__columns">
          <form className="card home__card" onSubmit={submit}>
            <div className="field">
              <label htmlFor="nick" className="field-label">Your nickname</label>
              <input id="nick" className="text-input" value={nickname} maxLength={16} autoComplete="nickname"
                     placeholder="2–16 letters, numbers or _" onChange={(e) => setNickname(e.target.value)} />
            </div>

            <div className="segmented" role="group" aria-label="Join or start a room">
              <button type="button" aria-pressed={mode === 'join'} onClick={() => setMode('join')}>Join room</button>
              <button type="button" aria-pressed={mode === 'create'} onClick={() => setMode('create')}>New room</button>
            </div>

            {mode === 'join' ? (
              <div className="home__panel">
                <fieldset className="code-boxes">
                  <legend className="field-label">Room code</legend>
                  <div className="code-boxes__row">
                    {code.map((letter, i) => (
                      <input key={i} ref={(el) => { boxes.current[i] = el; }} value={letter} maxLength={2}
                             aria-label={`Room code character ${i + 1}`} autoComplete="off" spellCheck={false}
                             onChange={(e) => setBox(i, e.target.value)} onKeyDown={(e) => onBoxKey(i, e)}
                             onPaste={onBoxPaste} />
                    ))}
                  </div>
                </fieldset>
                <button type="submit" className="btn btn--primary" disabled={!canJoin || busy}>
                  Join<span aria-hidden="true" className="kbd-hint">Enter</span>
                </button>
              </div>
            ) : (
              <div className="home__panel" style={{ gap: 16 }}>
                <div className="setting-row">
                  <span id="rounds-label" className="setting-label">Rounds</span>
                  <Stepper labelledBy="rounds-label" value={rounds} min={1} max={10} onChange={setRounds}
                           lessLabel="Fewer rounds" moreLabel="More rounds" />
                </div>
                <div className="field">
                  <span id="seconds-label" className="setting-label">Seconds per round</span>
                  <div className="segmented segmented--light" role="group" aria-labelledby="seconds-label">
                    {SECOND_OPTIONS.map((option) => (
                      <button key={option} type="button" aria-pressed={seconds === option} onClick={() => setSeconds(option)}>
                        {option}
                      </button>
                    ))}
                  </div>
                </div>
                <div className="setting-row">
                  <span id="max-label" className="setting-label">Max players <small>· bots count</small></span>
                  <Stepper labelledBy="max-label" value={maxPlayers} min={2} max={8} onChange={setMaxPlayers}
                           lessLabel="Fewer players" moreLabel="More players" />
                </div>
                <button type="submit" className="btn btn--primary" style={{ marginTop: 4 }} disabled={!nicknameOk || busy}>
                  Create room<span aria-hidden="true" className="kbd-hint">Enter</span>
                </button>
              </div>
            )}
          </form>

          <div className="home__help">
            <HowTo />
          </div>
        </div>
      </div>
    </main>
  );
}

interface StepperProps {
  labelledBy: string;
  value: number;
  min: number;
  max: number;
  onChange(value: number): void;
  lessLabel: string;
  moreLabel: string;
}

function Stepper({ labelledBy, value, min, max, onChange, lessLabel, moreLabel }: StepperProps) {
  return (
    <div className="stepper" role="group" aria-labelledby={labelledBy}>
      <button type="button" aria-label={lessLabel} disabled={value <= min} onClick={() => onChange(value - 1)}>−</button>
      <output aria-live="polite">{value}</output>
      <button type="button" aria-label={moreLabel} disabled={value >= max} onClick={() => onChange(value + 1)}>+</button>
    </div>
  );
}
