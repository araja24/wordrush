import { useState, type FormEvent } from 'react';
import { LetterTiles } from '../components/LetterTiles';
import { useApp } from '../state/store';

function inviteCode(): string {
  return new URLSearchParams(window.location.search).get('room')?.toUpperCase() ?? '';
}

export function Home() {
  const auth = useApp((s) => s.auth);
  return (
    <main className="home">
      <header className="home__intro">
        <h1>
          <LetterTiles text="WORDRUSH" label="WordRush" size="lg" />
        </h1>
        <p className="home__pitch">Same five-letter word for everyone. First to solve it takes the round.</p>
      </header>
      {auth ? <RoomChooser /> : <NicknameForm />}
    </main>
  );
}

function NicknameForm() {
  const signIn = useApp((s) => s.signIn);
  const [nickname, setNickname] = useState('');
  const [busy, setBusy] = useState(false);
  const valid = /^[A-Za-z0-9_]{2,16}$/.test(nickname);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!valid) return;
    setBusy(true);
    await signIn(nickname);
    setBusy(false);
  }

  return (
    <form className="panel" onSubmit={submit}>
      <label className="field">
        <span>Your nickname</span>
        <input value={nickname} onChange={(e) => setNickname(e.target.value)} autoFocus maxLength={16}
               autoComplete="nickname" placeholder="e.g. alex" aria-describedby="nickname-hint" />
      </label>
      <p id="nickname-hint" className="hint">2–16 letters, numbers or underscores.</p>
      <button className="button button--primary" disabled={!valid || busy}>
        Play
      </button>
    </form>
  );
}

function RoomChooser() {
  const { auth, createRoom, joinRoom, signOut } = useApp();
  const [code, setCode] = useState(inviteCode);
  const [rounds, setRounds] = useState(5);
  const [seconds, setSeconds] = useState(90);
  const [maxPlayers, setMaxPlayers] = useState(6);
  const [busy, setBusy] = useState(false);

  async function run(action: () => Promise<void>) {
    setBusy(true);
    await action();
    setBusy(false);
  }

  return (
    <div className="chooser">
      <form className="panel" onSubmit={(e) => { e.preventDefault(); void run(() => joinRoom(code)); }}>
        <h2>Join a room</h2>
        <label className="field">
          <span>Room code</span>
          <input className="input--code" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())}
                 maxLength={6} autoComplete="off" spellCheck={false} placeholder="ABC234" />
        </label>
        <button className="button button--primary" disabled={code.length !== 6 || busy}>
          Join room
        </button>
      </form>

      <form className="panel" onSubmit={(e) => { e.preventDefault(); void run(() => createRoom({ totalRounds: rounds, roundSeconds: seconds, maxPlayers })); }}>
        <h2>Start a room</h2>
        <div className="settings">
          <label className="field">
            <span>Rounds</span>
            <input type="number" min={1} max={10} value={rounds} onChange={(e) => setRounds(Number(e.target.value))} />
          </label>
          <label className="field">
            <span>Seconds per round</span>
            <input type="number" min={30} max={180} step={15} value={seconds} onChange={(e) => setSeconds(Number(e.target.value))} />
          </label>
          <label className="field">
            <span>Max players</span>
            <input type="number" min={2} max={8} value={maxPlayers} onChange={(e) => setMaxPlayers(Number(e.target.value))} />
          </label>
        </div>
        <button className="button" disabled={busy}>
          Create room
        </button>
      </form>

      <p className="signed-in">
        Playing as <strong>{auth?.user.nickname}</strong>.{' '}
        <button type="button" className="link" onClick={() => void signOut()}>
          Change nickname
        </button>
      </p>
    </div>
  );
}
