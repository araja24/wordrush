const COLOURS = ['bg-amber', 'bg-cyan', 'bg-white'];

/** "WORDRUSH" in tiles, cycling amber, cyan, white. */
export function Logo() {
  return (
    <h1 className="logo" aria-label="WordRush">
      {[...'WORDRUSH'].map((letter, i) => (
        <span key={i} aria-hidden="true" className={`logo__tile ${COLOURS[i % 3]}`}>
          {letter}
        </span>
      ))}
    </h1>
  );
}

/** The two-tile "WR" mark for page headers. */
export function LogoMini() {
  return (
    <div className="logo-mini" role="img" aria-label="WordRush">
      <span className="bg-amber">W</span>
      <span className="bg-cyan">R</span>
    </div>
  );
}
