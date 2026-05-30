import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/Auth.jsx';
import { useT } from '../context/Settings.jsx';

/**
 * Self-service signup (hosted web build only). Creates a trial account + its
 * owner via the License Server and logs straight in. Mirrors the Login card's
 * styling so the two screens feel like one flow.
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
  const [showPassword, setShowPassword] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const submit = async (e) => {
    e?.preventDefault?.();
    if (!businessName.trim() || !fullName.trim() || !username.trim() || !password) {
      setError(t('Barcha majburiy maydonlarni to\'ldiring'));
      return;
    }
    setBusy(true);
    setError('');
    try {
      await register({
        businessName: businessName.trim(),
        fullName: fullName.trim(),
        username: username.trim(),
        password,
        phone: phone.trim() || null,
      });
      // register() set the user → the app shell renders; point the URL at it.
      navigate('/dashboard', { replace: true });
    } catch (err) {
      setError(err.message || t('Ro\'yxatdan o\'tib bo\'lmadi'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={submit}>
        <h2 className="login-title">{t('Ro\'yxatdan o\'tish')}</h2>
        <p className="login-sub">{t('14 kunlik bepul sinov — kartasiz boshlang')}</p>

        <div className="field">
          <label>{t('Biznes / do\'kon nomi')}</label>
          <input
            className="input"
            autoFocus
            required
            value={businessName}
            onChange={(e) => setBusinessName(e.target.value)}
            placeholder={t('Masalan: Baraka Market')}
          />
        </div>

        <div className="field">
          <label>{t('Ismingiz')}</label>
          <input
            className="input"
            required
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
          />
        </div>

        <div className="field">
          <label>{t('Login')}</label>
          <input
            className="input"
            required
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </div>

        <div className="field">
          <label>{t('Parol')}</label>
          <div className="password-input">
            <input
              className="input"
              type={showPassword ? 'text' : 'password'}
              required
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <button
              type="button"
              className="password-toggle"
              onClick={() => setShowPassword(!showPassword)}
              aria-label={showPassword ? t('Yashirish') : t("Ko'rsatish")}
            >
              {showPassword ? '🙈' : '👁'}
            </button>
          </div>
        </div>

        <div className="field">
          <label>{t('Telefon (ixtiyoriy)')}</label>
          <input
            className="input"
            inputMode="tel"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            placeholder="+998 90 123 45 67"
          />
        </div>

        {error && <div className="login-error">⚠️ {error}</div>}

        <button type="submit" className="btn btn-primary login-submit" disabled={busy}>
          {busy ? t('Yaratilmoqda...') : t('Ro\'yxatdan o\'tish')}
        </button>

        <p className="login-foot">
          {t('Hisobingiz bormi?')} <Link to="/login">{t('Kirish')}</Link>
        </p>
      </form>
    </div>
  );
}
