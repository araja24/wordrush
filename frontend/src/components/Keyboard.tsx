import { BackspaceIcon } from './Icons';

const ROWS = ['QWERTYUIOP', 'ASDFGHJKL', 'ZXCVBNM'];
const STATE_NAMES = { G: 'correct spot', Y: 'in the word', B: 'not in the word' } as const;

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
          {[...row].map((letter) => {
            const state = states[letter];
            return (
              <button key={letter} type="button" className={`key${state ? ` key--${state}` : ''}`} disabled={disabled}
                      aria-label={`${letter}, ${state ? STATE_NAMES[state] : 'not tried'}`}
                      onClick={() => onLetter(letter)}>
                {letter}
              </button>
            );
          })}
          {r === 2 && (
            <button type="button" className="key key--wide" disabled={disabled} onClick={onBackspace}
                    aria-label="Delete letter">
              <BackspaceIcon />
            </button>
          )}
        </div>
      ))}
    </div>
  );
}
