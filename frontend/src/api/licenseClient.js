// HTTP client for the SavdoPRO License Server.
//
// The License Server owns accounts / users / subscriptions centrally
// — it lives on a separate port (9090 in dev) and, in production,
// on the operator's VPS. All other API traffic still goes to the
// local backend at :8086 via the regular `api` client.
//
// The URL is resolved in this order:
//   1. ?licenseUrl=... query parameter (injected by Electron main.js)
//   2. localStorage.savdopro.licenseUrl (set via the in-app settings)
//   3. http://localhost:9090 (dev fallback)
//
// Phase 3.2 refresh flow:
//   - Login stores BOTH an access JWT (~1h TTL) and a refresh token (~7d).
//   - On any 401 the client transparently calls /api/auth/refresh with
//     the stored refresh token, persists the new pair, and retries the
//     original request once. Only if the refresh itself fails do we
//     wipe credentials and surface the auth error.
//   - In-flight refreshes are coalesced via a single promise so 20
//     parallel requests don't trigger 20 refreshes.

import { getToken, setToken } from './client.js';
import { LICENSE_ORIGIN } from '../config.js';

const LICENSE_URL_KEY = 'savdopro.licenseUrl';
const REFRESH_KEY = 'savdopro.refreshToken';
// Local-first: a Windows Scheduled Task launches the license server on
// user logon (port 9090). The desktop hits localhost so login keeps
// working when the VPS is unreachable. The remote VPS is the off-site
// fallback when localhost is down (e.g. service didn't start yet).
const LOCAL_URL = 'http://127.0.0.1:9090';
const VPS_URL = 'https://167-172-164-214.nip.io';
const DEFAULT_URL = LOCAL_URL;

function urlFromQuery() {
  try {
    const params = new URLSearchParams(window.location.search);
    const fromQuery = params.get('licenseUrl');
    if (fromQuery) {
      localStorage.setItem(LICENSE_URL_KEY, fromQuery);
      return fromQuery;
    }
  } catch (_) { /* SSR safety */ }
  return null;
}

export function getLicenseUrl() {
  // Hosted web build: the License Server origin is fixed at build time.
  if (LICENSE_ORIGIN) return LICENSE_ORIGIN;

  const fromQuery = urlFromQuery();
  if (fromQuery) return fromQuery;

  const stored = localStorage.getItem(LICENSE_URL_KEY);
  // Migrate: any old build that pinned the URL to the VPS gets wiped so
  // the new local-first default takes effect on next launch. Keep custom
  // URLs (LAN IPs, alternative hosts) that the operator set intentionally.
  if (stored && (stored.includes('nip.io') || stored.startsWith('http://localhost'))) {
    localStorage.removeItem(LICENSE_URL_KEY);
    return DEFAULT_URL;
  }
  return stored || DEFAULT_URL;
}

/**
 * Off-site fallback URL — used by the client when {@link getLicenseUrl}
 * (typically localhost) is unreachable. Returns the remote VPS so a
 * machine without the local service still gets to log in.
 */
export function getFallbackLicenseUrl() {
  // Web build: the configured origin is authoritative — skip the
  // localhost->VPS fallback dance (a desktop-only resilience hack).
  if (LICENSE_ORIGIN) return LICENSE_ORIGIN;
  return VPS_URL;
}

export function setLicenseUrl(url) {
  if (url) {
    localStorage.setItem(LICENSE_URL_KEY, url);
  } else {
    localStorage.removeItem(LICENSE_URL_KEY);
  }
}

export function getRefreshToken() {
  return localStorage.getItem(REFRESH_KEY);
}

export function setRefreshToken(token) {
  if (token) {
    localStorage.setItem(REFRESH_KEY, token);
  } else {
    localStorage.removeItem(REFRESH_KEY);
  }
}

let onUnauthorized = null;

/** Register a callback that fires when the License Server rejects auth. */
export function setLicenseUnauthorizedHandler(handler) {
  onUnauthorized = handler;
}

/**
 * Persist a fresh access + refresh pair from a login or refresh response.
 * Called by the AuthProvider; centralised here so we have a single place
 * to keep the two tokens in sync.
 */
