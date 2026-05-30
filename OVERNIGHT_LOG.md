# Work log — `feat/web-multitenant-hardening`

**Base:** `main` (`700fc3b`) · **Date:** 2026-05-30 · **Status:** not merged, no PR.

Hardens the backend for a hosted, multi-tenant **web merchant portal** and adapts
the existing React SPA into that portal. Every change targets the web deployment;
the desktop/Electron build keeps working (embedded H2, localhost license server,
kiosk conveniences).

## Test status (real numbers)

| Module | Command | Result |
|---|---|---|
| backend | `./mvnw -o test` | **107 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS** |
| license-server | `./mvnw -o test` | **62 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS** |
| frontend | `npm run build` | builds clean (no automated test suite exists) |

No `@Disabled`/`@Ignore`, no stubs/TODO in new code. One existing test file
(`PermissionServiceTest`) was intentionally updated to match the new — broader,
not weaker — permission matrix.

## Commits, grouped by theme (oldest → newest)

**Tenancy / data isolation & WebSocket security**
- `5660227` feat(tenancy): X-Shop-Id ownership check + fail-closed tenant aspect + native sales-queries scoped by shop
- `0ce181b` feat(realtime): STOMP CONNECT authenticated + per-shop topics + SUBSCRIBE authorized

**Authorization (RBAC)**
- `6bc98de` feat(authz): License Server mints `perms` into the JWT; backend enforces RESOURCE:ACTION per endpoint via `SecurityConfig` + wildcard `PermissionChecker`
- `e13d69e` fix(authz): owner-only `SHIFTS:ADMIN` to clear shift history (was `SHIFTS:WRITE` — a cashier could erase cash-discrepancy evidence)

**Correctness**
- `ec0cbcb` fix(backend): payments-timeline sort, `orderStatus` NPE, unified loyalty rate (USD fix)
- `99e33e3` feat(reports): value historical sales at the sale-time price snapshot (V23)

**Validation & data constraints**
- `01bbe95` fix(backend): tighten input validation; `DataIntegrityViolation` → 409
- `da8a23a` fix(db): widen stale global UNIQUE to per-shop (V22)

**PostgreSQL** *(standalone — independently reviewable/revertable)*
- `24aa7a3` feat(db): PostgreSQL driver + `prod` profile datasource (desktop stays H2)

**Web portal adaptation**
- `d06392c` feat(web): env-driven origins, WS-auth client, 403-handling, route guards, mobile nav, no plaintext-password persistence

**Tests & docs**
- `a9651a3` test(backend): context-load smoke test + test profile
- `e579e4f` test(tenancy): native report queries exclude other shops' rows *(gap c)*
- `fea46ab` test(authz): end-to-end MockMvc 403/200 *(gap d)*
- `ed92d08` test(tenancy): tenant-filter aspect fails closed *(gap b)*
- `2b82922` test(authz): bare-base paths require their permission (no fall-through)
- `8bd2626` + this refresh — branch work log

> History note: an earlier bundled commit (`f681623`) was split into the four
> atomic commits `ec0cbcb` / `01bbe95` / `da8a23a` / `24aa7a3` so the PostgreSQL
> datasource change stands alone. Verified byte-identical to the pre-split tree.

## Phase-1 security fixes — each proven by a test

| Fix | Proving test | Asserts |
|---|---|---|
| X-Shop-Id ownership | `TenantFilterTest` | foreign shop → 403 + chain never runs; owned → scope set, 200 |
| Fail-closed tenant filter | `TenantFilterAspectTest` | activation failure → rethrow, `proceed()` never called |
| Native query shop scoping | `StockMovementRepositoryTenantScopeTest` | shop A queries exclude shop B's rows (all 3 native queries) |
| Per-endpoint authorization | `AuthorizationEndpointTest` | real endpoint → 401/403/200 by permission; bare-base paths covered (not fall-through) |

## Operational notes (before running the web build)

- **Re-login required after deploy.** Old tokens lack the `perms` claim → backend denies until a fresh login/refresh (≤1h). Deploy the License Server too (it mints `perms`); both share `SAVDOPRO_JWT_SECRET`.
- **Web build:** `VITE_TARGET=web VITE_LICENSE_URL=https://… [VITE_API_URL=…] npm run build` (see `frontend/.env.example`).
- **Postgres (prod):** `SPRING_PROFILES_ACTIVE=prod` + `DB_URL`/`DB_USER`/`DB_PASSWORD` + `SAVDOPRO_JWT_SECRET` + `WEB_ALLOWED_ORIGINS`.

## Still open (optional — to be done with the web-portal work, on a fresh branch)

- Pagination on high-growth lists (sales history, customer transactions).
- Web polish: hide in-page hardware UI (printer picker, cash-drawer, `window.print`, scale/voice) when `IS_WEB`.
- A direct `PaymentService` loyalty unit test.
