import { PageHeader } from '../components/ui.jsx';
import { ContactCards } from '../components/ContactCards.jsx';
import { useT } from '../context/Settings.jsx';

/**
 * In-app "Yordam markazi" (Help) page — reachable from the sidebar. A premium
 * support hub: contact cards, a product blurb and an FAQ, all sharing the same
 * SUPPORT config as the public landing page so a signed-in merchant can reach
 * the admin / sales team at any time.
 */
const FAQ = [
  { q: 'Demo qanday ishlaydi?',
    a: "Ro'yxatdan o'tgach 3 kunlik bepul sinov ochiladi — kartasiz. Barcha modullarni sinab ko'rasiz, so'ng rejani tanlaysiz." },
  { q: 'Kassa qanday ulanadi?',
    a: "Kassa brauzerda ishlaydi. Barcode skaner USB orqali ulanadi, chek esa oddiy printerga chiqadi. Alohida o'rnatish shart emas." },
  { q: 'Qarz daftarini qanday yuritaman?',
    a: "Sotuvda \"Qarzga\" to'lov turini tanlang yoki Mijozlar bo'limida nasiya qo'shing. Har bir mijozning qarzi va to'lov tarixi avtomatik yuritiladi." },
  { q: 'Hisobotlarni qanday ko‘raman?',
    a: "Boshqaruv markazi va Buxgalteriya bo'limida foyda, sotuv va kassa holatini real vaqtda ko'rasiz. Hisobotlarni Excel/PDF ga eksport qilish mumkin." },
];

const ABOUT_POINTS = [
  { icon: '🧾', text: 'Kassa (POS), ombor va chek chop etish' },
  { icon: '👥', text: 'Mijozlar bazasi, qarz va to‘lov tarixi' },
  { icon: '📊', text: 'Foyda, sotuv va kassa hisobotlari' },
  { icon: '🤖', text: 'AI yordamchi va ko‘p do‘kon boshqaruvi' },
];

export function Help() {
  const t = useT();
  return (
    <>
      <PageHeader
        title={t('SavdoPRO yordam markazi')}
        desc={t("Admin va sotuv bo'limi bilan bog'lanish, ko'p so'raladigan savollar")}
      />

      <div className="section">
        <ContactCards />
      </div>

      <div className="grid grid-2 section hlp-about-grid">
        <div className="card hlp-about">
          <div className="card-pad">
            <div className="hlp-about-h">
              <span className="hlp-about-ico">🚀</span>
              <h2>{t('Dastur haqida')}</h2>
            </div>
            <p className="hlp-about-lead">
              {t("SavdoPRO — O'zbekiston bizneslari uchun bulutli savdo boshqaruv tizimi. Telefon, planshet yoki kompyuterdan, o'rnatishsiz ishlang.")}
            </p>
            <ul className="hlp-points">
              {ABOUT_POINTS.map((p) => (
                <li key={p.text}><span>{p.icon}</span>{t(p.text)}</li>
              ))}
            </ul>
          </div>
        </div>

        <div className="card hlp-faq">
          <div className="card-head"><h2>{t('Ko‘p so‘raladigan savollar')}</h2></div>
          <div className="card-pad">
            {FAQ.map((f) => (
              <details key={f.q} className="hlp-faq-item">
                <summary>{t(f.q)}</summary>
                <p>{t(f.a)}</p>
              </details>
            ))}
          </div>
        </div>
      </div>
    </>
  );
}
