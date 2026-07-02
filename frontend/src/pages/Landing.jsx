import { Link } from 'react-router-dom';
import { useT } from '../context/Settings.jsx';
import { ContactCards } from '../components/ContactCards.jsx';

// Pricing mirrors SubscriptionPlan on the License Server (BASIC/STANDARD/PRO).
// The real charge is always the backend's — these are the display catalogue.
const PLANS = [
  { key: 'BASIC', name: "Boshlang'ich", priceUzs: 99000, users: 5, shops: 1,
    perks: ['Kassa (POS)', 'Ombor', 'Asosiy hisobotlar'] },
  { key: 'STANDARD', name: 'Standart', priceUzs: 249000, users: 15, shops: 3, featured: true,
    perks: ['BASIC hammasi', 'Mijozlar va qarz', 'AI yordamchi', '3 ta do\'kon'] },
  { key: 'PRO', name: 'Pro', priceUzs: 599000, users: 100, shops: 20,
    perks: ['STANDARD hammasi', '20 ta do\'kon', 'Kengaytirilgan tahlil', 'Ustuvor qo\'llab-quvvatlash'] },
];

// Premium "module ecosystem" — each card carries a gradient icon, a benefit and
// a small product-preview mock (purely illustrative, not live data).
const MODULES = [
  { id: 'pos',   tone: 'green',  icon: '🧾', title: 'Kassa (POS)',
    benefit: 'Tez sotuv, barcode, chek va smena nazorati.' },
  { id: 'wh',    tone: 'blue',   icon: '📦', title: 'Ombor',
    benefit: 'Mahsulot, qoldiq, kam zaxira va import.' },
  { id: 'debt',  tone: 'purple', icon: '👥', title: 'Mijoz va qarz',
    benefit: "Mijoz bazasi, nasiya va to'lov tarixi." },
  { id: 'rep',   tone: 'amber',  icon: '📊', title: 'Hisobotlar',
    benefit: 'Foyda, sotuv va kassa holati real vaqtda.' },
  { id: 'ai',    tone: 'teal',   icon: '🤖', title: 'AI yordamchi',
    benefit: 'Savdo, zaxira va moliyani tezroq tushunishga yordam beradi.' },
  { id: 'multi', tone: 'orange', icon: '🏪', title: "Ko'p do'kon",
    benefit: 'Filiallarni bitta hisobdan boshqaring.' },
];

const STORY_BENEFITS = [
  { icon: '⚡', title: '5 daqiqada start', text: "O'rnatishsiz — ro'yxatdan o'ting va sotuvni boshlang." },
  { icon: '🛡️', title: 'Har kuni avtomatik nazorat', text: 'Savdo, qarz va kassa har kuni o‘zi hisoblanadi.' },
  { icon: '🏬', title: 'Filiallar uchun tayyor', text: 'Bitta hisobdan bir nechta do‘konni boshqaring.' },
];

const STEPS = [
  { n: 1, icon: '📥', title: 'Mahsulotlarni kiriting', text: 'Import yoki barcode bilan katalog tuzing.' },
  { n: 2, icon: '🧾', title: 'Kassada soting', text: 'Tez chek, naqd/karta/qarz to‘lovlari.' },
  { n: 3, icon: '📊', title: 'Hisobotni kuzating', text: 'Foyda va sotuvni real vaqtda ko‘ring.' },
  { n: 4, icon: '📒', title: 'Qarzni nazorat qiling', text: 'Nasiya va to‘lovlarni bir joyda yuriting.' },
];

function money(n) {
  return Number(n || 0).toLocaleString('ru-RU');
}

