# Monitoring runbook — Prometheus + Grafana + Telegram alerting

Maqsad: droplet'da nima bo'layotganini KO'RISH (metrics dashboard) va muammo
bo'lganda darhol Telegram'da XABAR olish. Stack: `ops/monitoring/` dagi
docker-compose (Prometheus + Grafana, ixtiyoriy Uptime Kuma).

Ikkala servis ham `/actuator/prometheus` ni allaqachon chiqaradi; scrape
uchun statik token filtri qo'shilgan (`MetricsScrapeTokenFilter`) — token
o'rnatilmaguncha endpoint avvalgidek faqat JWT bilan ochiladi.

## 1. Scrape tokenini yoqish (app tomonida)

```bash
# Droplet'da:
TOKEN=$(openssl rand -hex 32)

# /etc/savdopro/backend.env va /etc/savdopro/license.env ga qo'shing:
#   METRICS_SCRAPE_TOKEN=<TOKEN>
# (ikkalasida BIR XIL qiymat bo'lsin — bitta scraper ikkalasini o'qiydi)

systemctl restart savdopro-backend savdopro-license

# Tekshirish (401/403 = token noto'g'ri; 200 + matnli metrikalar = OK):
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8086/actuator/prometheus | head -3
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:9090/actuator/prometheus | head -3
```

> Eslatma: agar systemd o'rniga docker-compose bilan ishlayotgan bo'lsangiz,
> `METRICS_SCRAPE_TOKEN` ni `.env` ga qo'shib ikkala servisning
> `environment:` bo'limiga uzating.

## 2. Monitoring stackni ko'tarish

```bash
cd /opt/barakat/app/ops/monitoring   # repo qayerda bo'lsa
cp .env.example .env                 # GF_ADMIN_PASSWORD + Telegram to'ldiring
mkdir -p secrets
printf '%s' "$TOKEN" > secrets/scrape-token
chmod 600 secrets/scrape-token

docker compose -f docker-compose.monitoring.yml up -d
docker compose -f docker-compose.monitoring.yml ps
```

UI'lar ataylab 127.0.0.1 ga bog'langan (firewall 22/80/443 dan boshqasini
yopadi). Laptopdan tunnel bilan ochiladi:

```bash
ssh -L 3001:localhost:3001 -L 9091:localhost:9091 root@167.172.164.214
# Grafana:    http://localhost:3001   (admin / GF_ADMIN_PASSWORD)
# Prometheus: http://localhost:9091   (Status → Targets: ikkala job UP bo'lsin)
```

## 3. Nima provision qilingan

| Narsa | Qayerda |
|---|---|
| Datasource (Prometheus) | `grafana/provisioning/datasources/prometheus.yml` |
| "SavdoPRO Overview" dashboard | `grafana/dashboards/savdopro-overview.json` — up/down, req/s, 5xx %, p95, heap %, Hikari pool, error log, CPU, uptime |
| Telegram contact point | `grafana/provisioning/alerting/contact-points.yml` (env: `TELEGRAM_BOT_TOKEN`, `TELEGRAM_ALERT_CHAT_ID`) |
| 3 ta alert qoidasi | `rules.yml`: Service down (2 daq), 5xx > 5% (5 daq), heap > 90% (5 daq) |

Chat ID topish: botga bitta xabar yuboring, so'ng
`https://api.telegram.org/bot<TOKEN>/getUpdates` — javobdagi `chat.id`.
(Bu HARDENING.md §4.2 dagi "chat not found" muammosining ham davosi —
watch.sh dagi eski chat id ni shu qiymat bilan almashtiring.)

Alert testi: Grafana → Alerting → Contact points → telegram-owner → **Test**.
Yoki backend'ni 3 daqiqaga to'xtating (`systemctl stop savdopro-backend`) —
"Service down" kelishi kerak; qayta yoqing.

## 4. p95 latency paneli haqida

`management.metrics.distribution.percentiles-histogram.http.server.requests=true`
ikkala servisga qo'shilgan — p95/p99 histogram bucketlari shu deploydan
keyingina paydo bo'ladi. Eski buildda p95 paneli bo'sh ko'rinadi, bu normal.

## 5. Uptime Kuma (ixtiyoriy, tashqi-ko'rinish probe)

Prometheus ichki metrikani ko'radi; Kuma esa tashqaridan (nginx orqali)
HTTPS probe qiladi — sertifikat muddati va nginx muammolarini ham ushlaydi:

```bash
docker compose -f docker-compose.monitoring.yml --profile kuma up -d
# Tunnel: ssh -L 3002:localhost:3002 ...  →  http://localhost:3002
# Monitor qo'shing: https://167-172-164-214.nip.io/actuator/health (60s)
# Notification: Telegram (o'sha bot token + chat id)
```

## 6. Sentry (xatolar uchun — alohida, 1 daqiqalik ish)

Kod allaqachon tayyor (`sentry.dsn=${SENTRY_DSN:}` ikkala servisda).
sentry.io da bepul akkaunt → DSN oling → `/etc/savdopro/backend.env` va
`license.env` ga `SENTRY_DSN=...` qo'shing → restart. Bo'ldi.

## 7. Diskka e'tibor

Prometheus 30 kunlik retention bilan ~200–500 MB ishlatadi (bu stackda
scrape hajmi kichik). 1 GB droplet'da joy tor — monitoringni **serverni
2–4 GB ga kengaytirgandan keyin** yoqish tavsiya etiladi
(`migration-handoff/` paketiga qarang).
