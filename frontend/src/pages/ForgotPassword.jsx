import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthApi } from '../api/endpoints.js';
import { PhoneInput } from '../components/PhoneInput.jsx';
import { useT } from '../context/Settings.jsx';

/**
 * Two-step SMS password reset (hosted web build). Step 1 requests a code for
 * a phone; step 2 verifies the code + sets a new password, then bounces back
 * to Login. Mirrors the Login/Register card styling.
 */
export function ForgotPassword() {
  const t = useT();
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [phone, setPhone] = useState('');
  const [code, setCode] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  const requestCode = async () => {
    if (!phone.trim()) {
      setError(t('Telefon raqamini kiriting'));
      return;
    }
    setBusy(true);
    setError('');
    try {
      await AuthApi.forgotPassword(phone.trim());
      setInfo(t('Agar bu raqam ro\'yxatda bo\'lsa, SMS kod yuborildi.'));
      setStep(2);
    } catch (err) {
      setError(err.message || t('Kod yuborilmadi'));
    } finally {
      setBusy(false);
    }
  };

  const doReset = async () => {
    if (!code.trim() || !newPassword) {
      setError(t('Kod va yangi parolni kiriting'));
      return;
    }
    setBusy(true);
    setError('');
    try {
      await AuthApi.resetPassword({ phone: phone.trim(), code: code.trim(), newPassword });
      navigate('/login', { replace: true });
    } catch (err) {
      setError(err.message || t('Parol tiklanmadi'));
    } finally {
      setBusy(false);
    }
  };

  const submit = (e) => {
    e?.preventDefault?.();
    if (step === 1) requestCode();
    else doReset();
  };

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={submit}>
        <h2 className="login-title">{t('Parolni tiklash')}</h2>
        <p className="login-sub">
          {step === 1
            ? t('Telefon raqamingizni kiriting — SMS kod yuboramiz')
            : t('SMS kodni va yangi parolni kiriting')}
        </p>

        <div className="field">
          <label>{t('Telefon')}</label>
          <PhoneInput
            required
            autoFocus={step === 1}
            disabled={step === 2 || busy}
            value={phone}
            onChange={setPhone}
          />
        </div>

        {step === 2 && (
          <>
            <div className="field">
              <label>{t('SMS kod')}</label>
              <input
                className="input"
                inputMode="numeric"
                required
                autoFocus
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
                placeholder="123456"
              />
            </div>
            <div className="field">
              <label>{t('Yangi parol')}</label>
              <input
                className="input"
                type="password"
                required
                autoComplete="new-password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
              />
            </div>
          </>
        )}

        {info && <div className="login-sub">{info}</div>}
        {error && <div className="login-error">⚠️ {error}</div>}

        <button type="submit" className="btn btn-primary login-submit" disabled={busy}>
          {busy ? '...' : step === 1 ? t('Kod yuborish') : t('Parolni tiklash')}
        </button>

        <p className="login-foot">
          <Link to="/login">{t('Kirishga qaytish')}</Link>
        </p>
      </form>
    </div>
  );
}
