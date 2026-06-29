# Tenant Isolation — Test Procedure & Evidence

SavdoPRO is multi-tenant: one **Account** owns one or more **Shops**, and every
row of operational data carries a `shop_id`. This document describes how we prove
that account A can never read or write account B's data, and what evidence to
collect before a pilot.

## The three enforcement layers

1. **Boundary (request):** `TenantFilter` reads `X-Shop-Id` and rejects with **403**
   unless the shop belongs to the caller's account
   (`shops.existsByIdAndAccountId(shopId, accountId)`, `TenantFilter.java`).
   It runs *before* the controller, so the check is uniform across every
   tenant-scoped endpoint.
2. **Query (list/search):** a Hibernate `@Filter` adds `WHERE shop_id = :shopId`
   (or `IN (:shopIds)` for the consolidated "all shops" view) to every list query,
   enabled per request by `TenantFilterAspect`. Fails closed if it can't be enabled.
3. **Load by id:** `TenantScopedEntity.@PostLoad` re-checks the row's `shop_id`
   against the active scope and throws **404** — so guessing another tenant's row
   id leaks nothing (the Hibernate filter alone can't rewrite `findById`).

API keys add a fourth: each key is permanently bound to one shop, so an external
integration can never widen its own scope.

## Automated proof

| Test | Level | Proves |
|---|---|---|
| `TenantIsolationEndpointIT` | HTTP (MockMvc + real JWT/filter chain) | A↔B cross-tenant **403/404** across 10 modules |
| `TenantIsolationIntegrationTest` | service / repository | `@PostLoad` 404 on cross-shop `findById`; list returns only active shop |
| `TenantFilterAspectTest` | unit | fail-closed when the filter can't be enabled |
| `ApiKeyAuthFilterTest` | unit | API key resolves to its bound shop only |

`TenantIsolationEndpointIT` sets up two tenants — A (account 90011, shop A) and
B (account 90012, shop B) — each with one distinctly-named product, then asserts:

| Scenario | Request | Expected |
|---|---|---|
| A reads own shop | `GET /api/products` + `X-Shop-Id: A` (A's token) | 200, sees A's product, **not** B's |
| A points header at B | `GET /api/products` + `X-Shop-Id: B` (A's token) | **403** |
| B points header at A | `GET /api/products` + `X-Shop-Id: A` (B's token) | **403** |
| A guesses B's row id | `GET /api/products/{B-id}` + `X-Shop-Id: A` | **404** |
| Shop list scoping | `GET /api/shops` (A's token) | 200, A's shops only |
| Uniform across modules | `GET` of products / customers / payments / debts / devices / expenses / transfers / orders / management / integrations + `X-Shop-Id: B` (A's token) | **403** each |

## Modules covered (and why the rest are covered too)

Directly asserted at the HTTP layer: products, customers, payments, debts,
devices (IMEI), expenses, transfers, orders, management/accounting, integrations
(API keys), shops.

Every other tenant-scoped endpoint group — categories, POS/sales, sales reports,
dashboard, bulk import/export, promos, suppliers, shifts, audit — is protected by
the **same** `TenantFilter` boundary check and the same `@Filter`/`@PostLoad`
layers, because they all extend `TenantScopedEntity` and route through the same
filter. The breadth test samples enough modules to demonstrate the mechanism is
not endpoint-specific.

Exempt-by-design (not tenant-scoped): `/api/auth/**`, `/api/shops` (returns only
the caller's own shops), `/api/health`, `/api/admin/**` (super-admin only).

## Manual evidence checklist (before pilot)

- [ ] Log in as account A; note A's shop id(s) from `GET /api/shops`.
- [ ] With browser dev-tools, replay a `GET /api/products` request but change the
      `X-Shop-Id` header to a shop id you do **not** own → expect **403**.
- [ ] Try `GET /api/products/<some id from B>` while scoped to A → expect **404**.
- [ ] Confirm A's product/customer/debt lists never contain B's rows.
- [ ] Confirm an API key created in shop A cannot read shop B (the key is bound to A).
- [ ] Run `mvnw -pl backend -Dtest=TenantIsolation* test` → all green.
