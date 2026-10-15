import { create } from 'zustand';
import { ApiError, api } from '../api/http';
import type { AuthResponse, Difficulty, GuessResult, RoomSettings, RoomSnapshot } from '../api/types';
import { RoomSocket } from '../realtime/RoomSocket';
import { initialState, reduce, type Action, type GameState } from './reducer';

/**
 * Auth lives in sessionStorage, not localStorage: each browser tab is its own player (handy for
 * testing against yourself), and a refresh keeps you signed in and in your room.
 */
const AUTH_KEY = 'wordrush.auth';

function loadAuth(): AuthResponse | null {
  try {
    const raw = sessionStorage.getItem(AUTH_KEY);
    const auth = raw ? (JSON.parse(raw) as AuthResponse) : null;
    return auth && new Date(auth.expiresAt).getTime() > Date.now() ? auth : null;
  } catch {
    return null;
  }
}

function saveAuth(auth: AuthResponse | null): void {
  try {
    if (auth) sessionStorage.setItem(AUTH_KEY, JSON.stringify(auth));
    else sessionStorage.removeItem(AUTH_KEY);
  } catch {
    // storage unavailable (private mode); the session just won't survive a refresh
  }
}

export interface Toast {
  id: number;
  text: string;
}

interface AppState {
  auth: AuthResponse | null;
  game: GameState | null;
  connected: boolean;
  resuming: boolean;
  toast: Toast | null;
  /** Set when the host removed us; shows the "removed" screen until dismissed. */
  removedFrom: string | null;
  helpOpen: boolean;
  /** The latest private guess result, so the board can react (shake, clear input). */
  lastResult: GuessResult | null;
  /** Whether the floating chat panel (or phone sheet) is open. The lobby's docked chat is always open. */
  chatOpen: boolean;
  /** Id of the newest chat line the player has had on screen; later lines count as unread. */
  chatReadId: number;

  /** Resolves true once signed in. */
  signIn(nickname: string): Promise<boolean>;
  signOut(): Promise<void>;
  resume(): Promise<void>;
  createRoom(settings: Partial<RoomSettings>): Promise<void>;
  joinRoom(code: string): Promise<void>;
  leaveRoom(): Promise<void>;
  kick(userId: string): Promise<void>;
  addBot(difficulty: Difficulty): Promise<void>;
  startGame(): void;
  guess(word: string): void;
  playAgain(): void;
  sendChat(text: string, reaction: boolean): void;
  setChatOpen(open: boolean): void;
  markChatRead(id: number): void;
  showToast(text: string): void;
  clearToast(): void;
  dismissRemoved(): void;
  setHelpOpen(open: boolean): void;
}

let socket: RoomSocket | null = null;
let toastId = 0;

/** The server's answer when the room expired (2 h without activity) or we're no longer in it. */
const ROOM_GONE = new Set(['ROOM_NOT_FOUND', 'NOT_A_MEMBER']);
const ROOM_GONE_MESSAGE = 'That room has closed or expired. Start a new one or join another.';

