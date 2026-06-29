// Price-tag PDF generator.
//
// Layout: A4 portrait, 3 columns x 8 rows = 24 tags per page.
// Each tag is 70mm wide x 35mm tall with the shop / brand colour as a
// header strip, the product name, the price in big bold UZS, the SKU
// barcode (text-only — printers usually have a real barcode font, but
// we ship a fallback that works on any printer), and a small footer
// line for the shop name.

// Named export — the stable form across jspdf 2.x → 4.x (the default export
// was dropped in the v3 ESM rework).
import { jsPDF } from 'jspdf';

const PAGE_W = 210; // mm — A4 portrait
const PAGE_H = 297;
const COLS = 3;
const ROWS = 8;
const MARGIN_X = 5;
const MARGIN_Y = 10;
const TAG_W = (PAGE_W - 2 * MARGIN_X) / COLS;
const TAG_H = (PAGE_H - 2 * MARGIN_Y) / ROWS;

/**
 * @param {Array<{name: string, sku?: string, priceUzs?: number, priceUsd?: number}>} products
 * @param {{brandName?: string, brandColor?: string, currency?: 'UZS'|'USD'}} opts
 */
export function generatePriceTagsPdf(products, opts = {}) {
  const brandName = opts.brandName || 'SavdoPRO';
  const brandColor = opts.brandColor || '#1e3a8a';
  const currency = opts.currency || 'UZS';
  const [r, g, b] = hexToRgb(brandColor);

  const doc = new jsPDF({ unit: 'mm', format: 'a4' });
  let i = 0;
  for (const p of products) {
    if (i > 0 && i % (COLS * ROWS) === 0) doc.addPage();
    const idxOnPage = i % (COLS * ROWS);
    const col = idxOnPage % COLS;
    const row = Math.floor(idxOnPage / COLS);
    const x = MARGIN_X + col * TAG_W;
    const y = MARGIN_Y + row * TAG_H;

    // Outer border.
    doc.setDrawColor(220);
    doc.setLineWidth(0.2);
    doc.rect(x + 1, y + 1, TAG_W - 2, TAG_H - 2);

    // Header strip with brand colour.
    doc.setFillColor(r, g, b);
    doc.rect(x + 1, y + 1, TAG_W - 2, 5, 'F');
    doc.setTextColor(255, 255, 255);
    doc.setFontSize(7);
    doc.text(brandName, x + 3, y + 4.5);

    // Product name (wrap at TAG_W - 6mm).
    doc.setTextColor(20, 20, 20);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(9);
    const nameLines = doc.splitTextToSize(p.name || '—', TAG_W - 6);
    doc.text(nameLines.slice(0, 2), x + 3, y + 11);

    // Big price.
    const priceValue = currency === 'USD' ? p.priceUsd : p.priceUzs;
    const priceText = formatPrice(priceValue, currency);
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(16);
    doc.setTextColor(r, g, b);
    doc.text(priceText, x + TAG_W / 2, y + TAG_H - 8, { align: 'center' });

    // SKU / barcode (text).
    if (p.sku) {
      doc.setFont('courier', 'normal');
      doc.setFontSize(7);
      doc.setTextColor(110, 110, 110);
      doc.text(String(p.sku), x + TAG_W / 2, y + TAG_H - 3, { align: 'center' });
    }

    i++;
  }

  const stamp = new Date().toISOString().slice(0, 10);
  doc.save(`narx-yorliqlari-${stamp}.pdf`);
}

function formatPrice(value, currency) {
  if (value === null || value === undefined || isNaN(value)) return '—';
  const num = Number(value);
  if (currency === 'USD') return `$ ${num.toFixed(2)}`;
  // UZS: space-thousands, no decimals.
  return `${Math.round(num).toLocaleString('en-US').replace(/,/g, ' ')} so'm`;
}

function hexToRgb(hex) {
  const m = String(hex).replace('#', '').match(/^([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i);
  return m ? [parseInt(m[1], 16), parseInt(m[2], 16), parseInt(m[3], 16)]
           : [30, 58, 138];
}
