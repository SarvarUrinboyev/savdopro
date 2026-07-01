#!/usr/bin/env bash
# =============================================================================
# vps-setup.sh — Barakat SuperMarket / SavdoPRO Spring Boot VPS hardening
# =============================================================================
#
# ⚠️  SUPERSEDED — DO NOT run as-is to (re)provision the CURRENT production box.
#     This legacy script (a) installs a SINGLE `savdopro` unit, but prod now
#     runs TWO units (`savdopro-backend`, `savdopro-license`); (b) runs the app
#     as `User=root`; and (c) — the root cause of the "prod isn't on the prod
#     profile" incident — activates `--spring.profiles.active=local` whenever
#     application-local.properties exists (see step 3 below). That makes the
#     prod box boot under the `local` profile, so application-prod.properties
#     (Swagger-off, Postgres, CORS fail-closed, CSP, HSTS, demo-seed hard-off)
#     never loads. For production use docs/ops/production-hardening-runbook.md
#     instead: profile via SPRING_PROFILES_ACTIVE=prod in a 0600 env file,
#     dedicated service users, and systemd hardening drop-ins (ops/systemd/).
#
# WHAT THIS DOES:
#   1. Auto-detects the barakat-market.jar and its working directory
#   2. Fixes NTP clock drift and sets timezone to Asia/Tashkent
#   3. Installs /etc/systemd/system/savdopro.service with:
#        - Restart=always + RestartSec=10  (service auto-recovers on crash)
#        - MemoryMax=600M                  (prevents OOM kills on small VPS)
#        - JVM flags: -Xmx512m -Xms128m
#        - Structured journal logging
#        - "local" Spring profile if application-local.properties is present
#   4. Kills any stale Java process on port 8080/8443
#   5. Enables + restarts the service via systemd
#   6. Shows live status and recent logs after 15 s
#   7. Sets up logrotate (10 MB, 5 rotations, compressed)
#   8. Installs a belt-and-suspenders cron watchdog (every 5 min)
#
# HOW TO RUN (DigitalOcean web console, root shell):
#   chmod +x vps-setup.sh && bash vps-setup.sh
#
# IDEMPOTENT: safe to run multiple times — existing unit/cron/logrotate files
# are overwritten cleanly each run.
# =============================================================================

set -euo pipefail

# ── colour helpers ─────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[ OK ]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
die()   { echo -e "${RED}[FAIL]${NC}  $*" >&2; exit 1; }

echo ""
echo -e "${CYAN}============================================================${NC}"
echo -e "${CYAN}  Barakat SuperMarket — VPS Spring Boot hardening script    ${NC}"
echo -e "${CYAN}============================================================${NC}"
echo ""

# ── 0. Must be root ───────────────────────────────────────────────────────────
[[ "$EUID" -eq 0 ]] || die "Run as root (sudo su - or DigitalOcean web console)."

# ── 1. Auto-detect JAR ────────────────────────────────────────────────────────
info "Searching for barakat-market.jar ..."
JAR_PATH=$(find / -name "barakat-market.jar" 2>/dev/null | head -1 || true)

if [[ -z "$JAR_PATH" ]]; then
    die "barakat-market.jar not found on this server. Upload it first, then re-run."
fi
ok "Found JAR: $JAR_PATH"

# ── 2. Auto-detect working directory ─────────────────────────────────────────
info "Detecting working directory (application.properties location) ..."
WORK_DIR=$(dirname "$JAR_PATH")

# Walk up to find application.properties if not in the same dir as the JAR
PROPS_PATH=$(find / -name "application.properties" 2>/dev/null | head -1 || true)
if [[ -n "$PROPS_PATH" ]]; then
    CANDIDATE_DIR=$(dirname "$PROPS_PATH")
    # Prefer the dir that also contains the JAR; otherwise use props location
    if [[ -f "$CANDIDATE_DIR/barakat-market.jar" ]]; then
        WORK_DIR="$CANDIDATE_DIR"
    else
        # Use the JAR dir but note where props are
        warn "application.properties found at $PROPS_PATH (different from JAR dir)."
        warn "Using JAR dir as WorkingDirectory: $WORK_DIR"
    fi
fi
ok "Working directory: $WORK_DIR"

# ── 3. Detect Spring profile ──────────────────────────────────────────────────
SPRING_PROFILE_FLAG=""
if [[ -f "$WORK_DIR/application-local.properties" ]]; then
    SPRING_PROFILE_FLAG="--spring.profiles.active=local"
    info "application-local.properties detected → activating Spring profile 'local'."
else
    info "No application-local.properties found → using default Spring profile."
fi

# ── 4. Fix NTP / timezone ─────────────────────────────────────────────────────
info "Fixing NTP and timezone ..."
timedatectl set-ntp true
timedatectl set-timezone Asia/Tashkent
ok "Timezone set to Asia/Tashkent; NTP enabled."
timedatectl status | grep -E "(Local time|Time zone|NTP)"

# ── 5. Kill stale Java processes on port 8080 / 8443 ─────────────────────────
info "Stopping any Java process on port 8080 or 8443 ..."
for PORT in 8080 8443; do
    PIDS=$(fuser "${PORT}/tcp" 2>/dev/null || true)
    if [[ -n "$PIDS" ]]; then
        warn "Killing PID(s) $PIDS on port $PORT ..."
        kill -9 $PIDS 2>/dev/null || true
        sleep 1
    fi
