// IndexedDB schema for the offline POS queue.
//
// Two object stores:
//   - products  — last-known catalog snapshot, refreshed on every online
//                 ProductApi.list() call. Read-only at the POS.
//   - queue     — pending checkout requests that couldn't reach the
//                 backend. Drained by the sync worker once connectivity
//                 returns; each row is keyed by a client-generated UUID
//                 so a duplicate flush doesn't book the sale twice.
//
// Dexie wraps IndexedDB with a clean Promise API and handles schema
// migrations automatically — bumping the .version() preserves data.

import Dexie from 'dexie';

export const offlineDb = new Dexie('savdopro.offline');

offlineDb.version(1).stores({
  // [+id] auto-increment; barcode index for the POS lookup.
  products: '++id, barcode, name, quantity, updatedAt',
  // [&id] unique client-generated UUID; status indexed for the
  // sync worker's "WHERE status = 'pending'" query.
  queue: '&id, status, createdAt',
});

// Version 2 keeps the same indexes but formalises richer queue rows:
// summary, attempts, last error and cashier-visible cancellation state.
offlineDb.version(2).stores({
  products: '++id, barcode, name, quantity, updatedAt',
  queue: '&id, status, createdAt',
});

/** Persist (or replace) the product catalog snapshot. */
export async function cacheProducts(list) {
  if (!Array.isArray(list)) return;
  const now = Date.now();
  await offlineDb.transaction('rw', offlineDb.products, async () => {
    await offlineDb.products.clear();
    await offlineDb.products.bulkAdd(list.map((p) => ({
      ...p, updatedAt: now,
    })));
  });
}

/** Read the cached product list. Returns [] when nothing is cached. */
export async function getCachedProducts() {
  return offlineDb.products.toArray();
}

/** Enqueue a checkout payload to flush when connectivity returns. */
export async function enqueueCheckout(payload, summary) {
  const id = crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  await offlineDb.queue.add({
    id,
    status: 'pending',
    // Stamp the queue id as the checkout's clientRef so a replay after a lost
    // response is idempotent on the backend (V27) — never a double sale.
    payload: { ...payload, clientRef: payload.clientRef || id },
    summary: summary || summarizePayload(payload),
    createdAt: Date.now(),
    attempts: 0,
    lastTriedAt: null,
    lastError: null,
  });
  return id;
}

export async function pendingCount() {
  return offlineDb.queue.where('status').equals('pending').count();
}

export async function listPending() {
  return offlineDb.queue.where('status').equals('pending').toArray();
}

export async function listQueue(statuses = ['pending', 'failed']) {
  const rows = await offlineDb.queue.toArray();
  const wanted = new Set(statuses);
  return rows
    .filter((row) => wanted.has(row.status))
    .sort((a, b) => (a.createdAt || 0) - (b.createdAt || 0));
}

export async function pendingProductQuantities() {
  const pending = await listPending();
  const totals = {};
  for (const row of pending) {
    for (const item of row.payload?.items || []) {
      const id = item.productId;
      if (id == null) continue;
      totals[id] = (totals[id] || 0) + Number(item.quantity || 0);
    }
  }
  return totals;
}

export async function markSynced(id) {
  await offlineDb.queue.update(id, { status: 'synced', syncedAt: Date.now() });
}

export async function markFailed(id, error) {
  await offlineDb.queue.update(id, {
    lastTriedAt: Date.now(),
    lastError: String(error?.message || error || 'unknown'),
  });
}

export async function retryQueued(id) {
  await offlineDb.queue.update(id, {
    status: 'pending',
    attempts: 0,
    lastError: null,
    lastTriedAt: null,
    requeuedAt: Date.now(),
  });
}

export async function cancelQueued(id) {
  await offlineDb.queue.update(id, {
    status: 'cancelled',
    cancelledAt: Date.now(),
  });
}

export async function clearOldSynced(maxAgeMs = 7 * 24 * 60 * 60 * 1000) {
  const before = Date.now() - maxAgeMs;
  const old = await offlineDb.queue
    .where('status').equals('synced')
    .filter((row) => (row.syncedAt || row.createdAt || 0) < before)
    .toArray();
  if (old.length) {
    await offlineDb.queue.bulkDelete(old.map((row) => row.id));
  }
}

/**
 * Retry every pending checkout. Returns # successfully flushed. Safe to call
 * repeatedly: each payload carries a clientRef, so the backend dedups a replay
 * of a sale that actually went through (no double-charge). Errors are split:
 *  - server unreachable (status 0/undefined) -> stop, keep all pending, retry later;
 *  - cashier-resolvable conflicts (400/404/409 etc.) -> mark failed immediately;
 *  - transient 5xx errors -> retry a few times, then surface as failed.
 */
export async function flushQueue(checkoutFn) {
  const pending = await listPending();
  let ok = 0;
  for (const row of pending) {
    try {
      await checkoutFn(row.payload);
      await markSynced(row.id);
      ok++;
    } catch (err) {
      const status = err?.status;
      if (status === undefined || status === 0) {
        await offlineDb.queue.update(row.id, { lastTriedAt: Date.now(), lastError: 'offline' });
        break; // server unreachable — try the whole queue again later
      }
      const attempts = (row.attempts || 0) + 1;
      const patch = { attempts, lastTriedAt: Date.now(), lastError: String(err?.message || err) };
      const conflict = status >= 400 && status < 500;
      if (conflict || attempts >= 5) patch.status = 'failed';
      await offlineDb.queue.update(row.id, patch);
    }
  }
  return ok;
}

/** Count of checkouts that exhausted their retries and need cashier attention. */
export async function failedCount() {
  return offlineDb.queue.where('status').equals('failed').count();
}

function summarizePayload(payload) {
  const items = Array.isArray(payload?.items) ? payload.items : [];
  return {
    itemCount: items.length,
    total: null,
    method: payload?.paymentMethod || null,
    items: items.map((item) => ({
      productId: item.productId,
      quantity: item.quantity,
    })),
  };
}
