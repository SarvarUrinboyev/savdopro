import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { AuthApi } from '../api/endpoints.js';
import { getToken, setToken, setUnauthorizedHandler } from '../api/client.js';
import {
  clearAuthPair,
  getRefreshToken,
  persistAuthPair,
  setLicenseUnauthorizedHandler,
} from '../api/licenseClient.js';

const AuthCtx = createContext(null);

/**
 * Apply per-account brand colours / name to the running document
 * (Phase 4.6 white-label). Null wipes any previously-set overrides so
 * the next user lands on the SavdoPRO defaults. We write the changes
 * to CSS variables on :root and to the document title; pages that
 * read --brand-primary / --brand-secondary inherit instantly.
 */
function applyBrand(brand) {
  const root = document.documentElement;
  const primary  = brand?.colorPrimary;
  const secondary = brand?.colorSecondary;
  const name     = brand?.name;
  if (primary)  root.style.setProperty('--brand-primary', primary);
  else          root.style.removeProperty('--brand-primary');
  if (secondary) root.style.setProperty('--brand-secondary', secondary);
  else           root.style.removeProperty('--brand-secondary');
  if (name) document.title = name + ' · SavdoPRO';
  else      document.title = 'SavdoPRO';
}

/**
 * Session state for the desktop app. Loads /api/auth/me on mount if a
 * token is present; otherwise renders the login screen. Provides
 * {@code login}, {@code logout} and {@code refresh} so any page can
 * trigger a session change.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const loadMe = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const me = await AuthApi.me();
      setUser(me);
      applyBrand(me?.brand);
    } catch (err) {
      // Only wipe the session for a genuine auth rejection (401 / 403 /
      // explicit "session invalid" message). For transient network or
      // server errors (status 0, 5xx, License Server temporarily down)
      // keep the token in localStorage so the user is auto-logged-in
      // again on the next attempt instead of being forced through Login.
      const status = err?.status;
      const isAuthReject = status === 401 || status === 403
        || /sessiya|akkaunt/i.test(String(err.message));
      if (isAuthReject) {
        // Drop both tokens — the License Server has the final say on
        // session validity and it told us no. (licenseClient already
        // tried a silent refresh before throwing, so reaching here
        // means even the refresh token is dead.)
        clearAuthPair();
        setUser(null);
      } else {
        // Surface transient failures so the UI can show a retry banner;
        // we leave `user` as-is (null on first boot, populated otherwise).
        setError(err.message || 'Serverga ulanib bo\'lmadi');
      }
    } finally {
      setLoading(false);
    }
  }, []);

  // On startup: bounce-handler clears token, refresh me when token exists.
  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null));
    setLicenseUnauthorizedHandler(() => setUser(null));
    // Treat either token as "we might still have a session" — if only
    // the refresh remains, the first /me call will trigger an in-flight
    // refresh and either resurrect the session or bounce to login.
    if (getToken() || getRefreshToken()) {
      loadMe();
    } else {
      setLoading(false);
    }
  }, [loadMe]);

  const login = useCallback(async (username, password, totpCode) => {
    const response = await AuthApi.login({ username, password, totpCode });
    persistAuthPair(response);
    setUser(response.user);
    applyBrand(response.user?.brand);
    setError(null);
    return response.user;
  }, []);

  // Self-service signup: creates a trial account + owner and logs in, so the
  // app shell appears immediately after a successful registration.
  const register = useCallback(async (form) => {
    const response = await AuthApi.register(form);
    persistAuthPair(response);
    setUser(response.user);
    applyBrand(response.user?.brand);
    setError(null);
    return response.user;
  }, []);

  // Apply a session returned by a social login (e.g. the Google account
  // chooser). Same persist + state update as login/register, so the app shell
  // appears immediately after "Sign in with Google".
  const applySession = useCallback((response) => {
    persistAuthPair(response);
    setUser(response.user);
    applyBrand(response.user?.brand);
    setError(null);
    return response.user;
  }, []);

  const logout = useCallback(() => {
    // Best-effort revoke on the server so the refresh token can't be
    // reused. We don't block on the result — the local clear below
    // happens either way so the user instantly lands on Login.
    const rt = getRefreshToken();
    if (rt) {
      AuthApi.logout({ refreshToken: rt }).catch(() => { /* ignore */ });
    }
    clearAuthPair();
    setUser(null);
    applyBrand(null);   // restore defaults
  }, []);

  const value = {
    user,
    loading,
    error,
    login,
    register,
    applySession,
    logout,
    refresh: loadMe,
  };
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error('useAuth() outside <AuthProvider>');
  return ctx;
}
