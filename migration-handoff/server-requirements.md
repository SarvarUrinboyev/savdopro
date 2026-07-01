# Server Requirements (new host)

## OS
- **Ubuntu 24.04 LTS** (the current prod runs 24.04.3 LTS)

## Runtime / packages
| Component | Version | Install |
|---|---|---|
| Java (JDK) | **21** (Temurin/OpenJDK; prod runs 21.0.11) | `apt install openjdk-21-jdk` |
| PostgreSQL | **16** (distro build is fine; prod runs 16.14) | `apt install postgresql` |
| nginx | current stable (prod runs 1.24.0) | `apt install nginx` |
| certbot | for Let's Encrypt HTTPS | `apt install certbot python3-certbot-nginx` |
| ufw | firewall | `apt install ufw` |
| unzip, curl | helpers | `apt install unzip curl` |

## Hardware
| Resource | Minimum | Recommended |
|---|---|---|
| RAM | **2 GB** | **4 GB** (avoids the tight-memory OOM tuning the old 1 GB box needed) |
| Disk | **40 GB** SSD | 40–80 GB |
| vCPU | 1 | 2 |

> The current prod is a 1 GB droplet with aggressive JVM caps
> (`-Xmx320m` backend, `-Xmx200m` license, `MemoryMax` set). The systemd
> templates in this package now default to the 2 GB sizing below.

### JVM / pool sizing per box (keep systemd units + env in sync)

| Box | Backend `-Xmx` | License `-Xmx` | Backend `MemoryMax` | License `MemoryMax` | `DB_POOL_MAX` | GC |
|---|---|---|---|---|---|---|
| 1 GB (legacy) | 320m | 200m | 420M | 280M | 5 | SerialGC |
| **2 GB (default)** | **512m** | **256m** | **768M** | **384M** | **10** | G1GC |
| 4 GB | 1024m | 384m | 1280M | 512M | 15–20 | G1GC |

Budget check for 2 GB: backend 768M + license 384M + Postgres (~300–400M
default) + nginx/OS (~200M) + monitoring stack agar yoqilsa (~300M) ≈ fits,
no swap thrash. On 4 GB there is room for Prometheus+Grafana comfortably.

`DB_POOL_MAX` (Hikari `maximum-pool-size`) is env-tunable in `backend.env` /
`license.env`; Postgres default `max_connections=100` — backend 15 + license 5
+ psql sessions fits easily.

## Network / firewall
- Public ports: **22, 80, 443 ONLY**.
```bash
ufw default deny incoming
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp
ufw enable
```
- Backend (8086) and license (9090) MUST stay **127.0.0.1 only** (set
  `SERVER_ADDRESS=127.0.0.1` in both env files). nginx is the sole ingress.
- SSH: key-only auth recommended (disable password login).

## Directory layout (matches current prod)
```
/opt/barakat/barakat-market.jar            # backend jar (+ update.json, optional)
/opt/savdopro/savdopro-license-server.jar  # license jar
/opt/savdopro/license-data.mv.db           # license H2 file (restored)
/etc/savdopro/backend.env                  # 0600, root:root
/etc/savdopro/license.env                  # 0600, root:root
/etc/systemd/system/savdopro-backend.service
/etc/systemd/system/savdopro-license.service
/etc/nginx/sites-available/savdopro        # symlinked into sites-enabled/
```
