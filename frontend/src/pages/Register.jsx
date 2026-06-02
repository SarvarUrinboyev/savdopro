import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/Auth.jsx';
import { AuthApi } from '../api/endpoints.js';
import { PhoneInput } from '../components/PhoneInput.jsx';
import { useT } from '../context/Settings.jsx';

/** 0–5 password strength: length + character-class variety. */
function passwordStrength(pw) {
  let score = 0;
  if (!pw) return 0;
  if (pw.length >= 9) score += 1;
  if (pw.length >= 12) score += 1;
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score += 1;
  if (/\d/.test(pw)) score += 1;
  if (/[^A-Za-z0-9]/.test(pw)) score += 1;
  return score;
}

/* Real brand marks (inline SVG — crisp at any DPI, no external requests). */
const GoogleMark = () => (
  <svg viewBox="0 0 48 48" width="22" height="22" aria-hidden="true">
    <path fill="#FFC107" d="M43.611 20.083H42V20H24v8h11.303c-1.649 4.657-6.08 8-11.303 8-6.627 0-12-5.373-12-12s5.373-12 12-12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 12.955 4 4 12.955 4 24s8.955 20 20 20 20-8.955 20-20c0-1.341-.138-2.65-.389-3.917z"/>
    <path fill="#FF3D00" d="M6.306 14.691l6.571 4.819C14.655 15.108 18.961 12 24 12c3.059 0 5.842 1.154 7.961 3.039l5.657-5.657C34.046 6.053 29.268 4 24 4 16.318 4 9.656 8.337 6.306 14.691z"/>
    <path fill="#4CAF50" d="M24 44c5.166 0 9.86-1.977 13.409-5.192l-6.19-5.238C29.211 35.091 26.715 36 24 36c-5.202 0-9.619-3.317-11.283-7.946l-6.522 5.025C9.505 39.556 16.227 44 24 44z"/>
    <path fill="#1976D2" d="M43.611 20.083H42V20H24v8h11.303c-.792 2.237-2.231 4.166-4.087 5.571l6.19 5.238C36.971 39.205 44 34 44 24c0-1.341-.138-2.65-.389-3.917z"/>
  </svg>
);
const TelegramMark = () => (
  <svg viewBox="0 0 24 24" width="24" height="24" fill="#fff" aria-hidden="true">
    <path d="M9.78 18.65l.28-4.23 7.68-6.92c.34-.31-.07-.46-.52-.19L7.74 13.3 3.64 12c-.88-.25-.89-.86.2-1.3l15.97-6.16c.73-.33 1.43.18 1.15 1.3l-2.72 12.81c-.19.91-.74 1.13-1.5.71L12.6 16.3l-2.1 2.04c-.23.23-.42.42-.72.31z"/>
  </svg>
);
const FacebookMark = () => (
  <svg viewBox="0 0 24 24" width="24" height="24" fill="#fff" aria-hidden="true">
    <path d="M15.12 5.32H17V2.14A26.11 26.11 0 0 0 14.26 2c-2.72 0-4.58 1.66-4.58 4.7v2.6H6.61v3.56h3.07V22h3.68v-9.14h3.06l.46-3.56h-3.52V7.05c0-1.03.28-1.73 1.76-1.73z"/>
  </svg>
);
const XMark = () => (
  <svg viewBox="0 0 24 24" width="19" height="19" fill="#fff" aria-hidden="true">
    <path d="M18.244 2.25h3.308l-7.227 8.26 8.502 11.24h-6.656l-5.214-6.817L4.99 21.75H1.68l7.73-8.835L1.254 2.25H8.08l4.713 6.231zm-1.161 17.52h1.833L7.084 4.126H5.117z"/>
  </svg>
);

const SOCIALS = [
  { key: 'google', label: 'Google', Mark: GoogleMark, bg: '#ffffff', border: true },
  { key: 'telegram', label: 'Telegram', Mark: TelegramMark, bg: 'linear-gradient(135deg,#2AABEE,#229ED9)' },
  { key: 'facebook', label: 'Facebook', Mark: FacebookMark, bg: '#1877F2' },
  { key: 'x', label: 'X', Mark: XMark, bg: '#000000' },
];

/** Lazy-load Google Identity Services once; resolves when window.google is ready. */
function loadGoogleScript() {
  return new Promise((resolve, reject) => {
    if (window.google?.accounts?.oauth2) { resolve(); return; }
    const ID = 'gsi-client';
    const existing = document.getElementById(ID);
    if (existing) {
      existing.addEventListener('load', () => resolve());
      existing.addEventListener('error', () => reject(new Error('gsi')));
      return;
    }
    const sc = document.createElement('script');
    sc.src = 'https://accounts.google.com/gsi/client';
    sc.async = true; sc.defer = true; sc.id = ID;
    sc.onload = () => resolve();
    sc.onerror = () => reject(new Error('gsi'));
    document.head.appendChild(sc);
  });
}

