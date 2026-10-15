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

/** One line in the room chat. System lines narrate the game ("maya joined", "Round 2 starting"). */
export interface ChatLine {
  /** The room message's seq: unique and increasing, so it doubles as a read marker. */
  id: number;
  kind: 'system' | 'message' | 'reaction';
  text: string;
  /** Author, for messages and reactions. */
  userId?: string;
  name?: string;
}

/** Older lines drop off the top; chat is for the moment, not a transcript. */
export const CHAT_LIMIT = 100;

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
  /** Room chat plus system lines, oldest first. In memory only, like history. */
  chat: ChatLine[];
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
    chat: [],
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
    chat: sameRoom ? previous.chat : [],
    seq: s.room.seq,
    clockOffset: s.serverTime - receivedAt,
    needsResync: false,
  };
}

function applyMessage(state: GameState, message: RoomMessage, receivedAt: number): GameState {
  if (message.seq <= state.seq) return state; // already reflected in our snapshot
  const line = chatLine(state, message);
  const next: GameState = {
    ...state,
    seq: message.seq,
    clockOffset: message.serverTime - receivedAt,
    needsResync: state.needsResync || message.seq > state.seq + 1,
    chat: line ? [...state.chat, line].slice(-CHAT_LIMIT) : state.chat,
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
    case 'CHAT':
      return next; // the line was added above
  }
}

/** What, if anything, a room message adds to the chat. Names are looked up before the event applies. */
function chatLine(state: GameState, message: RoomMessage): ChatLine | null {
  const id = message.seq;
  const system = (text: string): ChatLine => ({ id, kind: 'system', text });
  const nameOf = (userId: string | null) => {
    const player = state.room?.players.find((p) => p.userId === userId);
    return player ? (player.bot ? botName(player.nickname).name : player.nickname) : 'Someone';
  };
  switch (message.type) {
    case 'CHAT': {
      const { userId, nickname, text, reaction } = message.payload;
      return { id, kind: reaction ? 'reaction' : 'message', text, userId, name: botName(nickname).name };
    }
    case 'PLAYER_JOINED': {
      const { nickname, bot } = message.payload;
      return system(bot ? `${botName(nickname).name} (bot) joined` : `${nickname} joined`);
    }
    case 'PLAYER_LEFT': {
      const { userId, kicked } = message.payload;
      if (userId === state.me) return null;
      return system(`${nameOf(userId)} ${kicked ? 'was removed' : 'left'}`);
    }
    case 'COUNTDOWN':
      return system(`Round ${message.payload.roundNo} starting`);
    case 'ROUND_ENDED': {
      const { roundNo, answer, reason, winnerId } = message.payload;
      const who = reason === 'SOLVED' ? `${winnerId === state.me ? 'You' : nameOf(winnerId)} won round ${roundNo}` : `Nobody got round ${roundNo}`;
      return system(`${who} · ${answer}`);
    }
    case 'GAME_OVER': {
      const top = message.payload.standings.filter((s) => s.rank === 1);
      if (top.length !== 1) return system('Game over: it’s a tie');
      return system(top[0].userId === state.me ? 'You win the game' : `${nameOf(top[0].userId)} wins the game`);
    }
    default:
      return null;
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
