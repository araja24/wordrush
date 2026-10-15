// Mirrors the backend DTOs. Times are epoch milliseconds unless noted.

export type RoomStatus = 'LOBBY' | 'COUNTDOWN' | 'IN_ROUND' | 'ROUND_RESULTS' | 'GAME_OVER';
export type RoundEndReason = 'SOLVED' | 'TIMEOUT' | 'ALL_FAILED';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type GuessOutcome = 'WRONG' | 'WON' | 'CORRECT_LATE' | 'ROUND_OVER' | 'OUT_OF_GUESSES' | 'NOT_A_WORD';

export interface User {
  id: string;
  username: string;
  nickname: string;
  guest: boolean;
  bot: boolean;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string; // ISO-8601
  user: User;
}

export interface RoomSettings {
  totalRounds: number;
  roundSeconds: number;
  maxPlayers: number;
}

export interface PlayerView {
  userId: string;
  nickname: string;
  bot: boolean;
  connected: boolean;
  host: boolean;
}

export interface RoomSnapshot {
  code: string;
  status: RoomStatus;
  hostId: string;
  settings: RoomSettings;
  players: PlayerView[];
  createdAt: string;
  seq: number;
}

export interface Standing {
  userId: string;
  nickname: string;
  score: number;
  rank: number;
}

export interface GuessView {
  word: string;
  feedback: string; // e.g. "GYBBG"
}

export interface RoundResult {
  answer: string;
  reason: RoundEndReason;
  winnerId: string | null;
  winnerAttempts: number | null;
  /** Round start to the winning guess, in ms; null if nobody solved it. */
  solveMillis: number | null;
}

export interface GameSnapshot {
  room: RoomSnapshot;
  roundNo: number;
  phaseEndsAt: number;
  round: {
    roundId: string;
    roundNo: number;
    endsAt: number;
    yourGuesses: GuessView[];
    opponentFeedback: Record<string, string[]>;
    result: RoundResult | null;
  } | null;
  leaderboard: Standing[];
  serverTime: number;
}

export interface GuessResult {
  guessId: string;
  roundId: string;
  word: string;
  feedback: string | null;
  attempt: number;
  outcome: GuessOutcome;
  points: number;
  msBehind: number;
}

/** Envelope for everything on /topic/rooms/{code}. */
export type RoomMessage =
  | Envelope<'PLAYER_JOINED', { userId: string; nickname: string; bot: boolean }>
  | Envelope<'PLAYER_LEFT', { userId: string; kicked: boolean }>
  | Envelope<'HOST_CHANGED', { hostId: string }>
  | Envelope<'COUNTDOWN', { roundNo: number; totalRounds: number; startsAt: number }>
  | Envelope<'ROUND_STARTED', { roundId: string; roundNo: number; totalRounds: number; endsAt: number }>
  | Envelope<'PLAYER_PROGRESS', { userId: string; attempt: number; feedback: string }>
  | Envelope<'ROUND_ENDED', {
      roundId: string;
      roundNo: number;
      answer: string;
      reason: RoundEndReason;
      winnerId: string | null;
      winnerAttempts: number | null;
      points: number;
      solveMillis: number | null;
      nextPhaseAt: number;
    }>
  | Envelope<'LEADERBOARD', { standings: Standing[] }>
  | Envelope<'GAME_OVER', { standings: Standing[] }>
  | Envelope<'RETURNED_TO_LOBBY', Record<string, never>>
  | Envelope<'CHAT', { userId: string; nickname: string; text: string; reaction: boolean }>;

interface Envelope<T extends string, P> {
  type: T;
  roomCode: string;
  seq: number;
  serverTime: number;
  payload: P;
}

export interface ErrorMessage {
  code: string;
  message: string;
}