/** Lazy-load the Telegram Login Widget; resolves when window.Telegram.Login is ready. */
function loadTelegramScript() {
  return new Promise((resolve, reject) => {
    if (window.Telegram?.Login?.auth) { resolve(); return; }
    const ID = 'tg-widget';
    const existing = document.getElementById(ID);
    if (existing) {
      existing.addEventListener('load', () => resolve());
      existing.addEventListener('error', () => reject(new Error('tg')));
      return;
    }
    const sc = document.createElement('script');
    sc.src = 'https://telegram.org/js/telegram-widget.js?22';
    sc.async = true; sc.id = ID;
    sc.onload = () => resolve();
    sc.onerror = () => reject(new Error('tg'));
    document.head.appendChild(sc);
  });
}

/** Lazy-load + init the Facebook JS SDK for the given app id. */
function loadFacebookScript(appId) {
  return new Promise((resolve, reject) => {
    if (window.FB) { resolve(); return; }
    window.fbAsyncInit = function () {
      window.FB.init({ appId, cookie: true, xfbml: false, version: 'v19.0' });
      resolve();
    };
    const ID = 'fb-sdk';
    if (document.getElementById(ID)) return; // fbAsyncInit will fire
    const sc = document.createElement('script');
    sc.src = 'https://connect.facebook.net/en_US/sdk.js';
    sc.async = true; sc.defer = true; sc.id = ID;
    sc.onerror = () => reject(new Error('fb'));
    document.head.appendChild(sc);
  });
}

