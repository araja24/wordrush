const ROWS = ['QWERTYUIOP', 'ASDFGHJKL', 'ZXCVBNM'];

interface Props {
  states: Record<string, 'G' | 'Y' | 'B'>;
  disabled: boolean;
  onLetter(letter: string): void;
  onEnter(): void;
  onBackspace(): void;
}

/** On-screen keyboard; each key shows the best colour you've found for that letter. */
export function Keyboard({ states, disabled, onLetter, onEnter, onBackspace }: Props) {
  return (
    <div className="keyboard" aria-label="Keyboard">
      {ROWS.map((row, r) => (
        <div key={row} className="keyboard__row">
          {r === 2 && (
            <button type="button" className="key key--wide" disabled={disabled} onClick={onEnter}>
              Enter
            </button>
          )}
          {[...row].map((letter) => (
            <button key={letter} type="button" className={`key${states[letter] ? ` key--${states[letter]}` : ''}`}
                    disabled={disabled} onClick={() => onLetter(letter)}>
              {letter}
            </button>
          ))}
          {r === 2 && (
            <button type="button" className="key key--wide" disabled={disabled} onClick={onBackspace} aria-label="Delete letter">
              ⌫
            </button>
          )}
        </div>
      ))}
    </div>
  );
}
