import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/Auth.jsx';
import { AuthApi } from '../api/endpoints.js';
import { useT } from '../context/Settings.jsx';

/**
 * Lands here after X (Twitter) redirects back with ?code&state. Validates the
 * state against the value the Register page stashed, then exchanges the code +
 * PKCE verifier on the server for a session and drops the user into the app.
 */
export function XCallback() {
  const { applySession } = useAuth();
  const navigate = useNavigate();
  const t = useT();
  const [msg, setMsg] = useState(t('X orqali kirilmoqda...'));
  const ran = useRef(false);

  useEffect(() => {
    if (ran.current) return;
    ran.current = true;
    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');
    const state = params.get('state');
    const savedState = sessionStorage.getItem('x_oauth_state');
    const verifier = sessionStorage.getItem('x_oauth_verifier');
    sessionStorage.removeItem('x_oauth_state');
    sessionStorage.removeItem('x_oauth_verifier');
    if (!code || !state || state !== savedState || !verifier) {
      setMsg(t('X orqali kirish bekor qilindi'));
      setTimeout(() => navigate('/register', { replace: true }), 1500);
      return;
    }
    AuthApi.socialX(code, verifier)
      .then((session) => {
        applySession(session);
        navigate('/dashboard', { replace: true });
      })
      .catch((err) => {
        setMsg(err.message || t('X orqali kirib bo‘lmadi'));
        setTimeout(() => navigate('/register', { replace: true }), 2200);
      });
  }, [applySession, navigate, t]);

  return (
    <div className="login-shell">
      <div className="login-card" style={{ textAlign: 'center' }}>
        <h2 className="login-title">X</h2>
        <p className="login-sub">{msg}</p>
      </div>
    </div>
  );
}
