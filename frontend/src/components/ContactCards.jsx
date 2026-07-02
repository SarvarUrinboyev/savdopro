import { useState } from 'react';
import { telegramUrl, phoneHref, emailHref, SUPPORT } from '../config/contact.js';
import { useT } from '../context/Settings.jsx';

/**
 * Premium support hub — a deep-navy glass panel (matching the landing's CTA
 * band) with three action buttons and trust chips. Shared by the public
 * landing "Bog'lanish" block and the in-app /help page.
 *
 * Privacy by design: the raw phone / telegram username / email are NEVER
 * rendered as text. They live only in hrefs (tel:/mailto:/t.me) and in the
 * clipboard fallback — Telegram opens the chat, tel:/mailto: fire natively
 * on touch devices, and on a desktop with no handler we copy the value and
 * show a GENERIC "nusxalandi" note (still without printing the value).
 */
const TELEGRAM_PATH = 'M9.78 18.65l.28-4.23 7.68-6.92c.34-.31-.07-.46-.52-.19L7.74 13.3 3.64 12c-.88-.25-.89-.86.2-1.3l15.97-6.16c.73-.33 1.43.18 1.15 1.3l-2.72 12.81c-.19.91-.74 1.13-1.5.71L12.6 16.3l-1.99 1.93c-.23.23-.42.42-.83.42z';
const PHONE_PATH = 'M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z';
const MAIL_PATH = 'M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z';

const ACTIONS = [
  {
    key: 'tg', href: telegramUrl, color: '#229ED9', icon: TELEGRAM_PATH,
    title: 'Telegram', desc: 'Eng tez javob — jonli chat',
    cta: 'Yozish', external: true, copyValue: null,
  },
  {
    key: 'phone', href: phoneHref, color: '#22C55E', icon: PHONE_PATH,
    title: 'Telefon', desc: "Reja tanlash va jonli demo bo'yicha",
    cta: "Qo'ng'iroq", copyValue: SUPPORT.phone, copiedNote: 'Raqam nusxalandi',
  },
  {
    key: 'mail', href: emailHref, color: '#F59E0B', icon: MAIL_PATH,
    title: 'Email', desc: "Hamkorlik va batafsil so'rovlar",
    cta: 'Xat yozish', copyValue: SUPPORT.email, copiedNote: 'Email nusxalandi',
  },
];

export function ContactCards() {
  const t = useT();
  const [note, setNote] = useState('');

  const isTouch = typeof navigator !== 'undefined'
    && ((navigator.maxTouchPoints || 0) > 0 || (typeof window !== 'undefined' && 'ontouchstart' in window));

  const onClick = (e, it) => {
    if (!it.copyValue || isTouch) return;  // Telegram, and tel:/mailto: on phones → native
    e.preventDefault();                    // desktop: no handler → copy quietly
    try { navigator.clipboard && navigator.clipboard.writeText(it.copyValue); } catch (_) { /* ignore */ }
    setNote(`${t(it.copiedNote)} ✓ — ${t('istalgan joyga qo\'ying')}`);
    window.clearTimeout(onClick._t);
    onClick._t = window.setTimeout(() => setNote(''), 3500);
  };

  return (
    <div className="cc-hub">
      <div className="cc-hub-glow" aria-hidden="true" />
      <div className="cc-hub-head">
        <div className="cc-hub-eyebrow">{t("Qo'llab-quvvatlash")}</div>
        <h3 className="cc-hub-title">{t('Sizga qanday yordam beraylik?')}</h3>
        <p className="cc-hub-sub">
          {t("Savdo bo'limi va texnik jamoa bilan bevosita bog'laning — kanalni o'zingiz tanlang.")}
        </p>
        <div className="cc-hub-chips">
          <span className="cc-hub-chip"><i>⚡</i> {t('Odatda ~5 daqiqada javob')}</span>
          <span className="cc-hub-chip"><i>🕘</i> Du–Sha, 9:00–18:00</span>
          <span className="cc-hub-chip"><i>🇺🇿</i> {t("O'zbek tilida yordam")}</span>
        </div>
      </div>

      <div className="cc-hub-actions">
        {ACTIONS.map((it) => (
          <a
            key={it.key}
            href={it.href}
            className="cc-act"
            target={it.external ? '_blank' : undefined}
            rel={it.external ? 'noreferrer' : undefined}
            onClick={(e) => onClick(e, it)}
            style={{ '--cc': it.color }}
          >
            <span className="cc-act-ico">
              <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true">
                <path d={it.icon} />
              </svg>
            </span>
            <span className="cc-act-body">
              <span className="cc-act-title">{t(it.title)}</span>
              <span className="cc-act-desc">{t(it.desc)}</span>
            </span>
            <span className="cc-act-cta">{t(it.cta)} <i>→</i></span>
          </a>
        ))}
      </div>

      <div className={`cc-note${note ? ' show' : ''}`} role="status">{note || '·'}</div>
    </div>
  );
}
