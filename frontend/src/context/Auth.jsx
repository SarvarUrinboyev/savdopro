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

  const login = useCallback(async (username, password) => {
    const response = await AuthApi.login({ username, password });
    persistAuthPair(response);
    setUser(response.user);
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
  }, []);

  const value = {
    user,
    loading,
    error,
    login,
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
