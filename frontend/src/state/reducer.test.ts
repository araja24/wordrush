import { describe, expect, it } from 'vitest';
import type { GameSnapshot, RoomMessage, RoomSnapshot } from '../api/types';
import { initialState, letterStates, reduce, type GameState } from './reducer';

const ME = 'me';
const OTHER = 'other';

const room: RoomSnapshot = {
  code: 'ABC234',
  status: 'LOBBY',
  hostId: ME,
  settings: { totalRounds: 3, roundSeconds: 90, maxPlayers: 6 },
  players: [{ userId: ME, nickname: 'me', bot: false, connected: true, host: true }],
  createdAt: '2026-10-14T00:00:00Z',
  seq: 0,
};

function msg<T extends RoomMessage['type']>(type: T, seq: number, payload: Extract<RoomMessage, { type: T }>['payload']) {
  return { type: 'message', message: { type, roomCode: 'ABC234', seq, serverTime: 1_000, payload } as RoomMessage, receivedAt: 1_000 } as const;
}

function play(state: GameState, ...actions: Parameters<typeof reduce>[1][]): GameState {
  return actions.reduce(reduce, state);
}

describe('reduce', () => {
  it('runs a round from countdown to results', () => {
    const state = play(
      initialState(ME, room),
      msg('PLAYER_JOINED', 1, { userId: OTHER, nickname: 'other', bot: false }),
      msg('COUNTDOWN', 2, { roundNo: 1, totalRounds: 3, startsAt: 5_000 }),
      msg('ROUND_STARTED', 3, { roundId: 'r1', roundNo: 1, totalRounds: 3, endsAt: 95_000 }),
      { type: 'guessResult', result: { guessId: 'g1', roundId: 'r1', word: 'CRANE', feedback: 'BYBBG', attempt: 1, outcome: 'WRONG', points: 0, msBehind: 0 } },
      msg('PLAYER_PROGRESS', 4, { userId: ME, attempt: 1, feedback: 'BYBBG' }),
      msg('PLAYER_PROGRESS', 5, { userId: OTHER, attempt: 1, feedback: 'GGBBB' }),
      msg('ROUND_ENDED', 6, { roundId: 'r1', roundNo: 1, answer: 'GLOBE', reason: 'SOLVED', winnerId: OTHER, winnerAttempts: 2, points: 180, nextPhaseAt: 100_000 }),
    );

    expect(state.status).toBe('ROUND_RESULTS');
    expect(state.room?.players.map((p) => p.userId)).toEqual([ME, OTHER]);
    expect(state.round?.myGuesses).toEqual([{ word: 'CRANE', feedback: 'BYBBG' }]);
    expect(state.round?.opponents).toEqual({ [OTHER]: ['GGBBB'] }); // own progress not duplicated
    expect(state.round?.result).toMatchObject({ answer: 'GLOBE', winnerId: OTHER });
    expect(state.phaseEndsAt).toBe(100_000);
    expect(state.needsResync).toBe(false);
  });

  it('ignores messages already covered by the snapshot', () => {
    const snapshot: GameSnapshot = { room: { ...room, seq: 7 }, roundNo: 0, phaseEndsAt: 0, round: null, leaderboard: [], serverTime: 2_000 };
    const state = play(initialState(ME), { type: 'snapshot', snapshot, receivedAt: 1_500 }, msg('PLAYER_JOINED', 7, { userId: OTHER, nickname: 'other', bot: false }));

    expect(state.room?.players).toHaveLength(1);
    expect(state.clockOffset).toBe(500);
  });

  it('flags a resync when a sequence number is skipped', () => {
    const state = play(initialState(ME, room), msg('PLAYER_JOINED', 3, { userId: OTHER, nickname: 'other', bot: false }));

    expect(state.needsResync).toBe(true);
  });

  it('does not add a replayed guess twice', () => {
    const result = { guessId: 'g1', roundId: 'r1', word: 'CRANE', feedback: 'BBBBB', attempt: 1, outcome: 'WRONG' as const, points: 0, msBehind: 0 };
    const state = play(
      initialState(ME, room),
      msg('ROUND_STARTED', 1, { roundId: 'r1', roundNo: 1, totalRounds: 3, endsAt: 9 }),
      { type: 'guessResult', result },
      { type: 'guessResult', result },
    );

    expect(state.round?.myGuesses).toHaveLength(1);
  });

  it('moves the host flag', () => {
    const state = play(
      initialState(ME, room),
      msg('PLAYER_JOINED', 1, { userId: OTHER, nickname: 'other', bot: false }),
      msg('HOST_CHANGED', 2, { hostId: OTHER }),
    );

    expect(state.room?.players.find((p) => p.host)?.userId).toBe(OTHER);
  });
});

describe('letterStates', () => {
  it('keeps the best colour seen for each letter', () => {
    expect(letterStates([
      { word: 'CRANE', feedback: 'BYBBB' },
      { word: 'ROUND', feedback: 'GBBBB' },
    ])).toMatchObject({ C: 'B', R: 'G', A: 'B', O: 'B' });
  });
});