done

# Also stop via systemd if old unit existed
if systemctl is-active --quiet savdopro 2>/dev/null; then
    info "Stopping existing savdopro service ..."
    systemctl stop savdopro || true
fi
ok "Port 8080/8443 cleared."

# ── 6. Write systemd unit ─────────────────────────────────────────────────────
info "Writing /etc/systemd/system/savdopro.service ..."

cat > /etc/systemd/system/savdopro.service <<EOF
[Unit]
Description=Barakat SuperMarket — Spring Boot application (savdopro)
After=network-online.target syslog.target
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=${WORK_DIR}
ExecStart=/usr/bin/java \\
    -Xmx512m -Xms256m \\
    -XX:+UseG1GC \\
    -XX:InitiatingHeapOccupancyPercent=40 \\
    -XX:+HeapDumpOnOutOfMemoryError \\
    -XX:HeapDumpPath=/var/log/savdopro-heapdump.hprof \\
    -jar ${JAR_PATH} \\
    ${SPRING_PROFILE_FLAG}

# ── Restart behaviour ─────────────────────────────────────────────────
Restart=always
RestartSec=10
StartLimitInterval=120
StartLimitBurst=5

# ── Resource limits (prevent OOM on small VPS) ────────────────────────
MemoryMax=600M
MemorySwapMax=0

# ── Logging ───────────────────────────────────────────────────────────
StandardOutput=journal
StandardError=journal
SyslogIdentifier=savdopro

# ── Security hardening (minimal) ──────────────────────────────────────
NoNewPrivileges=true

[Install]
WantedBy=multi-user.target
EOF

ok "systemd unit written."

# ── 7. Reload systemd, enable and start ──────────────────────────────────────
info "Reloading systemd daemon ..."
systemctl daemon-reload
ok "Daemon reloaded."

info "Enabling savdopro to start on boot ..."
systemctl enable savdopro
ok "Service enabled."

info "Starting savdopro ..."
systemctl start savdopro
ok "Service start command issued."

# ── 8. Wait and show status ───────────────────────────────────────────────────
info "Waiting 15 seconds for JVM to initialise ..."
sleep 15

echo ""
echo -e "${CYAN}── systemctl status ────────────────────────────────────────${NC}"
systemctl status savdopro --no-pager || true

echo ""
echo -e "${CYAN}── Last 20 log lines ───────────────────────────────────────${NC}"
journalctl -u savdopro -n 20 --no-pager || true

# ── 9. logrotate ─────────────────────────────────────────────────────────────
info "Configuring logrotate for savdopro ..."

# Journal logs are managed by systemd; set up rotation of any flat-file logs
# the app might write to /var/log/savdopro*.log
mkdir -p /var/log/savdopro

cat > /etc/logrotate.d/savdopro <<'EOF'
/var/log/savdopro/*.log {
    daily
    size 10M
    rotate 5
    compress
    delaycompress
    missingok
    notifempty
    copytruncate
    su root root
}
EOF

ok "logrotate configured (/etc/logrotate.d/savdopro)."

# ── 10. Cron watchdog (belt-and-suspenders) ───────────────────────────────────
info "Installing cron watchdog (/etc/cron.d/savdopro-watchdog) ..."

cat > /etc/cron.d/savdopro-watchdog <<'EOF'
# savdopro-watchdog: check every 5 min; restart service if not running
# Belt-and-suspenders on top of systemd Restart=always
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin

*/5 * * * * root \
    systemctl is-active --quiet savdopro \
    || (echo "$(date '+%%Y-%%m-%%d %%H:%%M:%%S') savdopro was down — restarting" \
        >> /var/log/savdopro-watchdog.log 2>&1 \
        && systemctl restart savdopro >> /var/log/savdopro-watchdog.log 2>&1)
EOF

chmod 644 /etc/cron.d/savdopro-watchdog
ok "Watchdog cron installed."

# ── 11. Quick connectivity check ─────────────────────────────────────────────
echo ""
info "Checking if port 8080 is listening (give it another 5 s) ..."
sleep 5
if ss -tlnp 2>/dev/null | grep -q ':8080'; then
    ok "Port 8080 is OPEN — Spring Boot is listening."
else
    warn "Port 8080 is not yet open. The JVM may still be starting up."
    warn "Run:  journalctl -u savdopro -f   to follow the logs live."
fi

# ── Done ──────────────────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}============================================================${NC}"
echo -e "${GREEN}  Setup complete.                                           ${NC}"
echo -e "${GREEN}                                                            ${NC}"
echo -e "${GREEN}  Useful commands:                                          ${NC}"
echo -e "${GREEN}    journalctl -u savdopro -f          # follow live logs   ${NC}"
echo -e "${GREEN}    systemctl status savdopro          # service status     ${NC}"
echo -e "${GREEN}    systemctl restart savdopro         # manual restart     ${NC}"
echo -e "${GREEN}    cat /var/log/savdopro-watchdog.log # watchdog history   ${NC}"
echo -e "${GREEN}============================================================${NC}"
echo ""
