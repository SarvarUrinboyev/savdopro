// Barakat Market — MOCK storefront backend (zero-dependency Node).
// Implements the customer API contract the Flutter app expects, with CORS,
// in-memory state and sample data. Run: node server.js  (listens :8090)
//
// NOT for production — demo only. Accepts OTP code "111111" for any phone.

const http = require('http');

const PORT = 8090;

// ── Sample data ─────────────────────────────────────────────────────────
// Real, subject-matching photos from loremflickr (keyword-based, CORS-enabled,
// deterministic via ?lock). Renders in Flutter web/CanvasKit (needs CORS).
const lf = (kw, lock, w = 400, h = 300) =>
  `https://loremflickr.com/${w}/${h}/${encodeURIComponent(kw)}?lock=${lock}`;

// Per-product photo keyword (English) so each image matches the product.
const KW = {
  1: 'apple,fruit', 2: 'banana', 3: 'orange,fruit', 4: 'grapes', 5: 'pear,fruit', 6: 'strawberry,fruit',
  7: 'tomato,vegetable', 8: 'cucumber,vegetable', 9: 'potato,vegetable', 10: 'onion,vegetable', 11: 'carrot,vegetable',
  12: 'milk,glass', 13: 'cheese,food', 14: 'cream,food', 15: 'cheese,wheel',
  16: 'bread,bakery', 17: 'bun,bakery', 18: 'chocolate,cake',
  19: 'cola,drink', 20: 'water,bottle', 21: 'orange,juice', 22: 'tea,cup',
  23: 'beef,steak', 24: 'chicken,meat',
};

const categories = [
  { id: 1, name: 'Mevalar', slug: 'mevalar', iconUrl: lf('fruit,basket', 101, 200, 200), productCount: 6 },
  { id: 2, name: 'Sabzavotlar', slug: 'sabzavotlar', iconUrl: lf('vegetables,fresh', 102, 200, 200), productCount: 5 },
  { id: 3, name: 'Sut mahsulotlari', slug: 'sut', iconUrl: lf('milk,glass', 113, 200, 200), productCount: 4 },
  { id: 4, name: 'Non va shirinlik', slug: 'non', iconUrl: lf('bread,bakery', 104, 200, 200), productCount: 3 },
  { id: 5, name: 'Ichimliklar', slug: 'ichimlik', iconUrl: lf('juice,drink', 115, 200, 200), productCount: 4 },
  { id: 6, name: "Go'sht", slug: 'gosht', iconUrl: lf('meat,steak', 106, 200, 200), productCount: 2 },
];

const banners = [
  { id: 1, title: 'Yangi hosil — 20% chegirma', subtitle: 'Mevalar va sabzavotlarga', imageUrl: lf('fruits,market', 201, 800, 350), actionLink: '/category/1' },
  { id: 2, title: 'Bepul yetkazib berish', subtitle: '300 000 so\'mdan yuqori xaridlarga', imageUrl: lf('grocery,delivery', 202, 800, 350), actionLink: null },
  { id: 3, title: 'Sut mahsulotlari yangi', subtitle: 'Har kuni yangi yetkazib beriladi', imageUrl: lf('dairy,fresh', 203, 800, 350), actionLink: '/category/3' },
];