export const useApp = create<AppState>((set, get) => {
  const dispatch = (action: Action) => {
    const game = get().game;
    if (!game) return;
    const next = reduce(game, action);
    set({ game: next });
    if (next.needsResync && !game.needsResync) socket?.resync();
  };

  const openRoom = (auth: AuthResponse, room: RoomSnapshot) => {
    socket?.disconnect();
    set({ game: initialState(auth.user.id, room), lastResult: null, chatOpen: false, chatReadId: 0 });
    socket = new RoomSocket(auth.accessToken, room.code, {
      onMessage: (message) => dispatch({ type: 'message', message, receivedAt: Date.now() }),
      onSnapshot: (snapshot) => dispatch({ type: 'snapshot', snapshot, receivedAt: Date.now() }),
      onGuessResult: (result) => {
        set({ lastResult: result });
        dispatch({ type: 'guessResult', result });
      },
      onError: (error) => {
        if (ROOM_GONE.has(error.code)) {
          closeRoom();
          get().showToast(ROOM_GONE_MESSAGE);
        } else {
          get().showToast(error.message);
        }
      },
      onKicked: () => {
        closeRoom();
        set({ removedFrom: room.code });
      },
      onConnectionChange: (connected) => set({ connected }),
    });
    socket.connect();
  };

  const closeRoom = () => {
    socket?.disconnect();
    socket = null;
    set({ game: null, connected: false, lastResult: null, chatOpen: false, chatReadId: 0 });
  };

  /** Runs an API call; an expired session sends you back to sign-in, other errors become a toast. */
  const guarded = async (work: (auth: AuthResponse) => Promise<void>) => {
    const auth = get().auth;
    if (!auth) return;
    try {
      await work(auth);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        closeRoom();
        saveAuth(null);
        set({ auth: null });
        get().showToast('Your session expired. Pick a nickname to play again.');
      } else if (error instanceof ApiError && ROOM_GONE.has(error.code) && get().game) {
        closeRoom();
        get().showToast(ROOM_GONE_MESSAGE);
      } else {
        get().showToast(error instanceof Error ? error.message : 'Something went wrong.');
      }
    }
  };

  return {
    auth: loadAuth(),
    game: null,
    connected: false,
    resuming: false,
    toast: null,
    removedFrom: null,
    helpOpen: false,
    lastResult: null,
    chatOpen: false,
    chatReadId: 0,

    async signIn(nickname) {
      try {
        const auth = await api.signIn(nickname);
        saveAuth(auth);
        set({ auth });
        return true;
      } catch (error) {
        get().showToast(error instanceof Error ? error.message : 'Could not sign in.');
        return false;
      }
    },

    async signOut() {
      await get().leaveRoom();
      saveAuth(null);
      set({ auth: null });
    },

    /** After a refresh: rejoin the room we were in, if it still exists. */
    async resume() {
      set({ resuming: true });
      await guarded(async (auth) => {
        const room = await api.currentRoom(auth.accessToken);
        if (room) openRoom(auth, room);
      });
      set({ resuming: false });
    },

    createRoom: (settings) => guarded(async (auth) => openRoom(auth, await api.createRoom(auth.accessToken, settings))),

    joinRoom: (code) => guarded(async (auth) => openRoom(auth, await api.joinRoom(auth.accessToken, code.trim()))),

    leaveRoom: () =>
      guarded(async (auth) => {
        const code = get().game?.room?.code;
        closeRoom();
        if (code) await api.leaveRoom(auth.accessToken, code);
      }),

    kick: (userId) =>
      guarded(async (auth) => {
        const code = get().game?.room?.code;
        if (code) await api.kick(auth.accessToken, code, userId);
      }),

    // The new player arrives through the PLAYER_JOINED event, like anyone else.
    addBot: (difficulty) =>
      guarded(async (auth) => {
        const code = get().game?.room?.code;
        if (code) await api.addBot(auth.accessToken, code, difficulty);
      }),

    startGame: () => socket?.start(),

    guess(word) {
      const roundId = get().game?.round?.roundId;
      if (roundId) socket?.guess(roundId, word);
    },

    playAgain: () => socket?.playAgain(),

    sendChat: (text, reaction) => socket?.chat(text, reaction),

    setChatOpen: (open) => set({ chatOpen: open }),

    markChatRead: (id) => {
      if (id > get().chatReadId) set({ chatReadId: id });
    },

    clearToast: () => set({ toast: null }),

    dismissRemoved: () => set({ removedFrom: null }),

    setHelpOpen: (open) => set({ helpOpen: open }),

    showToast(text) {
      const toast = { id: ++toastId, text };
      set({ toast });
      setTimeout(() => {
        if (get().toast?.id === toast.id) set({ toast: null });
      }, 3500);
    },
  };
});