/** PKCE helpers for the X (Twitter) OAuth2 redirect. */
function b64url(bytes) {
  return btoa(String.fromCharCode(...new Uint8Array(bytes)))
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function randomToken(len) {
  const arr = new Uint8Array(len);
  window.crypto.getRandomValues(arr);
  return b64url(arr).slice(0, len);
}
async function pkceChallenge(verifier) {
  const digest = await window.crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return b64url(digest);
}

/**
 * True when the page is running inside an app's embedded webview (Instagram,
 * Facebook, TikTok, WeChat, an Android WebView, …). These restrict popups and
 * third-party cookies, so the social-login popups (Google/Telegram/Facebook)
 * often fail — we steer the user to a real browser instead.
 */
function isInAppBrowser() {
  const ua = (navigator.userAgent || '').toLowerCase();
  const markers = ['fban', 'fbav', 'fb_iab', 'instagram', 'line/', 'micromessenger',
    'musical_ly', 'tiktok', 'bytedance', 'snapchat', 'twitter', '; wv)'];
  return markers.some((m) => ua.includes(m));
}

/**
 * Self-service signup. Creates a trial account + owner via the License Server
 * and logs straight in. Adds a password-strength meter, optional SMS phone
 * verification (when enforced server-side) and a social-login row.
 */
export function Register() {
  const { register, applySession } = useAuth();
  const t = useT();
  const navigate = useNavigate();
  const googleClientRef = useRef(null);

  const [businessName, setBusinessName] = useState('');
  const [fullName, setFullName] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [otpBusy, setOtpBusy] = useState(false);
  const [otpSent, setOtpSent] = useState(false);
  const [error, setError] = useState('');
  const [socialMsg, setSocialMsg] = useState('');
  const [socialBusy, setSocialBusy] = useState(false);
  const [linkCopied, setLinkCopied] = useState(false);
  const [cfg, setCfg] = useState({ otpRequired: false });

  useEffect(() => {
    AuthApi.signupConfig().then(setCfg).catch(() => { /* defaults: nothing enforced */ });
  }, []);

  // Prepare the Google account chooser as soon as the server hands us a client
  // id. Building the token client up-front means the click handler can open the
  // popup synchronously (no await) — otherwise the browser blocks it.
  useEffect(() => {
    const cid = cfg.googleClientId;
    if (!cid) return undefined;
    let cancelled = false;
    loadGoogleScript().then(() => {
      if (cancelled || !window.google?.accounts?.oauth2) return;
      googleClientRef.current = window.google.accounts.oauth2.initTokenClient({
        client_id: cid,
        scope: 'openid email profile',
        callback: async (resp) => {
          if (resp?.error || !resp?.access_token) {
            setSocialBusy(false);
            setSocialMsg(t('Google bekor qilindi'));
            return;
          }
          try {
            const session = await AuthApi.socialGoogle(resp.access_token);
            applySession(session);
            navigate('/dashboard', { replace: true });
          } catch (err) {
            setSocialBusy(false);
            setSocialMsg(err.message || t('Google orqali kirib bo‘lmadi'));
          }
        },
      });
    }).catch(() => { /* offline / blocked — the button shows a message on click */ });
    return () => { cancelled = true; };
  }, [cfg.googleClientId, applySession, navigate, t]);

  // Pre-load the Telegram widget so its login popup can open synchronously on
  // click (the widget, like Google, is blocked if opened after an await).
  useEffect(() => {
    if (!cfg.telegramBotId) return undefined;
    loadTelegramScript().catch(() => { /* button shows a message on click */ });
    return undefined;
  }, [cfg.telegramBotId]);

  // Pre-load + init the Facebook SDK so FB.login() fires inside the click gesture.
  useEffect(() => {
    if (!cfg.facebookAppId) return undefined;
    loadFacebookScript(cfg.facebookAppId).catch(() => { /* message on click */ });
    return undefined;
  }, [cfg.facebookAppId]);

  const strength = useMemo(() => passwordStrength(password), [password]);
  const inApp = useMemo(() => isInAppBrowser(), []);
  const pwOk = password.length >= 9;
  const phoneDigits = (phone || '').replace(/\D/g, '');
  const phoneOk = phoneDigits.length >= 12; // 998 + 9

  const sendCode = async () => {
    if (!phoneOk) { setError(t('Avval to‘g‘ri telefon raqamini kiriting')); return; }
    setOtpBusy(true); setError('');
    try {
      await AuthApi.signupRequestOtp(phone.trim());
      setOtpSent(true);
    } catch (err) {
      setError(err.message || t('Kod yuborilmadi'));
    } finally {
      setOtpBusy(false);
    }
  };

  const submit = async (e) => {
    e?.preventDefault?.();
    if (!businessName.trim() || !fullName.trim() || !username.trim() || !password) {
      setError(t('Barcha majburiy maydonlarni to\'ldiring'));
      return;
    }
    if (!pwOk) { setError(t('Parol kamida 9 belgi bo‘lishi kerak')); return; }
    if (cfg.otpRequired && (!phoneOk || !code.trim())) {
      setError(t('Telefon raqamini SMS kod bilan tasdiqlang')); return;
    }
    setBusy(true); setError('');
    try {
      await register({
        businessName: businessName.trim(),
        fullName: fullName.trim(),
        username: username.trim(),
        password,
        phone: phone.trim() || null,
        code: code.trim() || null,
      });
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(err.message || t('Ro\'yxatdan o\'tib bo\'lmadi'));
    } finally {
      setBusy(false);
    }
  };

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href);
      setLinkCopied(true);
      setTimeout(() => setLinkCopied(false), 2000);
    } catch {
      setSocialMsg(window.location.href);
    }
  };

  const social = (s) => {
    setError('');
    // Google: open the real account chooser. The token client was built on mount
    // (see effect above) so requestAccessToken() runs inside this user gesture.
    if (s.key === 'google' && cfg.googleClientId) {
      if (!googleClientRef.current) {
        setSocialMsg(t('Google hali tayyorlanmoqda — bir lahza kuting'));
        return;
      }
      setSocialBusy(true);
      setSocialMsg(`Google: ${t('hisob tanlang...')}`);
      googleClientRef.current.requestAccessToken();
      return;
    }
    // Telegram: open the Login Widget popup. Returns the signed payload, which
    // the server re-verifies (HMAC) before logging in / cold-creating a trial.
    if (s.key === 'telegram' && cfg.telegramBotId) {
      if (!window.Telegram?.Login?.auth) {
        setSocialMsg(t('Telegram hali tayyorlanmoqda — bir lahza kuting'));
        return;
      }
      setSocialBusy(true);
      setSocialMsg(`Telegram: ${t('hisobni tasdiqlang...')}`);
      window.Telegram.Login.auth(
        { bot_id: cfg.telegramBotId, request_access: 'write' },
        async (user) => {
          if (!user) {
            setSocialBusy(false);
            setSocialMsg(t('Telegram bekor qilindi'));
            return;
          }
          try {
            const session = await AuthApi.telegramLogin({
              id: String(user.id),
              firstName: user.first_name,
              lastName: user.last_name,
              username: user.username,
              photoUrl: user.photo_url,
              authDate: String(user.auth_date),
              hash: user.hash,
            });
            applySession(session);
            navigate('/dashboard', { replace: true });
          } catch (err) {
            setSocialBusy(false);
            setSocialMsg(err.message || t('Telegram orqali kirib bo‘lmadi'));
          }
        },
      );
      return;
    }
    // Facebook: FB JS SDK popup → access token → server verifies via the Graph API.
    if (s.key === 'facebook' && cfg.facebookAppId) {
      if (!window.FB) {
        setSocialMsg(t('Facebook hali tayyorlanmoqda — bir lahza kuting'));
        return;
      }
      setSocialBusy(true);
      setSocialMsg(`Facebook: ${t('hisobni tasdiqlang...')}`);
      window.FB.login((resp) => {
        const token = resp?.authResponse?.accessToken;
        if (resp?.status !== 'connected' || !token) {
          setSocialBusy(false);
          setSocialMsg(t('Facebook bekor qilindi'));
          return;
        }
        AuthApi.socialFacebook(token)
          .then((session) => { applySession(session); navigate('/dashboard', { replace: true }); })
          .catch((err) => {
            setSocialBusy(false);
            setSocialMsg(err.message || t('Facebook orqali kirib bo‘lmadi'));
          });
      }, { scope: 'public_profile,email' });
      return;
    }
    // X (Twitter): full-page OAuth2 redirect with PKCE. The /oauth/x/callback
    // route finishes the code exchange when X redirects back.
    if (s.key === 'x' && cfg.xClientId && cfg.xRedirectUri) {
      setSocialBusy(true);
      setSocialMsg(`X: ${t('ochilmoqda...')}`);
      (async () => {
        try {
          const verifier = randomToken(64);
          const challenge = await pkceChallenge(verifier);
          const state = randomToken(24);
          sessionStorage.setItem('x_oauth_verifier', verifier);
          sessionStorage.setItem('x_oauth_state', state);
          const params = new URLSearchParams({
            response_type: 'code',
            client_id: cfg.xClientId,
            redirect_uri: cfg.xRedirectUri,
            scope: 'tweet.read users.read offline.access',
            state,
            code_challenge: challenge,
            code_challenge_method: 'S256',
          });
          window.location.href = 'https://twitter.com/i/oauth2/authorize?' + params.toString();
        } catch (e) {
          setSocialBusy(false);
          setSocialMsg(t('X ulanmadi'));
        }
      })();
      return;
    }
    // Other providers need their own OAuth app credentials before a real chooser
    // can open — until then, guide the user to the password form (no dead popup).
    const enabled = cfg[`${s.key}Login`];
    if (!enabled) {
      setSocialMsg(
        t('{x} orqali kirish tez orada — hozircha login va parol bilan davom eting')
          .replace('{x}', s.label));
      return;
    }
    setSocialBusy(true);
    setSocialMsg(`${s.label}: ${t('ulanmoqda...')}`);
  };

  const sColor = strength <= 1 ? '#ef4444' : strength <= 3 ? '#f59e0b' : '#10b981';
  const sLabel = !password ? '' : strength <= 1 ? t('Zaif') : strength <= 3 ? t('O‘rtacha') : t('Kuchli');

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={submit}>
        <h2 className="login-title">{t('Ro\'yxatdan o\'tish')}</h2>
        <p className="login-sub">{t('3 kunlik bepul sinov — kartasiz boshlang')}</p>

        <div className="field">
          <label>{t('Biznes / do\'kon nomi')}</label>
          <input className="input" autoFocus required value={businessName}
                 onChange={(e) => setBusinessName(e.target.value)}
                 placeholder={t('Masalan: Baraka Market')} />
        </div>

        <div className="field">
          <label>{t('Ismingiz')}</label>
          <input className="input" required value={fullName}
                 onChange={(e) => setFullName(e.target.value)} />
        </div>

        <div className="field">
          <label>{t('Login')}</label>
          <input className="input" required autoComplete="username" value={username}
                 onChange={(e) => setUsername(e.target.value)} />
        </div>

        <div className="field">
          <label>{t('Parol')} <span style={{ color: 'var(--text-faint)', fontWeight: 400 }}>
            ({t('kamida 9 belgi')})</span></label>
          <div className="password-input">
            <input className="input" type={showPassword ? 'text' : 'password'} required
                   autoComplete="new-password" value={password}
                   onChange={(e) => setPassword(e.target.value)} />
            <button type="button" className="password-toggle"
                    onClick={() => setShowPassword(!showPassword)}
                    aria-label={showPassword ? t('Yashirish') : t("Ko'rsatish")}>
              {showPassword ? '🙈' : '👁'}
            </button>
          </div>
          {password && (
            <div style={{ marginTop: 6 }}>
              <div style={{ display: 'flex', gap: 4 }}>
                {[0, 1, 2, 3, 4].map((i) => (
                  <div key={i} style={{
                    height: 4, flex: 1, borderRadius: 2,
                    background: i < strength ? sColor : 'var(--border)',
                  }} />
                ))}
              </div>
              <div style={{ fontSize: 11, marginTop: 3, color: sColor, fontWeight: 600 }}>
                {sLabel}{!pwOk && ` — ${t('juda qisqa')}`}
              </div>
            </div>
          )}
        </div>

        <div className="field">
          <label>{t('Telefon')} {cfg.otpRequired
            ? <span style={{ color: 'var(--red)' }}>*</span>
            : <span style={{ color: 'var(--text-faint)', fontWeight: 400 }}>({t('ixtiyoriy')})</span>}
          </label>
          <div style={{ display: 'flex', gap: 8 }}>
            <div style={{ flex: 1 }}><PhoneInput value={phone} onChange={setPhone} /></div>
            {cfg.otpRequired && (
              <button type="button" className="btn btn-ghost" disabled={otpBusy || !phoneOk}
                      onClick={sendCode} style={{ whiteSpace: 'nowrap' }}>
                {otpBusy ? '...' : otpSent ? t('Qayta yuborish') : t('Kod yuborish')}
              </button>
            )}
          </div>
          {cfg.otpRequired && otpSent && (
            <input className="input" style={{ marginTop: 8, letterSpacing: '0.3em' }}
                   inputMode="numeric" maxLength={6} placeholder={t('SMS kod (6 raqam)')}
                   value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
          )}
        </div>

        {error && <div className="login-error">⚠️ {error}</div>}

        <button type="submit" className="btn btn-primary login-submit" disabled={busy}>
          {busy ? t('Yaratilmoqda...') : t('Ro\'yxatdan o\'tish')}
        </button>

        <div style={{ textAlign: 'center', margin: '16px 0 10px', color: 'var(--text-faint)',
          fontSize: 12, position: 'relative' }}>
          <span style={{ background: 'var(--card, #fff)', padding: '0 10px' }}>
            {t('yoki tezkor kirish')}
          </span>
          <div style={{ position: 'absolute', top: '50%', left: 0, right: 0, height: 1,
            background: 'var(--border)', zIndex: -1 }} />
        </div>

        {inApp && (
          <div style={{
            background: 'rgba(245,158,11,.12)', border: '1px solid rgba(245,158,11,.4)',
            borderRadius: 10, padding: '10px 12px', fontSize: 12, marginBottom: 10,
            color: 'var(--text)', display: 'flex', flexDirection: 'column', gap: 6,
          }}>
            <span>⚠️ {t('Ijtimoiy kirish ilova ichidagi brauzerda ishlamasligi mumkin. Bu sahifani Chrome yoki Safari’da oching.')}</span>
            <button type="button" className="btn btn-ghost" onClick={copyLink}
                    style={{ alignSelf: 'flex-start', fontSize: 12, padding: '4px 10px' }}>
              {linkCopied ? `✓ ${t('Nusxalandi')}` : `🔗 ${t('Havolani nusxalash')}`}
            </button>
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'center', gap: 14 }}>
          {SOCIALS.map((s) => (
            <button key={s.key} type="button" title={t('{x} bilan davom etish').replace('{x}', s.label)}
                    aria-label={s.label} onClick={() => social(s)} disabled={socialBusy}
                    style={{
                      width: 46, height: 46, borderRadius: '50%', padding: 0,
                      cursor: socialBusy ? 'default' : 'pointer', background: s.bg,
                      border: s.border ? '1px solid var(--border)' : 'none',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      boxShadow: '0 1px 5px rgba(0,0,0,.15)',
                      transition: 'transform .08s ease', opacity: socialBusy ? 0.6 : 1,
                    }}
                    onMouseDown={(e) => { e.currentTarget.style.transform = 'scale(.92)'; }}
                    onMouseUp={(e) => { e.currentTarget.style.transform = 'scale(1)'; }}
                    onMouseLeave={(e) => { e.currentTarget.style.transform = 'scale(1)'; }}>
              <s.Mark />
            </button>
          ))}
        </div>
        {socialMsg && (
          <div style={{ textAlign: 'center', fontSize: 11, marginTop: 8,
            color: 'var(--text-faint)' }}>{socialMsg}</div>
        )}

        <p className="login-foot" style={{ marginTop: 16 }}>
          {t('Hisobingiz bormi?')} <Link to="/login">{t('Kirish')}</Link>
        </p>
      </form>
    </div>
  );
}