const products = [
  { id: 1, name: 'Olma (Semerenka)', price: 18000, oldPrice: 22000, unit: 'kg', categoryId: 1, inStock: true },
  { id: 2, name: 'Banan', price: 24000, oldPrice: null, unit: 'kg', categoryId: 1, inStock: true },
  { id: 3, name: 'Apelsin', price: 28000, oldPrice: 32000, unit: 'kg', categoryId: 1, inStock: true },
  { id: 4, name: 'Uzum (yashil)', price: 35000, oldPrice: null, unit: 'kg', categoryId: 1, inStock: true },
  { id: 5, name: 'Nok', price: 26000, oldPrice: null, unit: 'kg', categoryId: 1, inStock: false },
  { id: 6, name: 'Qulupnay', price: 45000, oldPrice: 55000, unit: 'kg', categoryId: 1, inStock: true },
  { id: 7, name: 'Pomidor', price: 12000, oldPrice: 15000, unit: 'kg', categoryId: 2, inStock: true },
  { id: 8, name: 'Bodring', price: 9000, oldPrice: null, unit: 'kg', categoryId: 2, inStock: true },
  { id: 9, name: 'Kartoshka', price: 7500, oldPrice: null, unit: 'kg', categoryId: 2, inStock: true },
  { id: 10, name: 'Piyoz', price: 6000, oldPrice: 8000, unit: 'kg', categoryId: 2, inStock: true },
  { id: 11, name: 'Sabzi', price: 8000, oldPrice: null, unit: 'kg', categoryId: 2, inStock: true },
  { id: 12, name: 'Sut (2.5%) 1L', price: 13000, oldPrice: null, unit: 'dona', categoryId: 3, inStock: true },
  { id: 13, name: 'Tvorog 250g', price: 18000, oldPrice: 21000, unit: 'dona', categoryId: 3, inStock: true },
  { id: 14, name: 'Smetana 200g', price: 15000, oldPrice: null, unit: 'dona', categoryId: 3, inStock: true },
  { id: 15, name: 'Pishloq (Rossiya) 200g', price: 32000, oldPrice: null, unit: 'dona', categoryId: 3, inStock: true },
  { id: 16, name: 'Non (oddiy)', price: 4000, oldPrice: null, unit: 'dona', categoryId: 4, inStock: true },
  { id: 17, name: 'Bulochka', price: 3500, oldPrice: null, unit: 'dona', categoryId: 4, inStock: true },
  { id: 18, name: 'Tort (shokoladli)', price: 95000, oldPrice: 120000, unit: 'dona', categoryId: 4, inStock: true },
  { id: 19, name: 'Coca-Cola 1.5L', price: 14000, oldPrice: null, unit: 'dona', categoryId: 5, inStock: true },
  { id: 20, name: 'Suv (Hayot) 1.5L', price: 4500, oldPrice: null, unit: 'dona', categoryId: 5, inStock: true },
  { id: 21, name: 'Sharbat (olma) 1L', price: 16000, oldPrice: 19000, unit: 'dona', categoryId: 5, inStock: true },
  { id: 22, name: 'Choy (qora) 100g', price: 22000, oldPrice: null, unit: 'dona', categoryId: 5, inStock: true },
  { id: 23, name: "Mol go'shti", price: 89000, oldPrice: null, unit: 'kg', categoryId: 6, inStock: true },
  { id: 24, name: "Tovuq fileti", price: 42000, oldPrice: 48000, unit: 'kg', categoryId: 6, inStock: true },
];

const catName = (id) => (categories.find((c) => c.id === id) || {}).name || null;
const discountPct = (p) =>
  p.oldPrice && p.oldPrice > p.price ? Math.round((100 * (p.oldPrice - p.price)) / p.oldPrice) : null;

function toSummary(p) {
  return {
    id: p.id, name: p.name, price: p.price, oldPrice: p.oldPrice,
    unit: p.unit, imageUrl: lf(KW[p.id] || 'grocery', p.id), categoryId: p.categoryId,
    inStock: p.inStock, discountPercent: discountPct(p),
  };
}
function toDetail(p) {
  return {
    ...toSummary(p),
    description: `${p.name} — yangi va sifatli mahsulot. Har kuni yangi yetkazib beriladi. `
      + `Narx ${p.unit} uchun. Saqlash sharti: salqin, quruq joyda.`,
    images: [lf(KW[p.id] || 'grocery', p.id, 600, 450), lf(KW[p.id] || 'grocery', p.id + 1000, 600, 450)],
    categoryName: catName(p.categoryId),
  };
}

// ── In-memory state ─────────────────────────────────────────────────────
const customer = { id: 1, phone: '+998901234567', name: 'Sarvar', email: null };
let addresses = [
  { id: 1, label: 'Uy', addressLine: 'Toshkent, Yunusobod 5-kvartal, 12-uy, 34-xonadon', lat: 41.34, lng: 69.28, comment: '3-qavat, domofon 34' },
];
let orders = [];
let addressSeq = 2;
let orderSeq = 1001;

// ── Helpers ─────────────────────────────────────────────────────────────
function send(res, code, body) {
  const data = body === undefined ? '' : JSON.stringify(body);
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
    'Access-Control-Allow-Headers': 'Authorization,Content-Type',
  });
  res.end(data);
}
function readBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (c) => (raw += c));
    req.on('end', () => {
      try { resolve(raw ? JSON.parse(raw) : {}); } catch { resolve({}); }
    });
  });
}
function buildOrder(payload) {
  const items = (payload.items || []).map((it) => {
    const p = products.find((x) => x.id === it.productId) || { name: 'Mahsulot', price: 0 };
    return {
      productId: it.productId, productName: p.name, unitPrice: p.price,
      quantity: it.quantity, lineTotal: p.price * it.quantity,
    };
  });
  const itemsTotal = items.reduce((s, i) => s + i.lineTotal, 0);
  const deliveryFee = payload.deliveryType === 'PICKUP' || itemsTotal >= 300000 || itemsTotal === 0 ? 0 : 15000;
  const addr = addresses.find((a) => a.id === payload.addressId);
  return {
    id: orderSeq++, status: 'NEW',
    deliveryType: payload.deliveryType || 'DELIVERY',
    paymentMethod: payload.paymentMethod || 'CASH',
    addressLine: addr ? addr.addressLine : null,
    deliverySlot: payload.deliverySlot || null,
    comment: payload.comment || null,
    itemsTotal, deliveryFee, total: itemsTotal + deliveryFee,
    createdAt: new Date().toISOString(),
    items,
  };
}

