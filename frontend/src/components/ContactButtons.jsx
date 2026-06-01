import { useState } from 'react';
import { telegramUrl, phoneHref, emailHref, SUPPORT } from '../config/contact.js';

/**
 * Three large round contact icons (Telegram / call / email) — no text labels.
 *
 * Telegram opens a web page, so it works on every device. `tel:` and `mailto:`,
 * however, need a registered handler — on a desktop without a phone/mail app the
 * browser shows a confusing "pick an application" prompt. So on non-touch
 * devices we DON'T fire those: we copy the value to the clipboard and reveal it
 * on screen instead. On phones/tablets (touch) the native dial / compose action
 * still fires.
 */
const TELEGRAM_PATH = 'M9.78 18.65l.28-4.23 7.68-6.92c.34-.31-.07-.46-.52-.19L7.74 13.3 3.64 12c-.88-.25-.89-.86.2-1.3l15.97-6.16c.73-.33 1.43.18 1.15 1.3l-2.72 12.81c-.19.91-.74 1.13-1.5.71L12.6 16.3l-1.99 1.93c-.23.23-.42.42-.83.42z';
const PHONE_PATH = 'M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z';
const MAIL_PATH = 'M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z';

const ITEMS = [
  { key: 'tg', href: telegramUrl, color: '#229ED9', icon: TELEGRAM_PATH, label: 'Telegram', external: true, copy: false },
  { key: 'phone', href: phoneHref, color: '#22C55E', icon: PHONE_PATH, label: SUPPORT.phone, value: SUPPORT.phone, copy: true },
  { key: 'mail', href: emailHref, color: '#EA4335', icon: MAIL_PATH, label: SUPPORT.email, value: SUPPORT.email, copy: true },
];

export function ContactButtons() {
  const [note, setNote] = useState('');

  const isTouch = typeof navigator !== 'undefined'
    && ((navigator.maxTouchPoints || 0) > 0 || (typeof window !== 'undefined' && 'ontouchstart' in window));

  const onClick = (e, it) => {
    if (!it.copy || isTouch) return;       // Telegram, and tel:/mailto: on phones → native
    e.preventDefault();                    // desktop: no handler → copy + reveal instead
    try { navigator.clipboard && navigator.clipboard.writeText(it.value); } catch (_) { /* ignore */ }
    setNote(`${it.value} — nusxalandi ✓`);
    window.clearTimeout(onClick._t);
    onClick._t = window.setTimeout(() => setNote(''), 3500);
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: 24, justifyContent: 'center', flexWrap: 'wrap' }}>
        {ITEMS.map((it) => (
          <a
            key={it.key}
            href={it.href}
            title={it.label}
            aria-label={it.label}
            target={it.external ? '_blank' : undefined}
            rel={it.external ? 'noreferrer' : undefined}
            onClick={(e) => onClick(e, it)}
            style={{
              width: 78, height: 78, borderRadius: '50%', background: it.color,
              color: '#fff', display: 'inline-flex', alignItems: 'center',
              justifyContent: 'center', textDecoration: 'none',
              boxShadow: '0 8px 20px rgba(0,0,0,0.18)', cursor: 'pointer',
            }}
          >
            <svg viewBox="0 0 24 24" width="38" height="38" fill="currentColor" aria-hidden="true">
              <path d={it.icon} />
            </svg>
          </a>
        ))}
      </div>
      <div style={{
        textAlign: 'center', marginTop: 14, minHeight: 22, fontWeight: 600,
        color: '#16a34a', visibility: note ? 'visible' : 'hidden',
      }}>
        {note || '·'}
      </div>
    </div>
  );
}
