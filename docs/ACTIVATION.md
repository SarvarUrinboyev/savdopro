# Activation guide — flipping SavdoPRO from "ready" to "live"

Everything below is **built and deployed but config-gated**: the code runs
identically until you supply the credential, then the feature turns on. Set the
keys in `/opt/barakat/application-local.properties` on the droplet (never commit
them) and `systemctl restart savdopro-backend`. Some need an external account or
certification — noted per item.

## 1. Database — PostgreSQL ✅ (done 2026-06-02)
The backend now runs on PostgreSQL 16 (db `barakat`, role `barakat`). The H2
file is kept untouched as an instant rollback.
- Rollback: restore `/opt/barakat/application-local.properties.bak-h2-*` and
  `systemctl restart savdopro-backend`.
- The **license-server** is still on H2 — migrate it with the same method while
  watching (it is login-critical). See `memory/prod-deploy.md`.

## 2. Backups ✅ (done)
`/opt/barakat/backup.sh` runs daily at 03:30 (cron): gzipped `pg_dump` + the
license H2 file, 14-day rotation, Telegram alert on failure. Restore with
`/opt/barakat/restore-pg.sh <dump.sql.gz>`.

## 3. Monitoring
- **Uptime watchdog** ✅ — `/opt/barakat/watch.sh` every 3 min → Telegram alert
  on backend/license/Postgres/HTTPS state change. No account needed.
- **Sentry** — set `SENTRY_DSN=<your dsn>` (sentry.io free tier) to capture
  backend errors. Already wired; just add the DSN + restart.
- **UptimeRobot** (optional external) — add an HTTP monitor on
  `https://167-172-164-214.nip.io/actuator/health` for off-box alerting.

## 4. CI/CD auto-deploy
`.github/workflows/deploy.yml` builds + deploys on a `v*` tag or manual run.
Add these **GitHub repo secrets** (Settings → Secrets → Actions):
- `DEPLOY_SSH_KEY` — a private key whose public key is in the droplet's
  `~/.ssh/authorized_keys`
- `DEPLOY_HOST` = `167.172.164.214`
- `DEPLOY_USER` = `root`
Then push a tag (`git tag v1.0 && git push --tags`) or run the workflow.

## 5. SMS (Eskiz) — customer comms, debt reminders, receipts
Sign up at notify.eskiz.uz, then set:
```
sms.enabled=true
sms.email=<eskiz login email>
sms.password=<eskiz password>
```

## 6. Customer Telegram bot — free customer comms (preferred over SMS)
Create a SECOND bot with @BotFather, then set:
```
telegram.customer-bot.enabled=true
telegram.customer-bot.token=<bot token>
```
Customers /start it and share their phone to link; then debt reminders and
receipts reach them on Telegram for free.

## 7. Click / Payme — online debt payment AND subscription billing
Open merchant accounts with each PSP, then set (backend):
```
payment.payme.enabled=true
payment.payme.merchant-id=<...>
payment.payme.key=<cashbox key>
payment.click.enabled=true
payment.click.service-id=<...>
payment.click.merchant-id=<...>
payment.click.secret-key=<...>
payment.click.merchant-user-id=<...>
```
**IMPORTANT:** real money requires passing each PSP's **sandbox certification**
before they enable production. The webhooks (`/api/pay/payme`,
`/api/pay/click/*`) follow the documented protocol but have not been certified.
Give the PSP these webhook URLs during onboarding.

## 8. Transactional email (SMTP)
For invoices / notifications. Set:
```
app.email.enabled=true
app.email.from=no-reply@<your domain>
spring.mail.host=<smtp host>
spring.mail.username=<...>
spring.mail.password=<...>
```

## 9. Fiscalization (OFD) — MANDATORY in Uzbekistan ⚠️
Every retail sale must be registered with the tax authority via a licensed OFD
(soliq.uz / Didox / iiko-fiscal / ...). Two steps:
1. Sign a contract with an OFD and register the cash register (get api url/key,
   terminal/ZNM id).
2. Implement the provider branch in `FiscalizationService.callProvider` (each
   OFD has its own REST format + signing), then wire `fiscalize()` into the POS
   checkout and print the returned fiscal sign + QR on the receipt.
Config:
```
fiscal.enabled=true
fiscal.provider=<didox|soliq|...>
fiscal.api-url=<...>
fiscal.api-key=<...>
fiscal.terminal-id=<...>
```
This is the single biggest legal prerequisite for selling to real Uzbek shops.