// ── Router ──────────────────────────────────────────────────────────────
const server = http.createServer(async (req, res) => {
  const method = req.method;
  if (method === 'OPTIONS') return send(res, 204);

  const url = new URL(req.url, `http://localhost:${PORT}`);
  let path = url.pathname.replace(/^\/api/, ''); // strip /api prefix
  const q = url.searchParams;

  try {
    // ── Auth ──
    if (path === '/auth/request-otp' && method === 'POST') {
      const b = await readBody(req);
      return send(res, 200, { phone: b.phone || '', expiresInSeconds: 120, devCode: '111111' });
    }
    if (path === '/auth/verify-otp' && method === 'POST') {
      const b = await readBody(req);
      if (b.code !== '111111') return send(res, 400, { message: 'Kod noto\'g\'ri. Demo kod: 111111' });
      return send(res, 200, { token: 'mock-jwt-token-demo', customer: { ...customer, phone: b.phone || customer.phone } });
    }
    if (path === '/auth/me' && method === 'GET') return send(res, 200, customer);
    if (path === '/auth/me' && method === 'PATCH') {
      const b = await readBody(req);
      if (b.name !== undefined) customer.name = b.name;
      if (b.email !== undefined) customer.email = b.email;
      return send(res, 200, customer);
    }

    // ── Catalog ──
    if (path === '/categories' && method === 'GET') return send(res, 200, categories);
    if (path === '/banners' && method === 'GET') return send(res, 200, banners);
    if (path === '/products' && method === 'GET') {
      const page = parseInt(q.get('page') || '0', 10);
      const size = parseInt(q.get('size') || '20', 10);
      const catId = q.get('categoryId') ? parseInt(q.get('categoryId'), 10) : null;
      const search = (q.get('search') || '').toLowerCase().trim();
      let list = products.slice();
      if (catId) list = list.filter((p) => p.categoryId === catId);
      if (search) list = list.filter((p) => p.name.toLowerCase().includes(search));
      const total = list.length;
      const start = page * size;
      const content = list.slice(start, start + size).map(toSummary);
      return send(res, 200, {
        content, page, size, totalElements: total,
        totalPages: Math.max(1, Math.ceil(total / size)),
      });
    }
    const prodMatch = path.match(/^\/products\/(\d+)$/);
    if (prodMatch && method === 'GET') {
      const p = products.find((x) => x.id === parseInt(prodMatch[1], 10));
      return p ? send(res, 200, toDetail(p)) : send(res, 404, { message: 'Mahsulot topilmadi' });
    }

    // ── Orders ──
    if (path === '/orders' && method === 'GET') return send(res, 200, orders);
    if (path === '/orders' && method === 'POST') {
      const b = await readBody(req);
      const order = buildOrder(b);
      orders.unshift(order);
      return send(res, 200, order);
    }
    const orderMatch = path.match(/^\/orders\/(\d+)$/);
    if (orderMatch && method === 'GET') {
      const o = orders.find((x) => x.id === parseInt(orderMatch[1], 10));
      return o ? send(res, 200, o) : send(res, 404, { message: 'Buyurtma topilmadi' });
    }
    const cancelMatch = path.match(/^\/orders\/(\d+)\/cancel$/);
    if (cancelMatch && method === 'POST') {
      const o = orders.find((x) => x.id === parseInt(cancelMatch[1], 10));
      if (!o) return send(res, 404, { message: 'Buyurtma topilmadi' });
      o.status = 'CANCELLED';
      return send(res, 200, o);
    }

    // ── Addresses ──
    if (path === '/addresses' && method === 'GET') return send(res, 200, addresses);
    if (path === '/addresses' && method === 'POST') {
      const b = await readBody(req);
      const a = { id: addressSeq++, label: b.label || null, addressLine: b.addressLine || '', lat: b.lat || null, lng: b.lng || null, comment: b.comment || null };
      addresses.push(a);
      return send(res, 200, a);
    }
    const addrMatch = path.match(/^\/addresses\/(\d+)$/);
    if (addrMatch && method === 'PUT') {
      const b = await readBody(req);
      const a = addresses.find((x) => x.id === parseInt(addrMatch[1], 10));
      if (!a) return send(res, 404, { message: 'Manzil topilmadi' });
      Object.assign(a, { label: b.label ?? a.label, addressLine: b.addressLine ?? a.addressLine, lat: b.lat ?? a.lat, lng: b.lng ?? a.lng, comment: b.comment ?? a.comment });
      return send(res, 200, a);
    }
    if (addrMatch && method === 'DELETE') {
      addresses = addresses.filter((x) => x.id !== parseInt(addrMatch[1], 10));
      return send(res, 204);
    }

    return send(res, 404, { message: `Yo'l topilmadi: ${method} ${path}` });
  } catch (e) {
    return send(res, 500, { message: 'Mock server xatosi: ' + e.message });
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Mock Barakat backend → http://localhost:${PORT}/api  (OTP demo code: 111111)`);
});