export function persistAuthPair(response) {
  if (!response) return;
  if (response.token) setToken(response.token);
  if (response.refreshToken) setRefreshToken(response.refreshToken);
}

/** Wipe both tokens — used on logout and on irrecoverable refresh failure. */
export function clearAuthPair() {
  setToken(null);
  setRefreshToken(null);
  // Also drop the active shop id so a logout (or a dead session) never leaves a
  // stale tenant header for whoever logs in next. Literal key matches
  // ACTIVE_SHOP_KEY in client.js (kept inline to avoid an import cycle).
  try { localStorage.removeItem('savdopro.activeShopId'); } catch { /* ignore */ }
}

// In-flight refresh promise; null when no refresh is happening. Every
// concurrent caller awaits the same promise, so parallel 401s only
// trigger one refresh round-trip.
let refreshInFlight = null;

async function refreshOnce() {
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
      persistAuthPair(data);
      return data.token;
    } catch (_) {
      return null;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

// 8-second hard timeout per fetch so the UI is never stuck on a dead
// host — a TCP connect to an unreachable VPS would otherwise hang for
// ~75 s on Windows before the OS gives up.
const FETCH_TIMEOUT_MS = 8_000;

function buildOptions(method, body) {
  const options = { method, headers: {} };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  const token = getToken();
  if (token) options.headers.Authorization = `Bearer ${token}`;
  return options;
}

function fetchWithTimeout(url, options, timeoutMs = FETCH_TIMEOUT_MS) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  return fetch(url, { ...options, signal: controller.signal })
    .finally(() => clearTimeout(timer));
}

async function rawFetch(method, path, body) {
  const primaryBase = getLicenseUrl().replace(/\/+$/, '');
  const fallbackBase = getFallbackLicenseUrl().replace(/\/+$/, '');
  const options = buildOptions(method, body);

  // Try the configured (usually local) URL first.
  try {
    return await fetchWithTimeout(`${primaryBase}${path}`, options);
  } catch (err) {
    // If the primary IS the fallback, there is no point retrying.
    if (primaryBase === fallbackBase) throw err;
    // Network failure on localhost → try the off-site VPS as a backup.
    // This is the "VPS comes back to life" path so users can log in even
    // if the local service hasn't started yet.
    return fetchWithTimeout(`${fallbackBase}${path}`, options);
  }
}

async function request(method, path, body) {
  let response;
  try {
    response = await rawFetch(method, path, body);
  } catch {
    throw new LicenseError(
      "License Server'ga ulanib bo'lmadi. Internet va server URL'ini tekshiring.",
      0,
    );
  }

  // 401 → try a silent refresh and replay the original request once.
  // Skip the dance on the refresh endpoint itself to avoid loops.
  if (response.status === 401 && path !== '/api/auth/refresh') {
    const fresh = await refreshOnce();
    if (fresh) {
      try {
        response = await rawFetch(method, path, body);
      } catch {
        throw new LicenseError(
          "License Server'ga ulanib bo'lmadi.",
          0,
        );
      }
    }
  }

  if (response.status === 401 || response.status === 403) {
    clearAuthPair();
    if (onUnauthorized) onUnauthorized(response.status);
  }

  if (response.status === 204) return null;

  const text = await response.text();
  const data = text ? safeParse(text) : null;

  if (!response.ok) {
    const message = data?.message || data?.detail || data?.title
      || `Xatolik yuz berdi (${response.status})`;
    throw new LicenseError(message, response.status, data?.fieldErrors);
  }
  return data;
}

function safeParse(text) {
  try { return JSON.parse(text); } catch { return null; }
}

export class LicenseError extends Error {
  constructor(message, status, fieldErrors) {
    super(message);
    this.name = 'LicenseError';
    this.status = status;
    this.fieldErrors = fieldErrors || null;
  }
}

export const licenseApi = {
  get: (path) => request('GET', path),
  post: (path, body) => request('POST', path, body ?? {}),
  put: (path, body) => request('PUT', path, body ?? {}),
  patch: (path, body) => request('PATCH', path, body ?? {}),
  del: (path) => request('DELETE', path),
};