/** Small illustrative product-preview embedded in each module card. */
function ModulePreview({ id, t }) {
  if (id === 'pos') {
    return (
      <div aria-hidden="true" className="nq-pv nq-pv-pos">
        <div className="nq-pv-row"><span>Coca-Cola 1.5L</span><b>11 000</b></div>
        <div className="nq-pv-row"><span>Non × 3</span><b>10 500</b></div>
        <div className="nq-pv-row"><span>Choy 250g</span><b>18 000</b></div>
        <div className="nq-pv-pay"><span>{t('Jami')}: <b>39 500</b></span><span className="nq-pv-paybtn">{t("To'lov")}</span></div>
      </div>
    );
  }
  if (id === 'wh') {
    const rows = [['Sut', 82, ''], ['Shakar', 54, ''], ['Yog‘', 12, 'low']];
    return (
      <div aria-hidden="true" className="nq-pv nq-pv-wh">
        {rows.map(([name, pct, low]) => (
          <div key={name} className="nq-bar-row">
            <span className="nq-bar-l">{name}</span>
            <span className="nq-bar"><i className={low ? 'low' : ''} style={{ width: `${pct}%` }} /></span>
            {low ? <span className="nq-badge-low">{t('Kam')}</span> : null}
          </div>
        ))}
      </div>
    );
  }
  if (id === 'debt') {
    const rows = [['A', 'Akmal aka', '+ 320 000'], ['D', 'Dilnoza', '− 85 000'], ['S', 'Sardor', '+ 140 000']];
    return (
      <div aria-hidden="true" className="nq-pv nq-pv-debt">
        {rows.map(([ini, name, amt]) => (
          <div key={name} className="nq-debt-row">
            <span className="nq-ava">{ini}</span>
            <span className="nq-debt-name">{name}</span>
            <span className={`nq-debt-amt${amt.startsWith('−') ? ' neg' : ''}`}>{amt}</span>
          </div>
        ))}
      </div>
    );
  }
  if (id === 'rep') {
    const bars = [40, 62, 50, 78, 58, 88, 70];
    return (
      <div aria-hidden="true" className="nq-pv nq-pv-rep">
        <div className="nq-rep-head"><span>{t('Sotuv')}</span><span className="nq-rep-up">▲ 12%</span></div>
        <div className="nq-rep-bars">
          {bars.map((h, i) => <span key={i} style={{ height: `${h}%` }} />)}
        </div>
      </div>
    );
  }
  if (id === 'ai') {
    return (
      <div aria-hidden="true" className="nq-pv nq-pv-ai">
        <div className="nq-ai-q">{t("Bugun eng ko'p nima sotildi?")}</div>
        <div className="nq-ai-a">
          <span className="nq-ai-ava">AI</span>
          <span>{t('Bugun')}: <b>Non</b> {t('va')} <b>Sut</b> — {t('savdoning 38%')}.</span>
        </div>
      </div>
    );
  }
  // multi
  const shops = [['Barchasi', true], ['Chilonzor', false], ['Yunusobod', false]];
  return (
    <div aria-hidden="true" className="nq-pv nq-pv-multi">
      {shops.map(([name, active]) => (
        <span key={name} className={`nq-shop-pill${active ? ' active' : ''}`}>🏪 {name}</span>
      ))}
    </div>
  );
}

/**
 * Public marketing landing (hosted web build only). Logged-out visitors at "/"
 * see the product pitch, feature grid, pricing and a trial CTA that routes to
 * /register; everything else links into /login. Theme-aware via the same CSS
 * variables the app shell uses.
 */
