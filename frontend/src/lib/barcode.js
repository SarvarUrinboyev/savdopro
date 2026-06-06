// Canonicalises a scanned code so every physical unit of the same product maps
// to one stable string. Mirrors the backend BarcodeNormalizer — keep the two in
// sync so the POS matches whatever the warehouse stored.
//
// Uzbekistan's mandatory digital marking ("ASL BELGISI") prints a GS1 DataMatrix
// shaped like (01)<14-digit GTIN>(21)<unique serial>…; only the GTIN names the
// product, the serial differs on every unit. We reduce such codes to their GTIN
// and drop leading zeros, so a DataMatrix scan and the plain EAN-13/UPC on the
// same box resolve to the same value.
export function normalizeBarcode(raw) {
  if (raw == null) return '';
  // Drop the GS1 FNC1 / group-separator control char (ASCII 29), then trim.
  let s = String(raw).replace(/\x1d/g, '').trim();
  // Strip a leading AIM symbology identifier (e.g. "]d2", "]C1", "]Q3").
  if (s.length > 3 && s[0] === ']') s = s.slice(3);
  if (!s) return '';
  let core = s;
  if (s.length >= 16 && s.startsWith('01') && /^\d{14}$/.test(s.slice(2, 16))) {
    core = s.slice(2, 16);
  }
  if (/^\d+$/.test(core)) {
    const stripped = core.replace(/^0+/, '');
    return stripped || '0';
  }
  return core;
}
