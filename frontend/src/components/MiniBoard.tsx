/** An opponent's six rows as colours only. Letters never leave the server. */
export function MiniBoard({ rows }: { rows: string[] }) {
  return (
    <div className="mini" aria-hidden="true">
      {Array.from({ length: 6 }, (_, r) => (
        <div key={r} className="mini__row">
          {Array.from({ length: 5 }, (_, c) => {
            const tile = rows[r]?.[c];
            return <span key={c} className={`mini__cell${tile ? ` mini__cell--${tile}` : ''}`} />;
          })}
        </div>
      ))}
    </div>
  );
}
