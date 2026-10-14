/** Text set in game tiles: the logo and room codes. Purely visual; the label carries the meaning. */
export function LetterTiles({ text, label, size = 'md' }: { text: string; label?: string; size?: 'sm' | 'md' | 'lg' }) {
  return (
    <span className={`letter-tiles letter-tiles--${size}`} role="img" aria-label={label ?? text}>
      {[...text].map((letter, i) => (
        <span key={i} className="letter-tiles__tile" aria-hidden="true">
          {letter}
        </span>
      ))}
    </span>
  );
}
