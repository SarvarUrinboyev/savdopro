// Thin fetch wrapper around the backend REST API.
// In dev, Vite proxies /api to :8086; in production the JAR serves both.

import {
  getLicenseUrl,
  getRefreshToken,
  setRefreshToken,
  clearAuthPair,
} from './licenseClient.js';
import { API_ORIGIN } from '../config.js';

// '' (same origin) for the desktop / single-origin web deploy; an absolute
// origin when VITE_API_URL points the SPA at a separate API host.
const BASE = `${API_ORIGIN}/api`;
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

// In-flight refresh promise; null when idle. Concurrent 401s coalesce here
// so only one /api/auth/refresh round-trip is ever in flight at a time.
let refreshInFlight = null;

async function refreshLocalToken() {
  if (refreshInFlight) return refreshInFlight;
  const stored = getRefreshToken();
  if (!stored) return null;
  const base = getLicenseUrl().replace(/\/+$/, '');
  refreshInFlight = (async () => {
    try {
      const res = await fetch(`${base}/api/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: stored }),
      });
      if (!res.ok) return null;
      const data = await res.json();
      if (data.token) setToken(data.token);
      if (data.refreshToken) setRefreshToken(data.refreshToken);
      return data.token || null;
    } catch (_) {
      return null;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
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

  if (response.status === 401) {
    // Access token expired — attempt a silent refresh and replay once.
    const fresh = await refreshLocalToken();
    if (fresh) {
      // Rebuild options with the new token and retry.
      options.headers.Authorization = `Bearer ${fresh}`;
      try {
        response = await fetch(BASE + path, options);
      } catch {
        throw new ApiError("Serverga ulanib bo'lmadi. Backend ishlayaptimi?", 0);
      }
      // If the retry is also rejected, fall through to the clearAuthPair path below.
    }

    if (response.status === 401) {
      // Refresh failed or server still rejects — clear state and surface error.
      clearAuthPair();
      localStorage.removeItem(ACTIVE_SHOP_KEY);
      if (onUnauthorized) onUnauthorized(response.status);
    }
  }

  if (response.status === 403) {
    // Distinguish a permission denial from a dead session. The backend tags
    // pure authorization failures with {"code":"FORBIDDEN"}; those must NOT
    // log the user out — a cashier hitting an owner-only action stays signed
    // in and just sees the error. A 403 WITHOUT that code means the account
    // was blocked / the subscription expired → end the session.
    const denial = safeParse(await response.clone().text());
    // Keep the session for a permission denial (FORBIDDEN) AND for a lapsed
    // subscription (SUBSCRIPTION_EXPIRED): the latter is read-only, not a dead
    // session — the write fails with a message and the user heads to /billing
    // to renew. Only a hard block / dead session falls through to logout.
    if (denial?.code === 'FORBIDDEN' || denial?.code === 'SUBSCRIPTION_EXPIRED') {
      throw new ApiError(
        denial.message || "Bu amal uchun ruxsatingiz yo'q",
        403,
        denial.fieldErrors,
      );
    }
    clearAuthPair();
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
