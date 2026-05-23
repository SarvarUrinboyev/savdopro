// Thin fetch wrapper around the backend REST API.
// In dev, Vite proxies /api to :8086; in production the JAR serves both.

const BASE = '/api';
const TOKEN_KEY = 'savdopro.token';
const ACTIVE_SHOP_KEY = 'savdopro.activeShopId';

let onUnauthorized = null;

/** Register a callback that fires on a 401/403, used to bounce to /login. */
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

async function request(method, path, body) {
  const options = { method, headers: {} };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  const token = getToken();
  if (token) {
    options.headers.Authorization = `Bearer ${token}`;
  }
  // Tenant scoping: the active shop id is sent on every call so the
  // backend (Phase 1C-2) can filter rows by shop when needed. Only send
  // it when there's an active session — otherwise a stale shop id from
  // a previous install would tag /api/auth/login with a tenant header
  // the backend can't validate yet, confusing the customer with a 403.
  if (token) {
    const shopId = localStorage.getItem(ACTIVE_SHOP_KEY);
    if (shopId) {
      options.headers['X-Shop-Id'] = shopId;
    }
  }

  let response;
  try {
    response = await fetch(BASE + path, options);
  } catch {
    throw new ApiError("Serverga ulanib bo'lmadi. Backend ishlayaptimi?", 0);
  }

  if (response.status === 401 || response.status === 403) {
    // Token rejected — clear all session state and notify the app so it
    // can drop the user back on the login screen. We clear shopId too so
    // a stale X-Shop-Id from a deleted shop doesn't keep tripping the
    // backend on the next request.
    setToken(null);
    localStorage.removeItem(ACTIVE_SHOP_KEY);
    if (onUnauthorized) onUnauthorized(response.status);
  }

  if (response.status === 204) {
    return null;
  }

  const text = await response.text();
  const data = text ? safeParse(text) : null;

  if (!response.ok) {
    const message = data?.message || data?.detail || data?.title
      || `Xatolik yuz berdi (${response.status})`;
    throw new ApiError(message, response.status, data?.fieldErrors);
  }
  return data;
}

function safeParse(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

export class ApiError extends Error {
  constructor(message, status, fieldErrors) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors || null;
  }
}

export const api = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body ?? {}),
  put: (path, body) => request('PUT', path, body ?? {}),
  patch: (path, body) => request('PATCH', path, body ?? {}),
  del: (path) => request('DELETE', path),
};
