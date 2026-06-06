// Browser-side product lookup in Uzbekistan's national catalogue (tasnif.soliq.uz)
// via a CORS relay the operator controls (a Cloudflare Worker — see
// ops/mxik-cors-worker.js), whose URL is set in VITE_MXIK_PROXY_URL.
//
// Why a relay: the hosted web portal runs on a foreign server that CANNOT reach
// Uzbek government endpoints, and tasnif sends no CORS headers so the browser
// can't read it directly either. The relay (on Cloudflare's edge) fetches tasnif
// and adds CORS. When VITE_MXIK_PROXY_URL is unset the lookup is simply skipped
// and the cashier types the product details by hand.
//
// Best-effort: any failure (relay down, not found, timeout) resolves to null.

const PROXY = (import.meta.env.VITE_MXIK_PROXY_URL || '').trim();

/**
 * @param {string} gtin canonical numeric GTIN (from the scan response)
 * @returns {Promise<null | {name, mxikCode, categoryName, unit}>}
 */
export async function lookupCatalog(gtin) {
  if (!PROXY || !gtin || !/^\d+$/.test(gtin)) {
    return null;
  }
  const sep = PROXY.includes('?') ? '&' : '?';
  const url = `${PROXY}${sep}gtin=${encodeURIComponent(gtin)}&lang=uz`;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 8000);
  try {
    const res = await fetch(url, {
      signal: controller.signal,
      headers: { Accept: 'application/json' },
    });
    if (!res.ok) {
      return null;
    }
    const data = await res.json();
    const item = data?.data?.content?.[0];
    if (!item) {
      return null;
    }
    let name = firstNonBlank(item.brandName, item.mxikName, item.positionName);
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
    return null; // relay/network/abort/parse → silent fallback to manual entry
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

// ALL-CAPS catalogue group → tidy "Sentence case".
function sentenceCase(s) {
  const t = (s || '').trim();
  if (!t) {
    return '';
  }
  return t[0].toUpperCase() + t.slice(1).toLowerCase();
}
