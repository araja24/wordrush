import type {
  GameSnapshot,
  GuessResult,
  GuessView,
  RoomMessage,
  RoomSnapshot,
  RoomStatus,
  RoundEndReason,
  RoundResult,
  Standing,
} from '../api/types';

export interface Round {
  roundId: string;
  roundNo: number;
  endsAt: number;
  myGuesses: GuessView[];
  /** Opponents' colour rows by user id. Never letters. */
  opponents: Record<string, string[]>;
  result: RoundResult | null;
}

/** One finished round, for the "words this game" list on the final screen. */
export interface RoundRecord {
  roundNo: number;
  answer: string;
  reason: RoundEndReason;
  winnerId: string | null;
}

export interface GameState {
  me: string;
  room: RoomSnapshot | null;
  status: RoomStatus;
  roundNo: number;
  /** When the current countdown / round / results pause ends, in server time. */
  phaseEndsAt: number;
  /** When the current phase began, in server time (drives the draining progress bars). */
  phaseStartedAt: number;
  round: Round | null;
  leaderboard: Standing[];
  /** Rounds finished so far this game. Kept in memory only, so a refresh starts it afresh. */
  history: RoundRecord[];
  /** Last applied room message sequence number. */
  seq: number;
  /** serverTime - local time; add to Date.now() to get server time. */
  clockOffset: number;
  /** Set when a sequence gap shows we missed a message; the socket then re-fetches the snapshot. */
  needsResync: boolean;
}

export type Action =
  | { type: 'snapshot'; snapshot: GameSnapshot; receivedAt: number }
  | { type: 'message'; message: RoomMessage; receivedAt: number }
  | { type: 'guessResult'; result: GuessResult };

export function initialState(me: string, room: RoomSnapshot | null = null): GameState {
  return {
    me,
    room,
    status: room?.status ?? 'LOBBY',
    roundNo: 0,
    phaseEndsAt: 0,
    phaseStartedAt: 0,
    round: null,
    leaderboard: [],
    history: [],
    seq: room?.seq ?? 0,
    clockOffset: 0,
    needsResync: false,
  };
}

export function reduce(state: GameState, action: Action): GameState {
  switch (action.type) {
    case 'snapshot':
      return fromSnapshot(state, action.snapshot, action.receivedAt);
    case 'message':
      return applyMessage(state, action.message, action.receivedAt);
    case 'guessResult':
      return applyGuessResult(state, action.result);
  }
}

function fromSnapshot(previous: GameState, s: GameSnapshot, receivedAt: number): GameState {
  const sameRoom = previous.room?.code === s.room.code;
  return {
    me: previous.me,
    room: s.room,
    status: s.room.status,
    roundNo: s.roundNo,
    phaseEndsAt: s.phaseEndsAt,
    phaseStartedAt: sameRoom && previous.phaseStartedAt ? previous.phaseStartedAt : s.serverTime,
    round: s.round && {
      roundId: s.round.roundId,
      roundNo: s.round.roundNo,
      endsAt: s.round.endsAt,
      myGuesses: s.round.yourGuesses,
      opponents: s.round.opponentFeedback,
      result: s.round.result,
    },
    leaderboard: s.leaderboard,
    history: sameRoom ? previous.history : [],
    seq: s.room.seq,
    clockOffset: s.serverTime - receivedAt,
    needsResync: false,
  };
}

