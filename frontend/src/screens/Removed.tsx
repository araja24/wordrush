import { useApp } from '../state/store';

/** Shown after the host removes you from a room. */
export function Removed({ code }: { code: string }) {
  const dismissRemoved = useApp((s) => s.dismissRemoved);
  return (
    <main className="removed">
      <div className="removed__body">
        <div className="removed__tiles" aria-hidden="true">
          <span className="bg-amber" />
          <span className="bg-cyan" />
          <span className="gap" />
          <span className="bg-amber" />
          <span className="bg-cyan" />
        </div>
        <h1>The host removed you from {code}</h1>
      </div>
      <button type="button" className="btn btn--primary btn--block" onClick={dismissRemoved}>
        Back to home
      </button>
    </main>
  );
}
