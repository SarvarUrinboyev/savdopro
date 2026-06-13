import { NavLink } from 'react-router-dom';
import { useT } from '../context/Settings.jsx';

// Sub-navigation shared by every accounting page. One sidebar entry
// ("Buxgalteriya") plus these tabs keeps the sidebar uncluttered.
const TABS = [
  ['/accounting/profit-loss', 'Foyda va zarar'],
  ['/accounting/balance-sheet', 'Balans'],
  ['/accounting/cash-flow', 'Pul oqimi'],
  ['/accounting/journal', 'Jurnal'],
  ['/accounting/accounts', 'Hisoblar rejasi'],
  ['/accounting/periods', 'Davrlar'],
  ['/accounting/reconciliation', 'Moslashtirish'],
];

export function AccountingTabs() {
  const t = useT();
  return (
    <div className="chip-row section" style={{ flexWrap: 'wrap', gap: 8 }}>
      {TABS.map(([to, label]) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) => `chip ${isActive ? 'active' : ''}`}
        >
          {t(label)}
        </NavLink>
      ))}
    </div>
  );
}
