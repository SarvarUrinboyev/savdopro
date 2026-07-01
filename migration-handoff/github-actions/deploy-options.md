# Deploy Options

## Recommended order for a QUICK migration
1. Provision the new server (packages, users, dirs) — README §2–4.
2. Restore PostgreSQL + place the license H2 file — database/restore-commands.md.
3. **Manual jar deploy** for the first cut (fastest, no CI wiring needed).
4. systemd + nginx + verify.
5. Wire GitHub Actions secrets **afterwards** for future one-command deploys.

## Option A — Manual jar deploy (use this first)
```bash
mkdir -p /opt/barakat /opt/savdopro
# copy the two jars from the OLD server (already built + verified there):
scp root@OLD_HOST:/opt/barakat/barakat-market.jar            /opt/barakat/
scp root@OLD_HOST:/opt/savdopro/savdopro-license-server.jar  /opt/savdopro/
# (optional) the electron update manifest:
scp root@OLD_HOST:/opt/barakat/update.json /opt/barakat/ 2>/dev/null || true

systemctl daemon-reload
systemctl enable --now savdopro-license
systemctl enable --now savdopro-backend
```
Building from source instead (needs Java 21 + Node 20):
```bash
cd frontend && VITE_TARGET=web VITE_LICENSE_URL=https://__NEW_HOST__ npm ci && npm run build
cd ../backend && ./mvnw -B -DskipTests clean package            # bundles the frontend
cd ../license-server && ./mvnw -B -DskipTests clean package
# jars land in backend/target/*.jar and license-server/target/*.jar
```

## Option B — GitHub Actions (after secrets are set)
- Set the 3 secrets + `LICENSE_URL` var (github-actions-secrets.md).
- Push a `v*` tag or run the **Deploy** workflow manually.
- CI rebuilds + swaps the jars and restarts the services (~60s downtime).

## Notes
- The frontend is bundled INTO the backend jar; there is no separate web server.
- License starts before backend (shared JWT; backend `After=` license).
