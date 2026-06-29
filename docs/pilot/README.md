# Pilot Readiness Runbooks

Everything needed to stand up, demo, validate and operate a SavdoPRO pilot.

| Doc | Use it to |
|---|---|
| [STAGING_SETUP.md](STAGING_SETUP.md) | Boot the services, set env vars, run the seed, verify the box isn't on prod data |
| [SEED_DATA.md](SEED_DATA.md) | Understand the guarded demo/staging seed (what, where, how, safety) |
| [POS_E2E_CHECKLIST.md](POS_E2E_CHECKLIST.md) | Manually walk the cashier → stock → payment → report → accounting flow |
| [ACCOUNTING_RULES.md](ACCOUNTING_RULES.md) | The revenue/COGS/profit/cash/debt rules and the currency limitation |
| [TENANT_ISOLATION_TESTS.md](TENANT_ISOLATION_TESTS.md) | How multi-tenant isolation is enforced and proven (A/B procedure) |
| [BAROKAT_PILOT_CHECKLIST.md](BAROKAT_PILOT_CHECKLIST.md) | The operational go/no-go checklist for the real-store pilot |
| [QA_ACCEPTANCE.md](QA_ACCEPTANCE.md) | What must pass, known limitations, and the P0–P3 backlog |

Start with **STAGING_SETUP**, then demo with **POS_E2E_CHECKLIST**, then gate on
**QA_ACCEPTANCE** before letting a real store rely on it.
