/**
 * SavdoPRO — local MXIK catalogue proxy.
 *
 * Runs on the SHOP'S computer (which is in Uzbekistan) and lets the browser
 * read product data from tasnif.soliq.uz by GTIN. Needed because:
 *   - the hosted web server (abroad) and Cloudflare can't reach Uzbek gov sites;
 *   - tasnif sends no CORS headers, so the browser can't read it directly;
 *   - but THIS machine, in Uzbekistan, reaches tasnif fine — so it relays.
 *
 * It listens only on 127.0.0.1 (not exposed to the network), is locked to the
 * single read-only MXIK endpoint, and only forwards a sanitised numeric GTIN —
 * it is NOT an open proxy.
 *
 * Run it:  double-click  ops\start-mxik-proxy.bat   (keep the window open).
 * Test it: open  http://127.0.0.1:8077/?gtin=8907588001769  in the browser.
 */
'use strict';
const http = require('http');
const https = require('https');

const PORT = 8077;
const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 ' +
  '(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';

function setCors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', '*');
  // Chrome Private Network Access: a public HTTPS page calling localhost needs this.
  res.setHeader('Access-Control-Allow-Private-Network', 'true');
}

function writeJson(res, status, payload) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(payload));
}

function emptyCatalogue(source, extra = {}) {
  return { data: { content: [] }, source, ...extra };
}

function handleBarcodeList(gtin, res) {
  if (!/^\d{8,14}$/.test(gtin)) {
    writeJson(res, 200, emptyCatalogue('barcode-list'));
    return;
  }
  const target =
    `https://barcode-list.ru/barcode/RU/barcode-${gtin}/` +
    '%D0%9F%D0%BE%D0%B8%D1%81%D0%BA.htm';
  const upstream = https.get(
    target,
    {
      timeout: 12000,
      headers: {
        'User-Agent': UA,
        Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'ru,uz;q=0.9,en;q=0.8',
        Referer: 'https://barcode-list.ru/',
      },
    },
    (up) => {
      const chunks = [];
      up.on('data', (d) => chunks.push(d));
      up.on('end', () => {
        const html = Buffer.concat(chunks).toString('utf8');
        if ((up.statusCode || 0) < 200 || (up.statusCode || 0) >= 300) {
          writeJson(res, 200, emptyCatalogue('barcode-list', { upstreamStatus: up.statusCode || 0 }));
          return;
        }
        const suggestion = parseBarcodeListHtml(html, gtin);
        if (!suggestion) {
          writeJson(res, 200, emptyCatalogue('barcode-list'));
          return;
        }
        writeJson(res, 200, {
          source: 'barcode-list',
          data: {
            content: [{
              mxikName: suggestion.name,
              brandName: '',
              positionName: '',
              attributeName: '',
              mxikCode: '',
              groupName: suggestion.categoryName,
              commonUnitName: 'dona',
            }],
          },
        });
      });
    },
  );
  upstream.on('timeout', () => upstream.destroy(new Error('timeout')));
  upstream.on('error', (e) => {
    writeJson(res, 200, emptyCatalogue('barcode-list', { error: String(e) }));
  });
}

function parseBarcodeListHtml(html, gtin) {
  const title = stripHtml((html.match(/<title[^>]*>([\s\S]*?)<\/title>/i) || [])[1] || '');
  const meta = extractMeta(html, 'description') || extractMeta(html, 'og:title');
  for (const raw of [title, meta]) {
    const name = cleanBarcodeListName(raw, gtin);
    if (name) {
      return { name, categoryName: inferCategory(name) };
    }
  }
  return null;
}

function extractMeta(html, key) {
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const byNameFirst = new RegExp(
    `<meta\\s+[^>]*(?:name|property)=["']${escaped}["'][^>]*content=["']([^"']+)["'][^>]*>`,
    'i',
  );
  const byContentFirst = new RegExp(
    `<meta\\s+[^>]*content=["']([^"']+)["'][^>]*(?:name|property)=["']${escaped}["'][^>]*>`,
    'i',
  );
  return stripHtml((html.match(byNameFirst) || html.match(byContentFirst) || [])[1] || '');
}

function cleanBarcodeListName(raw, gtin) {
  let text = stripHtml(raw);
  if (!text) return '';
  const barcodeWord = '(?:\\u0428\\u0442\\u0440\\u0438\\u0445[-\\s]?\\u043a\\u043e\\u0434|Barcode|EAN|UPC)';
  text = text.replace(new RegExp(`\\s*[-|–—]\\s*${barcodeWord}\\s*:?\\s*${gtin}.*$`, 'i'), '');
  text = text.replace(new RegExp(`${barcodeWord}\\s*:?\\s*${gtin}`, 'ig'), '');
  text = text.replace(/\s*[-|–—]\s*barcode-list\.ru.*$/i, '');
  text = text.replace(/\s+/g, ' ').trim();
  if (!text || /^\d+$/.test(text) || /barcode-list\.ru/i.test(text)) return '';
  return text;
}

