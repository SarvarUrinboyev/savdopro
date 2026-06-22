# SavdoPRO — Multi-Tenant Cloud POS SaaS

**Live at [savdopro.uz](https://savdopro.uz)**

SavdoPRO is a production multi-tenant cloud point-of-sale (POS) platform for retail shops in Uzbekistan — especially phone/electronics stores. Shop owners and cashiers ring up sales (kassa), manage inventory (ombor) with barcode scanning and national-catalogue (MXIK) auto-fill, track customer debt (qarz), handle per-unit **IMEI/device lifecycle** (intake → sale → verify, with Apple ID capture), run fiscalization, and view dashboards and reports. It ships as a **hosted web portal, an Electron desktop kiosk, and a mobile app**, all on a shared Spring Boot backend, with a **separate licensing/billing microservice**.

> Tenants (shops) are isolated at the database layer; a super-admin operates the SaaS platform itself.

---

## Architecture — five deployable modules

1. **Backend** — Spring Boot 3.3 / Java 21: 324 main Java files (33 controllers, 95 services, 57 JPA entities), layered domain/repository/dto/service/controller.
2. **License server** — a separate Spring Boot service (own DB) for activation, billing (Click/Payme), refresh tokens, and admin audit.
3. **Web portal** — React 18 + Vite SPA (40 pages).
4. **Desktop** — Electron 33 kiosk with auto-update (`electron-updater`), bundling the web SPA.
5. **Mobile** — Flutter (Dart/Riverpod) customer app + a React Native/Expo companion ([savdopro-mobile](https://github.com/SarvarUrinboyev/savdopro-mobile)).

### Database-layer multi-tenancy (done right)
Tenant isolation is enforced **in the persistence layer**, not in business code: a `TenantFilter` resolves shop scope from the JWT, `TenantContext` carries it, and a **fail-closed** `TenantFilterAspect` activates a Hibernate `@Filter` (`WHERE shop_id = :shopId`) on every service call — **refusing to run a query unscoped** rather than risk leaking cross-tenant rows. A consolidated multi-shop mode (`shop_id IN (:shopIds)`) supports owners with several stores.

## Tech Stack

**Java 21** · **Spring Boot 3.3** (Web, Data JPA, Security, WebSocket, AOP, Actuator, Mail) · **PostgreSQL** / H2 (dev) · **Flyway** · **Hibernate `@Filter`** row-level multi-tenancy · **jjwt** · **springdoc-openapi** · **Micrometer + Prometheus** · **Sentry** · Apache POI + OpenPDF (Excel/PDF) · **React 18 + Vite** · **Electron 33** · **Flutter / Dart** (Riverpod, dio, go_router) · **Docker / docker-compose** · **GitHub Actions** · **Click/Payme** + **Telegram Bot** + **Eskiz** SMS.

## At a Glance

| | |
|---|---|
| Code | ~68,500 LOC (Java + JS/JSX + SQL) |
| Backend | 324 Java files · 33 controllers · 95 services · 57 entities (+ 59-file license server) |
| Tests | **307 automated tests** (215 backend + 92 license-server), gated in CI on every push/PR |
| Database | 47 Flyway migrations (34 backend + 13 license) |
| Releases | **35 tagged releases** (v1.4 → v2.3) across 145 conventional commits |
| Public API | API keys + OpenAPI docs + webhooks |
| Notable | AI sales-anomaly detection · IMEI/Apple-ID device tracking · MXIK national-catalogue barcode auto-fill |

## Security

- JWT auth where `JwtService` **refuses to boot** on a weak/placeholder secret unless an explicit dev opt-in flag is set.
- API-key filter for the public API, per-tenant rate limiting, WebSocket auth interceptor, and an admin audit log.
- **All secrets externalized** via `${ENV:default}` — DB, JWT, Telegram, SMS, payment, SMTP — with no real credentials in tracked files; only `*.example` placeholders are committed.

## Operations

docker-compose deploy (backend + license + Postgres), scripted VPS provisioning, Postgres backup cron + restore drill, Prometheus/Micrometer metrics, Sentry, and two GitHub Actions workflows (CI quality gate + deploy).

## License

Proprietary — production SaaS codebase, shared as an engineering work sample.
