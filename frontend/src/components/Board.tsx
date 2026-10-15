import type { CSSProperties } from 'react';
import type { GuessView } from '../api/types';

const ROWS = 6;
const COLS = 5;
const TILE_NAMES = { G: 'correct spot', Y: 'in the word', B: 'not in the word' } as const;

interface Props {
  guesses: GuessView[];
  current: string;
  shake: number;
  /** The typed word was rejected (not in the word list): outline it until it's edited. */
  invalid: boolean;
}

/** Your six rows: revealed guesses, the row you're typing, and empty rows. */
export function Board({ guesses, current, shake, invalid }: Props) {
  return (
    <div className="board" role="grid" aria-label="Your guesses">
      {Array.from({ length: ROWS }, (_, row) => {
        const guess = guesses[row];
        if (guess) {
          return (
            <div key={`${row}-${guess.word}`} className="board__row board__row--revealed" role="row">
              {[...guess.word].map((letter, i) => {
                const tile = guess.feedback[i] as keyof typeof TILE_NAMES;
                return (
                  <span key={i} role="gridcell" className={`tile tile--${tile}`}
                        style={{ '--i': i } as CSSProperties} aria-label={`${letter}, ${TILE_NAMES[tile]}`}>
                    {letter}
                  </span>
                );
              })}
            </div>
          );
        }
        const isCurrent = row === guesses.length;
        return (
          <div key={`${row}-input-${isCurrent ? shake : 0}`}
               className={`board__row${isCurrent && shake ? ' board__row--shake' : ''}`} role="row">
            {Array.from({ length: COLS }, (_, i) => {
              const letter = isCurrent ? (current[i] ?? '') : '';
              const look = !letter ? '' : invalid ? ' tile--invalid' : ' tile--typed';
              return (
                <span key={i} role="gridcell" className={`tile${look}`}>
                  {letter}
                </span>
              );
            })}
          </div>
        );
      })}
    </div>
  );
}
