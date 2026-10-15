import { useEffect, useRef } from 'react';
import { CloseIcon } from './Icons';

const LEGEND = [
  { letter: 'R', tile: 'G', text: 'is in the word and in the right spot' },
  { letter: 'S', tile: 'Y', text: 'is in the word but in the wrong spot' },
  { letter: 'T', tile: 'B', text: 'isn’t in the word at all' },
] as const;
const EXAMPLE = { word: 'TRIPS', feedback: 'BGGBY' };

function SmallTile({ letter, tile, size, font }: { letter: string; tile: string; size: number; font: number }) {
  return (
    <span className={`small-tile tile--${tile}`} style={{ width: size, height: size + 4, fontSize: font }}>
      {letter}
    </span>
  );
}

/** The rules. A plain panel on the home screen; a dialog during play. */
export function HowTo({ onClose }: { onClose?: () => void }) {
  const dialog = onClose !== undefined;
  return (
    <section className={`howto${dialog ? ' howto--dialog' : ''}`} aria-labelledby="howto-title">
      <div className="howto__head">
        <h2 id="howto-title" className="howto__title">How to play</h2>
        {dialog && (
          <button type="button" className="icon-btn" aria-label="Close" onClick={onClose}
                  style={{ background: 'var(--surface-2)' }}>
            <CloseIcon />
          </button>
        )}
      </div>
      <p className="howto__intro">
        Everyone gets the same hidden five-letter word. Solve it in six guesses, before anyone else does.
      </p>
      <div className="howto__example">
        <div className="howto__example-row">
          <div role="img" aria-label="Example guess TRIPS" style={{ display: 'flex', gap: 5 }}>
            {[...EXAMPLE.word].map((letter, i) => (
              <SmallTile key={i} letter={letter} tile={EXAMPLE.feedback[i]} size={46} font={30} />
            ))}
          </div>
          <span className="howto__note">if the word is BRISK</span>
        </div>
        <ul className="howto__legend">
          {LEGEND.map((item) => (
            <li key={item.letter}>
              <span aria-hidden="true">
                <SmallTile letter={item.letter} tile={item.tile} size={30} font={20} />
              </span>
              <span>
                <strong>{item.letter}</strong> {item.text}
              </span>
            </li>
          ))}
        </ul>
      </div>
      <ul className="howto__rules">
        <li>First correct guess wins the round. A tie goes to whoever was faster, to the millisecond.</li>
        <li>Use all six guesses, or let the clock run out, and you’re done for that round.</li>
        <li>You see everyone’s colours as they race. Never their letters.</li>
      </ul>
      <div className="howto__keys">
        <span><kbd>Enter</kbd> guess</span>
        <span><kbd>Backspace</kbd> delete</span>
        <span><kbd>?</kbd> this help</span>
      </div>
    </section>
  );
}

/** How to play as a modal: Escape or the backdrop closes it; focus moves in and comes back. */
export function HowToDialog({ onClose }: { onClose: () => void }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    ref.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
      previous?.focus();
    };
  }, [onClose]);

  return (
    <div className="backdrop" onClick={(event) => event.target === event.currentTarget && onClose()}>
      <div ref={ref} role="dialog" aria-modal="true" aria-label="How to play" tabIndex={-1}
           style={{ width: '100%', maxWidth: 480, outline: 'none' }}>
        <HowTo onClose={onClose} />
      </div>
    </div>
  );
}
