import { describe, expect, it } from 'vitest';
import type { GameSnapshot, RoomMessage, RoomSnapshot } from '../api/types';
import { CHAT_LIMIT, bestGreens, botName, initialState, letterStates, reduce, type GameState } from './reducer';

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
      msg('ROUND_ENDED', 6, { roundId: 'r1', roundNo: 1, answer: 'GLOBE', reason: 'SOLVED', winnerId: OTHER, winnerAttempts: 2, points: 180, solveMillis: 41_250, nextPhaseAt: 100_000 }),
    );

    expect(state.status).toBe('ROUND_RESULTS');
    expect(state.room?.players.map((p) => p.userId)).toEqual([ME, OTHER]);
    expect(state.round?.myGuesses).toEqual([{ word: 'CRANE', feedback: 'BYBBG' }]);
    expect(state.round?.opponents).toEqual({ [OTHER]: ['GGBBB'] }); // own progress not duplicated
    expect(state.round?.result).toMatchObject({ answer: 'GLOBE', winnerId: OTHER, solveMillis: 41_250 });
    expect(state.history).toEqual([{ roundNo: 1, answer: 'GLOBE', reason: 'SOLVED', winnerId: OTHER }]);
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

describe('helpers', () => {
  it('counts the most greens in any row', () => {
    expect(bestGreens(['GBBBB', 'GGGYB', 'BBBBB'])).toBe(3);
    expect(bestGreens([])).toBe(0);
  });

  it('splits bot nicknames', () => {
    expect(botName('Ada (hard)')).toEqual({ name: 'Ada', level: 'hard' });
    expect(botName('maya')).toEqual({ name: 'maya', level: null });
  });

  it('starts a fresh word history with each game', () => {
    const state = play(
      initialState(ME, room),
      msg('COUNTDOWN', 1, { roundNo: 1, totalRounds: 2, startsAt: 1 }),
      msg('ROUND_STARTED', 2, { roundId: 'r1', roundNo: 1, totalRounds: 2, endsAt: 9 }),
      msg('ROUND_ENDED', 3, { roundId: 'r1', roundNo: 1, answer: 'PLANT', reason: 'TIMEOUT', winnerId: null, winnerAttempts: null, points: 0, solveMillis: null, nextPhaseAt: 9 }),
      msg('COUNTDOWN', 4, { roundNo: 1, totalRounds: 2, startsAt: 1 }),
    );
    expect(state.history).toEqual([]);
  });
});

describe('chat', () => {
  it('interleaves messages with lines narrating the game', () => {
    const state = play(
      initialState(ME, room),
      msg('PLAYER_JOINED', 1, { userId: OTHER, nickname: 'maya', bot: false }),
      msg('PLAYER_JOINED', 2, { userId: 'bot', nickname: 'Ada (hard)', bot: true }),
      msg('CHAT', 3, { userId: OTHER, nickname: 'maya', text: 'who added a hard bot', reaction: false }),
      msg('COUNTDOWN', 4, { roundNo: 1, totalRounds: 3, startsAt: 5_000 }),
      msg('ROUND_STARTED', 5, { roundId: 'r1', roundNo: 1, totalRounds: 3, endsAt: 95_000 }),
      msg('CHAT', 6, { userId: ME, nickname: 'me', text: 'so close', reaction: true }),
      msg('ROUND_ENDED', 7, { roundId: 'r1', roundNo: 1, answer: 'BRISK', reason: 'SOLVED', winnerId: OTHER, winnerAttempts: 4, points: 180, solveMillis: 51_962, nextPhaseAt: 100_000 }),
      msg('PLAYER_LEFT', 8, { userId: 'bot', kicked: true }),
    );

    expect(state.chat.map((l) => [l.id, l.kind, l.text])).toEqual([
      [1, 'system', 'maya joined'],
      [2, 'system', 'Ada (bot) joined'],
      [3, 'message', 'who added a hard bot'],
      [4, 'system', 'Round 1 starting'],
      [6, 'reaction', 'so close'],
      [7, 'system', 'maya won round 1 · BRISK'],
      [8, 'system', 'Ada was removed'],
    ]);
    expect(state.chat[2]).toMatchObject({ userId: OTHER, name: 'maya' });
  });

  it('ignores replayed messages and survives a resync in the same room', () => {
    const chatted = play(
      initialState(ME, room),
      msg('CHAT', 1, { userId: OTHER, nickname: 'maya', text: 'gg', reaction: false }),
      msg('CHAT', 1, { userId: OTHER, nickname: 'maya', text: 'gg', reaction: false }),
    );
    expect(chatted.chat).toHaveLength(1);

    const snapshot: GameSnapshot = { room: { ...room, seq: 5 }, roundNo: 0, phaseEndsAt: 0, round: null, leaderboard: [], serverTime: 1_000 };
    expect(reduce(chatted, { type: 'snapshot', snapshot, receivedAt: 1_000 }).chat).toHaveLength(1);
  });

  it('keeps only the latest lines', () => {
    const many = Array.from({ length: CHAT_LIMIT + 5 }, (_, i) =>
      msg('CHAT', i + 1, { userId: OTHER, nickname: 'maya', text: `line ${i + 1}`, reaction: false }));
    const state = play(initialState(ME, room), ...many);
    expect(state.chat).toHaveLength(CHAT_LIMIT);
    expect(state.chat[0].text).toBe('line 6');
  });
});