function stripHtml(value) {
  return decodeHtmlEntities(String(value || '')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim());
}

function decodeHtmlEntities(value) {
  const named = {
    amp: '&',
    lt: '<',
    gt: '>',
    quot: '"',
    apos: "'",
    nbsp: ' ',
    laquo: '"',
    raquo: '"',
  };
  return String(value || '')
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([0-9a-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)))
    .replace(/&([a-z]+);/gi, (m, n) => Object.prototype.hasOwnProperty.call(named, n.toLowerCase()) ? named[n.toLowerCase()] : m);
}

function inferCategory(name) {
  const lower = String(name || '').toLowerCase();
  const hasAny = (terms) => terms.some((term) => lower.includes(term));
  if (hasAny([
    '\u0441\u043f\u043b\u0430\u0442',
    '\u0437\u0443\u0431',
    '\u043f\u0430\u0441\u0442\u0430',
    '\u0448\u0430\u043c\u043f\u0443\u043d',
    '\u043c\u044b\u043b',
    '\u0433\u0435\u043b\u044c',
    '\u043a\u0440\u0435\u043c',
    'tooth',
    'paste',
    'soap',
    'shampoo',
    'cream',
  ])) return 'Kosmetika';
  if (hasAny(['phone', 'smartphone', 'airpods', 'watch', 'xiaomi', 'samsung', 'apple'])) return 'Elektronika';
  if (hasAny(['tablet', '\u0442\u0430\u0431\u043b\u0435\u0442\u043a', '\u043b\u0435\u043a\u0430\u0440\u0441\u0442\u0432', '\u0430\u043f\u0442\u0435\u043a'])) return 'Dori vositalari';
  if (hasAny(['drink', 'water', 'juice', '\u0432\u043e\u0434\u0430', '\u0441\u043e\u043a', '\u043d\u0430\u043f\u0438\u0442'])) return 'Ichimliklar';
  return 'Boshqa';
}

const server = http.createServer((req, res) => {
  setCors(res);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }
  let gtin = '';
  let lang = 'uz';
  let source = 'mxik';
  try {
    const url = new URL(req.url, 'http://127.0.0.1');
    gtin = (url.searchParams.get('gtin') || '').replace(/\D/g, '');
    lang = (url.searchParams.get('lang') || 'uz').replace(/[^a-z]/gi, '').slice(0, 3) || 'uz';
    source = (url.searchParams.get('source') || 'mxik').replace(/[^a-z-]/gi, '').toLowerCase() || 'mxik';
  } catch { /* ignore */ }
  if (!gtin) {
    writeJson(res, 400, { error: 'gtin required' });
    return;
  }
  if (source === 'barcode-list') {
    handleBarcodeList(gtin, res);
    return;
  }
  const target =
    'https://tasnif.soliq.uz/api/cls-api/mxik/search/by-params' +
    `?size=1&page=0&lang=${lang}&gtin=${gtin}`;
  const upstream = https.get(
    target,
    {
      timeout: 12000,
      headers: {
        'User-Agent': UA,
        Accept: 'application/json',
        'Accept-Language': 'uz,ru;q=0.9,en;q=0.8',
        Referer: 'https://tasnif.soliq.uz/',
      },
    },
    (up) => {
      const chunks = [];
      up.on('data', (d) => chunks.push(d));
      up.on('end', () => {
        res.writeHead(up.statusCode || 502, { 'Content-Type': 'application/json; charset=utf-8' });
        res.end(Buffer.concat(chunks));
      });
    },
  );
  upstream.on('timeout', () => upstream.destroy(new Error('timeout')));
  upstream.on('error', (e) => {
    writeJson(res, 502, { error: 'upstream unreachable', detail: String(e) });
  });
});

server.listen(PORT, '127.0.0.1', () => {
  console.log('=================================================');
  console.log(' SavdoPRO MXIK proxy ishlayapti');
  console.log(' Manzil:  http://127.0.0.1:' + PORT);
  console.log(' Sinov:   http://127.0.0.1:' + PORT + '/?gtin=8907588001769');
  console.log('');
  console.log(' Bu oynani YOPMANG. Skaner ishlayotganda ochiq tursin.');
  console.log(' Toxtatish uchun: Ctrl+C');
  console.log('=================================================');
});

server.on('error', (e) => {
  if (e.code === 'EADDRINUSE') {
    console.error('Port ' + PORT + ' band — proxy allaqachon ishlayaptimi?');
  } else {
    console.error('Xato: ' + e.message);
  }
  process.exitCode = 1;
});
