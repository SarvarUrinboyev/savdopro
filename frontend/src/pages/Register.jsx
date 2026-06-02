import { useEffect, useMemo, useState } from 'react';
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

const SOCIALS = [
  { key: 'telegram', label: 'Telegram', icon: '✈️', bg: '#229ED9' },
  { key: 'google', label: 'Google', icon: 'G', bg: '#ffffff', fg: '#444', border: true },
  { key: 'facebook', label: 'Facebook', icon: 'f', bg: '#1877F2' },
  { key: 'x', label: 'X', icon: '𝕏', bg: '#000000' },
];

/**
 * Self-service signup. Creates a trial account + owner via the License Server
 * and logs straight in. Adds a password-strength meter, optional SMS phone
 * verification (when enforced server-side) and a social-login row.
 */
export function Register() {
  const { register } = useAuth();
  const t = useT();
  const navigate = useNavigate();

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
  const [cfg, setCfg] = useState({ otpRequired: false });

  useEffect(() => {
    AuthApi.signupConfig().then(setCfg).catch(() => { /* defaults: nothing enforced */ });
  }, []);

  const strength = useMemo(() => passwordStrength(password), [password]);
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

  const social = (s) => {
    const enabled = cfg[`${s.key}Login`];
    if (!enabled) {
      setSocialMsg(`${s.label}: ${t('bu kirish usuli hali sozlanmagan')}`);
      return;
    }
    // When configured, hand off to the provider (Telegram widget / OAuth redirect).
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

        <div style={{ display: 'flex', justifyContent: 'center', gap: 14 }}>
          {SOCIALS.map((s) => (
            <button key={s.key} type="button" title={s.label} onClick={() => social(s)}
                    style={{
                      width: 46, height: 46, borderRadius: '50%', cursor: 'pointer',
                      background: s.bg, color: s.fg || '#fff', fontWeight: 800, fontSize: 19,
                      border: s.border ? '1px solid var(--border)' : 'none',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      boxShadow: '0 1px 4px rgba(0,0,0,.12)',
                    }}>
              {s.icon}
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
