import { Link } from 'react-router-dom';
import { useT } from '../context/Settings.jsx';
import { ContactButtons } from '../components/ContactButtons.jsx';

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

const FEATURES = [
  { icon: '🧾', title: 'Kassa (POS)', text: "Tez sotuv, barcode skaner, chek chop etish va smenalar." },
  { icon: '📦', title: 'Ombor', text: "Mahsulot, qoldiq, kam zaxira ogohlantirishlari va import." },
  { icon: '👥', title: 'Mijoz va qarz', text: "Mijozlar bazasi, qarz daftari va to'lov tarixi." },
  { icon: '📊', title: 'Hisobotlar', text: "Sotuv, foyda, soatlik tahlil — real vaqtda." },
  { icon: '🤖', title: 'AI yordamchi', text: "Savol bering — sotuv, top mahsulot, moliyani darrov bilib oling." },
  { icon: '🏪', title: "Ko'p do'kon", text: "Bir nechta do'konni bitta hisobdan boshqaring." },
];

function money(n) {
  return Number(n || 0).toLocaleString('ru-RU');
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
        <div className="lp-hero-badge">{t('3 kun bepul — kartasiz')}</div>
        <h1 className="lp-hero-title">
          {t("Do'koningizni bitta tizimda boshqaring")}
        </h1>
        <p className="lp-hero-sub">
          {t("Kassa, ombor, mijozlar, qarz va hisobotlar — bulutda, har qanday qurilmadan. Bugun boshlang, 5 daqiqada sozlang.")}
        </p>
        <div className="lp-hero-cta">
          <Link to="/register" className="btn btn-primary lp-cta-lg">
            {t('3 kun bepul sinab ko\'rish')}
          </Link>
          <Link to="/login" className="btn lp-cta-ghost">{t('Hisobga kirish')}</Link>
        </div>
        <div className="lp-hero-note">{t('Kredit karta talab qilinmaydi · Istalgan vaqtda bekor qiling')}</div>
      </section>

      <section className="lp-section">
        <h2 className="lp-h2">{t('Nima qila olasiz')}</h2>
        <div className="lp-features">
          {FEATURES.map((f) => (
            <div key={f.title} className="lp-feature card">
              <div className="lp-feature-ico">{f.icon}</div>
              <div className="lp-feature-title">{t(f.title)}</div>
              <div className="lp-feature-text">{t(f.text)}</div>
            </div>
          ))}
        </div>
      </section>

      <section className="lp-section" id="about">
        <h2 className="lp-h2">{t('Dastur haqida')}</h2>
        <p className="lp-h2-sub" style={{ maxWidth: 760, margin: '0 auto', lineHeight: 1.7 }}>
          {t("SavdoPRO — O'zbekiston bizneslari uchun bulutli savdo boshqaruv tizimi. Kassa (POS), ombor, mijozlar va qarz, hisobotlar hamda AI yordamchi — barchasi bitta tizimda. Telefon, planshet yoki kompyuterdan, istalgan vaqtda, o'rnatishsiz ishlang. Kichik do'kondan ko'p filialli tarmoqqacha mos keladi.")}
        </p>
      </section>

      <section className="lp-section">
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

      <section className="lp-cta-band">
        <h2 className="lp-cta-band-title">{t('Bugun bepul boshlang')}</h2>
        <p className="lp-cta-band-sub">
          {t("Kartasiz, 5 daqiqada. Savdoni raqamlashtirishni hoziroq boshlang.")}
        </p>
        <Link to="/register" className="btn btn-primary lp-cta-lg">
          {t('Hisob yaratish')}
        </Link>
      </section>

      <section className="lp-section" id="contact">
        <h2 className="lp-h2">{t('Bog\'lanish')}</h2>
        <p className="lp-h2-sub">
          {t("Admin va sotuv bo'limi bilan to'g'ridan-to'g'ri bog'laning. Ish vaqti: Du–Sha, 9:00–18:00.")}
        </p>
        <ContactButtons />
      </section>

      <footer className="lp-footer">
        <div className="lp-logo">SavdoPRO <span className="lp-logo-tag">POS</span></div>
        <div className="lp-footer-links">
          <a href="#about" className="lp-link">{t('Dastur haqida')}</a>
          <a href="#contact" className="lp-link">{t('Bog\'lanish')}</a>
          <Link to="/login" className="lp-link">{t('Kirish')}</Link>
          <Link to="/register" className="lp-link">{t('Ro\'yxatdan o\'tish')}</Link>
        </div>
        <div className="lp-footer-copy">© 2026 SavdoPRO</div>
      </footer>
    </div>
  );
}
