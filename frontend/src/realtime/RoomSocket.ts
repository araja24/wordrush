import { Client, type IMessage } from '@stomp/stompjs';
import { API_URL } from '../api/http';
import type { ErrorMessage, GameSnapshot, GuessResult, RoomMessage } from '../api/types';

export interface RoomSocketHandlers {
  onMessage(message: RoomMessage): void;
  onSnapshot(snapshot: GameSnapshot): void;
  onGuessResult(result: GuessResult): void;
  onError(error: ErrorMessage): void;
  onKicked(): void;
  onConnectionChange(connected: boolean): void;
}

function socketUrl(): string {
  if (API_URL) return API_URL.replace(/^http/, 'ws') + '/ws';
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${window.location.host}/ws`;
}

/**
 * One STOMP connection for one room. On every (re)connect it subscribes to the room topic first
 * and only then asks for a snapshot, so no event can fall in the gap between the two; the reducer
 * drops events the snapshot already covers.
 */
export class RoomSocket {
  private readonly client: Client;

  constructor(
    token: string,
    private readonly code: string,
    private readonly handlers: RoomSocketHandlers,
  ) {
    this.client = new Client({
      brokerURL: socketUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      reconnectDelay: 1_000,
      onConnect: () => this.subscribeAll(),
      onWebSocketClose: () => handlers.onConnectionChange(false),
      onStompError: (frame) => handlers.onError({ code: 'CONNECTION', message: frame.headers.message ?? 'Connection error' }),
    });
  }

  connect(): void {
    this.client.activate();
  }

  disconnect(): void {
    void this.client.deactivate();
  }

  /** Re-fetches the full state, e.g. after a sequence gap. */
  resync(): void {
    if (!this.client.connected) return;
    const subscription = this.client.subscribe(`/app/rooms/${this.code}/state`, (frame) => {
      this.handlers.onSnapshot(parse<GameSnapshot>(frame));
      subscription.unsubscribe();
    });
  }

  start(): void {
    this.send('start', {});
  }

  playAgain(): void {
    this.send('playAgain', {});
  }

  /** Returns the guessId; a retry must reuse it so the server counts the guess once. */
  guess(roundId: string, word: string): string {
    const guessId = crypto.randomUUID();
    this.send('guess', { guessId, roundId, word });
    return guessId;
  }

  private subscribeAll(): void {
    const h = this.handlers;
    this.client.subscribe(`/topic/rooms/${this.code}`, (frame) => h.onMessage(parse<RoomMessage>(frame)));
    this.client.subscribe('/user/queue/guess-result', (frame) => h.onGuessResult(parse<GuessResult>(frame)));
    this.client.subscribe('/user/queue/errors', (frame) => h.onError(parse<ErrorMessage>(frame)));
    this.client.subscribe('/user/queue/kicked', () => h.onKicked());
    h.onConnectionChange(true);
    this.resync();
  }

  private send(command: string, body: object): void {
    this.client.publish({ destination: `/app/rooms/${this.code}/${command}`, body: JSON.stringify(body) });
  }
}

function parse<T>(frame: IMessage): T {
  return JSON.parse(frame.body) as T;
}
