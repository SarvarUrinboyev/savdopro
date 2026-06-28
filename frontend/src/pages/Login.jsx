import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/Auth.jsx';
import { useT } from '../context/Settings.jsx';
import { IS_WEB } from '../config.js';

// localStorage key for the "remember me" convenience. Only the USERNAME is
// remembered and pre-filled — never the password, which would be a
// credential-theft vector on a shared till (even on the desktop kiosk build).
const SAVED_USERNAME_KEY = 'savdopro:saved-username';
// Legacy key: older desktop builds also stored the password here. We delete it
// on mount so any previously-saved password is purged on the next launch.
const LEGACY_PASSWORD_KEY = 'savdopro:saved-password';

/**
 * Centered login form rendered when no session token is present.
 * Posts to {@code /api/auth/login}; on success the AuthProvider stores
 * the JWT and re-renders the app shell.
 *
 * Behaviour:
 *   - Server URL is baked in (licenseClient.js DEFAULT_URL → nip.io VPS),
 *     and there is intentionally no UI to edit it — ordinary users never
 *     see the server address.
 *   - On a successful login only the USERNAME is remembered, so that field is
 *     pre-filled next time; the password is never persisted and must always be
 *     re-entered. (The desktop build previously saved the password too — a
 *     credential-theft risk on a shared machine — which the audit flagged.)
 */
export function Login() {
  const { login } = useAuth();
  const t = useT();

  // Read the saved username synchronously so the input renders pre-filled.
  const savedUsername = (() => {
    try { return localStorage.getItem(SAVED_USERNAME_KEY) || ''; }
    catch { return ''; }
  })();

  const [username, setUsername] = useState(savedUsername);
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [totpCode, setTotpCode] = useState('');
  const [twofaRequired, setTwofaRequired] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const doLogin = async (uname, pwd, code) => {
    setBusy(true);
    setError('');
    try {
      await login(uname.trim(), pwd, code);
      // Remember ONLY the username for next time — never the password.
      try {
        localStorage.setItem(SAVED_USERNAME_KEY, uname.trim());
      } catch { /* private mode / quota — non-fatal */ }
    } catch (err) {
      const msg = err.message || t('Login muvaffaqiyatsiz');
      if (/totp|2fa|ikki|kod/i.test(msg)) {
        setTwofaRequired(true);
      }
      setError(msg);
    } finally {
      setBusy(false);
    }
  };

  // One-time migration: purge any password an older desktop build saved to
  // localStorage so existing installs stop carrying it after this update.
  useEffect(() => {
    try { localStorage.removeItem(LEGACY_PASSWORD_KEY); } catch { /* ignore */ }
  }, []);

  const submit = async (e) => {
    e?.preventDefault?.();
    if (!username.trim() || !password) {
      setError(t('Login va parolni kiriting'));
      return;
    }
    await doLogin(username, password, twofaRequired ? totpCode.trim() : undefined);
  };

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={submit}>
        <div className="login-brand">
          <svg viewBox="0 0 40 40" aria-hidden="true">
            <defs>
              <linearGradient id="loginBrand" x1="0" y1="1" x2="1" y2="0">
                <stop offset="0%" stopColor="#1e3a8a" />
                <stop offset="100%" stopColor="#3b82f6" />
              </linearGradient>
            </defs>
            <rect width="40" height="40" rx="9" fill="url(#loginBrand)" />
            <rect x="9" y="23.5" width="6.25" height="8.3" rx="1.7"
                  fill="#ffffff" opacity="0.42" />
            <rect x="16.9" y="19" width="6.25" height="12.65" rx="1.7"
                  fill="#ffffff" opacity="0.62" />
            <rect x="24.7" y="13.6" width="6.25" height="18.1" rx="1.7" fill="#22c55e" />
            <path d="M12.2 20.5 L20 15.8 L28.1 10.9" fill="none"
                  stroke="#ffffff" strokeWidth="2"
                  strokeLinecap="round" strokeLinejoin="round" />
            <path d="M24.7 10.9 L28.1 10.9 L28.1 14.4" fill="none"
                  stroke="#ffffff" strokeWidth="2"
                  strokeLinecap="round" strokeLinejoin="round" />
          </svg>
          <div>
            <h1>SavdoPRO <span className="brand-tag">POS</span></h1>
            <p>{t('Avtomatlashtirilgan boshqaruv tizimi')}</p>
          </div>
        </div>

        <h2 className="login-title">{t('Tizimga kirish')}</h2>
        <p className="login-sub">
          {t('Akkauntingiz uchun login va parolni kiriting')}
        </p>

        <div className="field">
          <label>{t('Login')}</label>
          <input
            className="input"
            autoFocus
            required
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="admin"
          />
        </div>

        <div className="field">
          <label>{t('Parol')}</label>
          <div className="password-input">
            <input
              className="input"
              type={showPassword ? 'text' : 'password'}
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <button
              type="button"
              className="password-toggle"
              onClick={() => setShowPassword(!showPassword)}
              title={showPassword ? t("Yashirish") : t("Ko'rsatish")}
              aria-label={showPassword ? t("Yashirish") : t("Ko'rsatish")}
            >
              {showPassword ? '🙈' : '👁'}
            </button>
          </div>
        </div>

        {twofaRequired && (
          <div className="field">
            <label>{t('2FA kodi (6 raqam)')}</label>
            <input
              className="input"
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength={6}
              autoComplete="one-time-code"
              autoFocus
              value={totpCode}
              onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, ''))}
              placeholder="123456"
            />
          </div>
        )}

        {error && (
          <div className="login-error">⚠️ {error}</div>
        )}

        <button
          type="submit"
          className="btn btn-primary login-submit"
          disabled={busy}
        >
          {busy ? t('Tekshirilmoqda...') : t('Kirish')}
        </button>

        {IS_WEB ? (
          <>
            <p className="login-foot">
              {t('Hisobingiz yo\'qmi?')}{' '}
              <Link to="/register">{t('Ro\'yxatdan o\'ting')}</Link>
            </p>
            <p className="login-foot muted">
              {t('Parolni unutdingizmi?')}{' '}
              <Link to="/forgot-password">{t('Tiklash')}</Link>
            </p>
          </>
        ) : (
          <p className="login-foot muted">
            {t('Parol unutilgan bo\'lsa super-admin bilan bog\'laning')}
          </p>
        )}
      </form>
    </div>
  );
}