function applyMessage(state: GameState, message: RoomMessage, receivedAt: number): GameState {
  if (message.seq <= state.seq) return state; // already reflected in our snapshot
  const next: GameState = {
    ...state,
    seq: message.seq,
    clockOffset: message.serverTime - receivedAt,
    needsResync: state.needsResync || message.seq > state.seq + 1,
  };

  switch (message.type) {
    case 'PLAYER_JOINED': {
      const { userId, nickname, bot } = message.payload;
      if (!next.room || next.room.players.some((p) => p.userId === userId)) return next;
      const player = { userId, nickname, bot, connected: true, host: false };
      return { ...next, room: { ...next.room, players: [...next.room.players, player] } };
    }
    case 'PLAYER_LEFT': {
      if (!next.room) return next;
      const players = next.room.players.filter((p) => p.userId !== message.payload.userId);
      return { ...next, room: { ...next.room, players } };
    }
    case 'HOST_CHANGED': {
      if (!next.room) return next;
      const { hostId } = message.payload;
      const players = next.room.players.map((p) => ({ ...p, host: p.userId === hostId }));
      return { ...next, room: { ...next.room, hostId, players } };
    }
    case 'COUNTDOWN': {
      const { roundNo, startsAt } = message.payload;
      const history = roundNo === 1 ? [] : next.history; // round 1 means a new game
      return withStatus(
        { ...next, roundNo, phaseEndsAt: startsAt, phaseStartedAt: message.serverTime, round: null, history },
        'COUNTDOWN',
      );
    }
    case 'ROUND_STARTED': {
      const { roundId, roundNo, endsAt } = message.payload;
      const round: Round = { roundId, roundNo, endsAt, myGuesses: [], opponents: {}, result: null };
      return withStatus({ ...next, roundNo, phaseEndsAt: endsAt, phaseStartedAt: message.serverTime, round }, 'IN_ROUND');
    }
    case 'PLAYER_PROGRESS': {
      const { userId, attempt, feedback } = message.payload;
      if (userId === state.me || !next.round) return next; // our own rows come from guess results
      const rows = next.round.opponents[userId] ?? [];
      if (rows.length >= attempt) return next;
      return { ...next, round: { ...next.round, opponents: { ...next.round.opponents, [userId]: [...rows, feedback] } } };
    }
    case 'ROUND_ENDED': {
      const { roundNo, answer, reason, winnerId, winnerAttempts, solveMillis, nextPhaseAt } = message.payload;
      const round = next.round && { ...next.round, result: { answer, reason, winnerId, winnerAttempts, solveMillis } };
      const history = next.history.some((r) => r.roundNo === roundNo)
        ? next.history
        : [...next.history, { roundNo, answer, reason, winnerId }];
      return withStatus(
        { ...next, round, history, phaseEndsAt: nextPhaseAt, phaseStartedAt: message.serverTime },
        'ROUND_RESULTS',
      );
    }
    case 'LEADERBOARD':
      return { ...next, leaderboard: message.payload.standings };
    case 'GAME_OVER':
      return withStatus({ ...next, leaderboard: message.payload.standings, phaseEndsAt: 0 }, 'GAME_OVER');
    case 'RETURNED_TO_LOBBY':
      return withStatus({ ...next, round: null, roundNo: 0, phaseEndsAt: 0, history: [] }, 'LOBBY');
  }
}

function applyGuessResult(state: GameState, result: GuessResult): GameState {
  const counted = result.outcome === 'WRONG' || result.outcome === 'WON';
  const round = state.round;
  if (!counted || !round || round.roundId !== result.roundId || !result.feedback) return state;
  if (round.myGuesses.length >= result.attempt) return state; // duplicate (e.g. replayed guess)
  const myGuesses = [...round.myGuesses, { word: result.word, feedback: result.feedback }];
  return { ...state, round: { ...round, myGuesses } };
}

function withStatus(state: GameState, status: RoomStatus): GameState {
  return { ...state, status, room: state.room && { ...state.room, status } };
}

/** Best known colour per letter, for the on-screen keyboard: G beats Y beats B. */
export function letterStates(guesses: GuessView[]): Record<string, 'G' | 'Y' | 'B'> {
  const rank = { B: 0, Y: 1, G: 2 } as const;
  const states: Record<string, 'G' | 'Y' | 'B'> = {};
  for (const { word, feedback } of guesses) {
    [...word].forEach((letter, i) => {
      const tile = feedback[i] as 'G' | 'Y' | 'B';
      const current = states[letter];
      if (current === undefined || rank[tile] > rank[current]) states[letter] = tile;
    });
  }
  return states;
}

/** Most greens in any one row: how close a player got. */
export function bestGreens(rows: string[]): number {
  return rows.reduce((best, row) => Math.max(best, [...row].filter((t) => t === 'G').length), 0);
}

/** Bots are named like "Ada (hard)": split into the display name and the difficulty. */
export function botName(nickname: string): { name: string; level: string | null } {
  const match = /^(.*) \((easy|medium|hard)\)$/.exec(nickname);
  return match ? { name: match[1], level: match[2] } : { name: nickname, level: null };
}
