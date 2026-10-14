import type { AuthResponse, RoomSettings, RoomSnapshot, User } from './types';

/** Empty in dev (Vite proxies /api); the backend's origin in production. */
export const API_URL: string = import.meta.env.VITE_API_URL ?? '';

/** The backend's RFC 9457 error body, reduced to what the UI needs. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit & { token?: string } = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set('Content-Type', 'application/json');
  if (init.token) headers.set('Authorization', `Bearer ${init.token}`);

  const response = await fetch(`${API_URL}${path}`, { ...init, headers });
  if (response.status === 204) return undefined as T;
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(response.status, body.code ?? 'ERROR', body.detail ?? `Request failed (${response.status})`);
  }
  return body as T;
}

export const api = {
  signIn: (nickname: string) =>
    request<AuthResponse>('/api/v1/auth/guest', { method: 'POST', body: JSON.stringify({ nickname }) }),

  me: (token: string) => request<User>('/api/v1/me', { token }),

  currentRoom: (token: string) => request<RoomSnapshot | undefined>('/api/v1/me/room', { token }),

  createRoom: (token: string, settings: Partial<RoomSettings>) =>
    request<RoomSnapshot>('/api/v1/rooms', { method: 'POST', token, body: JSON.stringify(settings) }),

  joinRoom: (token: string, code: string) =>
    request<RoomSnapshot>(`/api/v1/rooms/${encodeURIComponent(code)}/join`, { method: 'POST', token }),

  leaveRoom: (token: string, code: string) =>
    request<void>(`/api/v1/rooms/${encodeURIComponent(code)}/leave`, { method: 'POST', token }),

  kick: (token: string, code: string, userId: string) =>
    request<void>(`/api/v1/rooms/${encodeURIComponent(code)}/players/${userId}`, { method: 'DELETE', token }),
};
