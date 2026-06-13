// Browser-side product lookup in Uzbekistan's national catalogue
// (tasnif.soliq.uz) through a small CORS relay.
//
// Why a relay:
// - the hosted VPS is outside Uzbekistan and often cannot reach tasnif;
// - tasnif rejects public-browser CORS requests;
// - the cashier's computer in Uzbekistan can reach tasnif, so the local relay
//   at 127.0.0.1 fetches it and adds CORS/PNA headers.
//
// VITE_MXIK_PROXY_URL may contain one URL or comma-separated URLs. Even when it
// is unset we try the default local relay used by ops/mxik-local-proxy.cjs.

const DEFAULT_LOCAL_PROXY = 'http://127.0.0.1:8077/';
const LOCAL_STORAGE_PROXY_KEY = 'savdopro.mxikProxyUrl';
const configuredProxies = (import.meta.env.VITE_MXIK_PROXY_URL || '')
  .split(',')
  .map((v) => v.trim())
  .filter(Boolean);

/** The relay URL the shop saved in-app (settings field), or '' on any error. */
export function getSavedProxyUrl() {
  try {
    return localStorage.getItem(LOCAL_STORAGE_PROXY_KEY)?.trim() || '';
  } catch {
    return '';
  }
}

/** Save / clear the in-app relay URL. Takes effect on the next scan (no reload). */
export function setSavedProxyUrl(url) {
  try {
    const v = (url || '').trim();
    if (v) {
      localStorage.setItem(LOCAL_STORAGE_PROXY_KEY, v);
    } else {
      localStorage.removeItem(LOCAL_STORAGE_PROXY_KEY);
    }
  } catch {
    /* ignore storage failures (private mode etc.) */
  }
}

// Built fresh on every scan so the in-app relay setting takes effect immediately,
// without a page reload: env-configured proxies first, then the in-app one, then
// the default local relay.
function proxyList() {
  return Array.from(new Set([
    ...configuredProxies,
    getSavedProxyUrl(),
    DEFAULT_LOCAL_PROXY,
  ].filter(Boolean)));
}

/**
 * @param {string} gtin canonical numeric GTIN (from the scan response)
 * @returns {Promise<null | {name, mxikCode, categoryName, unit}>}
 */
export async function lookupCatalog(gtin) {
  if (!gtin || !/^\d+$/.test(gtin)) {
    return null;
  }
  const proxies = proxyList();
  for (const proxy of proxies) {
    const suggestion = await lookupViaProxy(proxy, gtin);
    if (suggestion) {
      return suggestion;
    }
  }
  // CIS/RU barcodes are often missing from MXIK but present in barcode-list.ru.
  // Route it through the same local/ngrok relay so the hosted app does not hit
  // CORS or VPS geo-blocking.
  if (gtin.startsWith('46')) {
    for (const proxy of proxies) {
      const suggestion = await lookupViaProxy(proxy, gtin, 'barcode-list');
      if (suggestion) {
        return suggestion;
      }
    }
  }
  return null;
}

async function lookupViaProxy(proxy, gtin, source = 'mxik') {
  const sep = proxy.includes('?') ? '&' : '?';
  const sourceParam = source === 'mxik' ? '' : `&source=${encodeURIComponent(source)}`;
  const url = `${proxy}${sep}gtin=${encodeURIComponent(gtin)}&lang=uz${sourceParam}`;
  const controller = new AbortController();
  const timeoutMs = proxy.startsWith(DEFAULT_LOCAL_PROXY) ? 3000 : 8000;
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const headers = { Accept: 'application/json' };
    if (proxy.includes('ngrok-free.app')) {
      headers['ngrok-skip-browser-warning'] = 'true';
    }
    const res = await fetch(url, {
      signal: controller.signal,
      headers,
      ...(proxy.startsWith(DEFAULT_LOCAL_PROXY) ? { targetAddressSpace: 'loopback' } : {}),
    });
    if (!res.ok) {
      return null;
    }
    const data = await res.json();
    const simpleItem = data?.name
      ? {
          mxikName: data.name,
          groupName: data.categoryName,
          mxikCode: data.mxikCode,
          commonUnitName: data.unit,
        }
      : null;
    const item = data?.data?.content?.[0] || simpleItem;
    if (!item) {
      return null;
    }
    let name = firstNonBlank(item.mxikName, item.brandName, item.positionName);
    if (!name) {
      return null;
    }
    const attr = (item.attributeName || '').trim();
    if (attr && !name.toLowerCase().includes(attr.toLowerCase())) {
      name = `${name}, ${attr}`;
    }
    return {
      name,
      mxikCode: item.mxikCode || '',
      categoryName: sentenceCase(firstNonBlank(item.groupName, item.className)),
      unit: firstNonBlank(item.commonUnitName, item.unitName) || '',
    };
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

function firstNonBlank(...values) {
  for (const v of values) {
    if (v && String(v).trim()) {
      return String(v).trim();
    }
  }
  return '';
}

function sentenceCase(s) {
  const t = (s || '').trim();
  if (!t) {
    return '';
  }
  return t[0].toUpperCase() + t.slice(1).toLowerCase();
}
