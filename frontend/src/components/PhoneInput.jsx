/**
 * Phone input locked to the Uzbek format: a fixed, non-deletable "+998" prefix
 * followed by exactly nine national digits (digits only — letters and extra
 * digits are ignored). Reports its value as "+998XXXXXXXXX".
 *
 * Usage:  <PhoneInput value={phone} onChange={setPhone} />
 * (onChange receives the full string, not a DOM event.)
 */
export function normalizePhone(v) {
  let d = String(v || '').replace(/\D/g, '');
  if (d.startsWith('998')) d = d.slice(3);
  return '+998' + d.slice(0, 9);
}

export function PhoneInput({
  value,
  onChange,
  placeholder = '90 123 45 67',
  autoFocus = false,
  required = false,
  disabled = false,
  name,
  id,
}) {
  let national = String(value || '').replace(/\D/g, '');
  if (national.startsWith('998')) national = national.slice(3);
  national = national.slice(0, 9);

  const handle = (e) => {
    const digits = e.target.value.replace(/\D/g, '').slice(0, 9);
    onChange('+998' + digits);
  };

  return (
    <div
      className="input"
      style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '0 0 0 12px', overflow: 'hidden', opacity: disabled ? 0.6 : 1 }}
    >
      <span style={{ fontWeight: 600, whiteSpace: 'nowrap', opacity: 0.75 }}>+998</span>
      <input
        type="tel"
        inputMode="numeric"
        pattern="[0-9]*"
        maxLength={9}
        value={national}
        onChange={handle}
        placeholder={placeholder}
        autoFocus={autoFocus}
        required={required}
        disabled={disabled}
        name={name}
        id={id}
        style={{
          flex: 1, border: 'none', outline: 'none', background: 'transparent',
          padding: '10px 12px 10px 0', font: 'inherit', color: 'inherit', minWidth: 0,
        }}
      />
    </div>
  );
}
