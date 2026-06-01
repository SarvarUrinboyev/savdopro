import { PageHeader } from '../components/ui.jsx';
import { ContactButtons } from '../components/ContactButtons.jsx';
import { useT } from '../context/Settings.jsx';

/**
 * In-app "Bog'lanish" (Help) page — reachable from the sidebar. Shows a short
 * "Dastur haqida" blurb and the same contact buttons as the landing page so a
 * signed-in merchant can reach the admin / sales team at any time.
 */
export function Help() {
  const t = useT();
  return (
    <>
      <PageHeader
        title={t("Bog'lanish")}
        desc={t("Admin va sotuv bo'limi bilan bog'lanish")}
      />

      <div className="card section">
        <div className="card-pad">
          <h2 style={{ marginTop: 0 }}>{t('Dastur haqida')}</h2>
          <p style={{ lineHeight: 1.7, margin: 0 }}>
            {t("SavdoPRO — O'zbekiston bizneslari uchun bulutli savdo boshqaruv tizimi. Kassa (POS), ombor, mijozlar va qarz, hisobotlar hamda AI yordamchi — barchasi bitta tizimda. Telefon, planshet yoki kompyuterdan, istalgan vaqtda ishlang. Kichik do'kondan ko'p filialli tarmoqqacha mos keladi.")}
          </p>
        </div>
      </div>

      <div className="card section">
        <div className="card-pad" style={{ textAlign: 'center' }}>
          <h2 style={{ marginTop: 0 }}>{t('Savol yoki yordam kerakmi?')}</h2>
          <p style={{ marginBottom: 20 }}>
            {t("Admin va sotuv bo'limimiz quyidagi kanallar orqali siz bilan. Ish vaqti: Du–Sha, 9:00–18:00.")}
          </p>
          <ContactButtons />
        </div>
      </div>
    </>
  );
}