export function Landing() {
  const t = useT();
  return (
    <div className="lp">
      <header className="lp-nav">
        <div className="lp-logo">
          SavdoPRO <span className="lp-logo-tag">POS</span>
        </div>
        <nav className="lp-nav-links">
          <a href="#about" className="lp-link">{t('Dastur haqida')}</a>
          <a href="#contact" className="lp-link">{t('Bog\'lanish')}</a>
          <Link to="/login" className="lp-link">{t('Kirish')}</Link>
          <Link to="/register" className="btn btn-primary lp-nav-cta">
            {t('Bepul boshlash')}
          </Link>
        </nav>
      </header>

      <section className="lp-hero">
        <div className="lp-hero-copy">
          <div className="lp-hero-badge">{t('3 kun bepul — kartasiz')}</div>
          <h1 className="lp-hero-title">
            {t("Do'koningizni bitta aqlli tizimda boshqaring")}
          </h1>
          <p className="lp-hero-sub">
            {t("Kassa, ombor, mijozlar, qarz va hisobotlar — bulutda, har qanday qurilmadan. Bugun boshlang, 5 daqiqada sozlang.")}
          </p>
          <div className="lp-hero-cta">
            <Link to="/register" className="btn btn-primary lp-cta-lg">
              {t('Bepul demo ko\'rish')}
            </Link>
            <Link to="/login" className="btn lp-cta-ghost">{t('Hisobga kirish')}</Link>
          </div>
          <div className="lp-hero-note">{t('Kredit karta talab qilinmaydi · Istalgan vaqtda bekor qiling')}</div>
          <div className="lp-trust">
            <div className="lp-trust-item">
              <div className="lp-trust-num">{t('Bulut')}</div>
              <div className="lp-trust-label">{t('har qurilmadan')}</div>
            </div>
            <div className="lp-trust-item">
              <div className="lp-trust-num">5 {t('daqiqa')}</div>
              <div className="lp-trust-label">{t('tez start')}</div>
            </div>
            <div className="lp-trust-item">
              <div className="lp-trust-num">AI</div>
              <div className="lp-trust-label">{t('yordamchi')}</div>
            </div>
            <div className="lp-trust-item">
              <div className="lp-trust-num">{t('O‘zbekcha')}</div>
              <div className="lp-trust-label">{t("qo'llab-quvvatlash")}</div>
            </div>
          </div>
        </div>

        {/* Product preview — a static UI mock, NOT live data. */}
        <div className="lp-hero-preview" aria-hidden="true">
          <div className="lp-mock">
            <div className="lp-mock-top">
              <span className="lp-mock-dot r" />
              <span className="lp-mock-dot y" />
              <span className="lp-mock-dot g" />
              <span className="lp-mock-title">SavdoPRO · {t('Boshqaruv')}</span>
              <span className="lp-mock-live"><i />Demo</span>
            </div>
            <div className="lp-mock-kpis">
              <div className="lp-mk g"><span className="lp-mk-l">{t('Bugungi savdo')}</span><span className="lp-mk-v">12.4M</span><span className="lp-mk-t">▲ 8%</span></div>
              <div className="lp-mk b"><span className="lp-mk-l">{t('Sof foyda')}</span><span className="lp-mk-v">3.2M</span><span className="lp-mk-t">▲ 5%</span></div>
              <div className="lp-mk a"><span className="lp-mk-l">{t('Qarzlar')}</span><span className="lp-mk-v">1.8M</span></div>
              <div className="lp-mk v"><span className="lp-mk-l">{t('Kassa')}</span><span className="lp-mk-v">5.6M</span></div>
            </div>
            <div className="lp-mock-grid">
              <div className="lp-mock-panel">
                <div className="lp-mock-ph">{t('Savdo dinamikasi')}</div>
                <div className="lp-mock-bars">
                  {[46, 64, 52, 78, 60, 86, 72].map((h, i) => (
                    <span key={i} style={{ height: `${h}%` }} />
                  ))}
                </div>
              </div>
              <div className="lp-mock-panel">
                <div className="lp-mock-ph">{t("To'lov ulushi")}</div>
                <div className="lp-mock-donut">
                  <svg viewBox="0 0 80 80">
                    <circle cx="40" cy="40" r="30" fill="none" stroke="var(--surface-2)" strokeWidth="12" />
                    <circle cx="40" cy="40" r="30" fill="none" stroke="var(--green)" strokeWidth="12"
                            strokeDasharray="75.4 188.5" strokeDashoffset="0" transform="rotate(-90 40 40)" />
                    <circle cx="40" cy="40" r="30" fill="none" stroke="var(--blue)" strokeWidth="12"
                            strokeDasharray="56.5 188.5" strokeDashoffset="-75.4" transform="rotate(-90 40 40)" />
                    <circle cx="40" cy="40" r="30" fill="none" stroke="var(--amber)" strokeWidth="12"
                            strokeDasharray="33.9 188.5" strokeDashoffset="-131.9" transform="rotate(-90 40 40)" />
                    <circle cx="40" cy="40" r="30" fill="none" stroke="var(--red)" strokeWidth="12"
                            strokeDasharray="22.6 188.5" strokeDashoffset="-165.8" transform="rotate(-90 40 40)" />
                  </svg>
                </div>
                <div className="lp-mock-leg">
                  <span><i className="g" />{t('Naqd')}</span>
                  <span><i className="b" />{t('Karta')}</span>
                  <span><i className="a" />{t("O'tkazma")}</span>
                  <span><i className="r" />{t('Qarz')}</span>
                </div>
              </div>
            </div>
          </div>
          <div className="lp-mock-phone">
            <div className="lp-mock-phone-top">{t('Kassa (POS)')}</div>
            <div className="lp-mock-phone-row"><span>Coca-Cola 1.5L</span><span>11 000</span></div>
            <div className="lp-mock-phone-row"><span>Non × 3</span><span>10 500</span></div>
            <div className="lp-mock-phone-total"><span>{t('Jami')}</span><b>21 500</b></div>
            <div className="lp-mock-phone-pay">{t("To'lov")}</div>
          </div>
        </div>
      </section>

      {/* PHASE B — premium module ecosystem */}
      <section className="lp-section">
        <h2 className="lp-h2">{t('SavdoPRO bilan nimalarni boshqarasiz?')}</h2>
        <p className="lp-h2-sub">
          {t('Kassa, ombor, qarz, hisobot va AI-ready nazorat — barchasi bitta premium platformada.')}
        </p>
        <div className="nq-grid">
          {MODULES.map((m) => (
            <div key={m.id} className={`nq-card nq-${m.tone}`}>
              <div className="nq-card-head">
                <span className="nq-ico">{m.icon}</span>
                <div>
                  <div className="nq-label">{t('Modul')}</div>
                  <div className="nq-title">{t(m.title)}</div>
                </div>
              </div>
              <p className="nq-benefit">{t(m.benefit)}</p>
              <ModulePreview id={m.id} t={t} />
            </div>
          ))}
        </div>
      </section>

      {/* PHASE C — "Dastur haqida" product story */}
      <section className="lp-section" id="about">
        <div className="ds-grid">
          <div className="ds-copy">
            <div className="ds-eyebrow">{t('Dastur haqida')}</div>
            <h2 className="ds-h2">{t('Oddiy do‘konni aqlli boshqaruv markaziga aylantiring')}</h2>
            <p>
              {t("SavdoPRO — O'zbekiston bizneslari uchun bulutli savdo boshqaruv tizimi. Kassa (POS), ombor, mijozlar va qarz, hisobotlar hamda AI yordamchi — barchasi bitta tizimda.")}
            </p>
            <p>
              {t("Telefon, planshet yoki kompyuterdan, istalgan vaqtda, o'rnatishsiz ishlang. Ma'lumotlar bulutda saqlanadi va har bir do'kon faqat o'z hisobotini ko'radi.")}
            </p>
            <p>
              {t("Kichik do'kondan ko'p filialli tarmoqqacha — biznesingiz bilan birga o'sadi.")}
            </p>
          </div>
          <div className="ds-benefits">
            {STORY_BENEFITS.map((b) => (
              <div key={b.title} className="ds-benefit">
                <span className="ds-benefit-ico">{b.icon}</span>
                <div>
                  <div className="ds-benefit-title">{t(b.title)}</div>
                  <div className="ds-benefit-text">{t(b.text)}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="ds-timeline">
          {STEPS.map((s, i) => (
            <div key={s.n} className="ds-step">
              <div className="ds-step-top">
                <span className="ds-step-ico">{s.icon}</span>
                {i < STEPS.length - 1 && <span className="ds-step-line" />}
              </div>
              <div className="ds-step-num">{t('Qadam')} {s.n}</div>
              <div className="ds-step-title">{t(s.title)}</div>
              <div className="ds-step-text">{t(s.text)}</div>
            </div>
          ))}
        </div>
      </section>

      <section className="lp-section" id="pricing">
        <h2 className="lp-h2">{t('Oddiy narxlar')}</h2>
        <p className="lp-h2-sub">{t('Har bir reja 3 kunlik bepul sinov bilan boshlanadi.')}</p>
        <div className="lp-plans">
          {PLANS.map((p) => (
            <div key={p.key} className={`lp-plan card${p.featured ? ' lp-plan-featured' : ''}`}>
              {p.featured && <div className="lp-plan-badge">{t('Ommabop')}</div>}
              <div className="lp-plan-name">{t(p.name)}</div>
              <div className="lp-plan-price">
                {money(p.priceUzs)} <span className="lp-plan-per">{t('so\'m/oy')}</span>
              </div>
              <div className="lp-plan-limits">
                {p.users} {t('foydalanuvchi')} · {p.shops} {t("do'kon")}
              </div>
              <ul className="lp-plan-perks">
                {p.perks.map((perk) => (
                  <li key={perk}>✓ {t(perk)}</li>
                ))}
              </ul>
              <Link to="/register" className={`btn lp-plan-cta ${p.featured ? 'btn-primary' : ''}`}>
                {t('Tanlash')}
              </Link>
            </div>
          ))}
        </div>
      </section>

      {/* PHASE D — premium conversion band */}
      <section className="lp-section" id="cta">
        <div className="cta2">
          <div className="cta2-copy">
            <h2 className="cta2-title">{t('Bugun bepul boshlang')}</h2>
            <p className="cta2-sub">
              {t("Kartasiz, 5 daqiqada. Savdoni raqamlashtirishni hoziroq boshlang.")}
            </p>
            <div className="cta2-chips">
              <span className="cta2-chip">✓ {t('Kartasiz sinov')}</span>
              <span className="cta2-chip">✓ {t('3 kun bepul')}</span>
              <span className="cta2-chip">✓ {t("O'zbekcha qo'llab-quvvatlash")}</span>
            </div>
          </div>
          <div className="cta2-actions">
            <Link to="/register" className="btn cta2-primary">{t('Bepul boshlash')}</Link>
            <a href="#about" className="btn cta2-ghost">{t('Demo ko‘rish')}</a>
          </div>
        </div>
      </section>

      {/* PHASE D — premium contact area */}
      <section className="lp-section" id="contact">
        <h2 className="lp-h2">{t('SavdoPRO jamoasi bilan bog‘laning')}</h2>
        <p className="lp-h2-sub">
          {t("Admin va sotuv bo'limi bilan to'g'ridan-to'g'ri bog'laning.")}
        </p>
        <ContactCards />
      </section>

      <footer className="lpf">
        <div className="lpf-glow" aria-hidden="true" />
        <div className="lpf-in">
          <div className="lpf-main">
            <div className="lpf-brand">
              <div className="lpf-logo">SavdoPRO <span className="lpf-logo-tag">POS</span></div>
              <p className="lpf-tag">
                {t("O'zbekiston bizneslari uchun bulutli savdo boshqaruv tizimi — kassa, ombor, mijozlar va AI tahlil bitta platformada.")}
              </p>
              <div className="lpf-badges">
                <span className="lpf-badge">☁️ {t('Bulutda')}</span>
                <span className="lpf-badge">🤖 {t('AI yordamchi')}</span>
                <span className="lpf-badge">📴 {t('Offline kassa')}</span>
                <span className="lpf-badge">🇺🇿 {t("O'zbekcha")}</span>
              </div>
            </div>
            <div className="lpf-col">
              <div className="lpf-col-h">{t('Mahsulot')}</div>
              <a href="#about" className="lpf-link">{t('Dastur haqida')}</a>
              <a href="#pricing" className="lpf-link">{t('Tariflar')}</a>
              <Link to="/login" className="lpf-link">{t('Kirish')}</Link>
            </div>
            <div className="lpf-col">
              <div className="lpf-col-h">{t('Yordam')}</div>
              <a href="#contact" className="lpf-link">{t('Bog\'lanish')}</a>
              <a href="#contact" className="lpf-link">{t('Demo so\'rash')}</a>
              <a href="#contact" className="lpf-link">{t('Texnik yordam')}</a>
            </div>
            <div className="lpf-cta">
              <div className="lpf-cta-h">{t('Bugun boshlang')}</div>
              <p className="lpf-cta-p">{t('3 kun bepul, karta shart emas.')}</p>
              <Link to="/register" className="btn lpf-cta-btn">{t('Bepul boshlash')} →</Link>
            </div>
          </div>
          <div className="lpf-bar">
            <span className="lpf-copy">© 2026 SavdoPRO — {t('Barcha huquqlar himoyalangan')}</span>
            <span className="lpf-status"><i className="lpf-dot" /> {t('Tizim ishlamoqda')}</span>
            <span className="lpf-copy">{t('Ish vaqti')}: Du–Sha, 9:00–18:00</span>
          </div>
        </div>
      </footer>
    </div>
  );
}
