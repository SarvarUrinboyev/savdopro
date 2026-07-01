# GitHub Actions — Secrets & Variables

The repo ships `.github/workflows/deploy.yml`. It builds the frontend → backend
jar (frontend bundled in) → license jar, uploads over SSH, verifies SHA256,
swaps the jars, and restarts **license then backend**. It does **NOT** manage
systemd units, env files, nginx, or the database — those are one-time manual
setup (this handoff). CI only swaps jars, so your systemd/env/nginx changes
persist across future deploys.

## Configure in the NEW repo/server: Settings → Secrets and variables → Actions

### Secrets (required)
| Name | Value | Notes |
|---|---|---|
| `DEPLOY_SSH_KEY` | private SSH key authorised on the NEW droplet | **Do not paste this key anywhere else.** Generate a deploy-only keypair; put the public half in the server's `~/.ssh/authorized_keys`. |
| `DEPLOY_HOST` | new server IP / host | e.g. `NEW.IP.ADDR` |
| `DEPLOY_USER` | ssh user with rights to `/opt/*` + systemctl | `root` on the current setup |

### Variables (optional, with defaults)
| Name | Value |
|---|---|
| `LICENSE_URL` | `https://__NEW_PUBLIC_HOST__` (baked into the web build as `VITE_LICENSE_URL`) |

## Trigger
- **Automatic:** push a version tag `v*` (e.g. `git tag -a v2.3.5 -m ... && git push origin v2.3.5`).
- **Manual:** Actions → **Deploy** → *Run workflow*.

> Deploying causes ~60s of downtime (jar swap + restart). Do it in an off-hours
> window. The deploy expects the paths `/opt/barakat/barakat-market.jar` and
> `/opt/savdopro/savdopro-license-server.jar` to already exist (first jars can be
> placed manually — see README §7/§8).

## Do NOT
- Do not commit the SSH private key or any real secret to the repo.
- Do not point CI at the OLD server once you cut over.
