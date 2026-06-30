import { useState } from 'react';
import { telegramUrl, phoneHref, emailHref, SUPPORT } from '../config/contact.js';
import { useT } from '../context/Settings.jsx';

/**
 * Premium contact section — full cards (icon + label + description + value +
 * CTA) instead of bare round icons, plus a business-hours card. Shared by the
 * public landing "Bog'lanish" block and the in-app /help page so both stay in
 * sync with the single SUPPORT config.
 *
 * Keeps the same device-aware behaviour as the old ContactButtons: Telegram
 * always opens; tel:/mailto: fire natively on touch devices, but on a desktop
 * (no handler) we copy the value to the clipboard and reveal a confirmation.
 */
const TELEGRAM_PATH = 'M9.78 18.65l.28-4.23 7.68-6.92c.34-.31-.07-.46-.52-.19L7.74 13.3 3.64 12c-.88-.25-.89-.86.2-1.3l15.97-6.16c.73-.33 1.43.18 1.15 1.3l-2.72 12.81c-.19.91-.74 1.13-1.5.71L12.6 16.3l-1.99 1.93c-.23.23-.42.42-.83.42z';
const PHONE_PATH = 'M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z';
const MAIL_PATH = 'M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z';

const CARDS = [
  {
    key: 'tg', href: telegramUrl, color: '#229ED9', icon: TELEGRAM_PATH,
    title: 'Telegram', label: 'Tezkor yordam', desc: 'Savol bering — bir necha daqiqada javob beramiz.',
    value: `@${SUPPORT.telegram}`, cta: 'Telegramda yozish', external: true, copy: false,
  },
  {
    key: 'phone', href: phoneHref, color: '#22C55E', icon: PHONE_PATH,
    title: 'Telefon', label: 'Savdo va demo', desc: 'Reja tanlash va jonli demo bo‘yicha gaplashing.',
    value: SUPPORT.phone, cta: 'Qo‘ng‘iroq qilish', copy: true,
  },
  {
    key: 'mail', href: emailHref, color: '#EA4335', icon: MAIL_PATH,
    title: 'Email', label: 'Texnik murojaat', desc: 'Hamkorlik va batafsil so‘rovlar uchun yozing.',
    value: SUPPORT.email, cta: 'Xat yuborish', copy: true,
  },
];

export function ContactCards() {
  const t = useT();
  const [note, setNote] = useState('');

  const isTouch = typeof navigator !== 'undefined'
    && ((navigator.maxTouchPoints || 0) > 0 || (typeof window !== 'undefined' && 'ontouchstart' in window));

  const onClick = (e, it) => {
    if (!it.copy || isTouch) return;       // Telegram, and tel:/mailto: on phones → native
    e.preventDefault();                    // desktop: no handler → copy + reveal instead
    try { navigator.clipboard && navigator.clipboard.writeText(it.value); } catch (_) { /* ignore */ }
    setNote(`${it.value} — ${t('nusxalandi')} ✓`);
    window.clearTimeout(onClick._t);
    onClick._t = window.setTimeout(() => setNote(''), 3500);
  };

  return (
    <div className="cc-wrap">
      <div className="cc-grid">
        {CARDS.map((it) => (
          <a
            key={it.key}
            href={it.href}
            className="cc-card"
            title={it.value}
            target={it.external ? '_blank' : undefined}
            rel={it.external ? 'noreferrer' : undefined}
            onClick={(e) => onClick(e, it)}
            style={{ '--cc': it.color }}
          >
            <span className="cc-ico">
              <svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor" aria-hidden="true">
                <path d={it.icon} />
              </svg>
            </span>
            <div className="cc-label">{t(it.label)}</div>
            <div className="cc-title">{t(it.title)}</div>
            <div className="cc-desc">{t(it.desc)}</div>
            <div className="cc-value">{it.value}</div>
            <span className="cc-cta">{t(it.cta)} <i>→</i></span>
          </a>
        ))}

        <div className="cc-card cc-hours">
          <span className="cc-ico cc-ico-hours" aria-hidden="true">🕘</span>
          <div className="cc-label">{t('Ish vaqti')}</div>
          <div className="cc-title">Du–Sha, 9:00–18:00</div>
          <div className="cc-desc">{t('Yakshanba dam olish kuni.')}</div>
          <div className="cc-value">{t('Pilot va demo bo‘yicha yordam')}</div>
          <span className="cc-cta cc-cta-static">{t('Onlayn qo‘llab-quvvatlash')}</span>
        </div>
      </div>
      <div className={`cc-note${note ? ' show' : ''}`} role="status">{note || '·'}</div>
    </div>
  );
}
