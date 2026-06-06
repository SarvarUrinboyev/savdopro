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

const server = http.createServer((req, res) => {
  setCors(res);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }
  let gtin = '';
  let lang = 'uz';
  try {
    const url = new URL(req.url, 'http://127.0.0.1');
    gtin = (url.searchParams.get('gtin') || '').replace(/\D/g, '');
    lang = (url.searchParams.get('lang') || 'uz').replace(/[^a-z]/gi, '').slice(0, 3) || 'uz';
  } catch { /* ignore */ }
  if (!gtin) {
    res.writeHead(400, { 'Content-Type': 'application/json' });
    res.end('{"error":"gtin required"}');
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
    res.writeHead(502, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'upstream unreachable', detail: String(e) }));
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
