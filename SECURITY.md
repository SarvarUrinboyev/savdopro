# Security policy

## Supported code

Security reports should target the current default branch.

## Reporting

Do not open a public issue for a suspected vulnerability or exposed secret.
Report it privately through the contact options on the repository owner's
GitHub profile.

Include the affected component, reproduction steps, impact, and a suggested
remediation when possible. Do not access or retain real customer data while
testing.

## Public-repository policy

- Production credentials and local configuration files must never be committed.
- Only `*.example` configuration with non-secret placeholders may be tracked.
- Customer, tenant, payment, and operational data must not appear in fixtures,
  screenshots, logs, or documentation.
- Security fixes may be disclosed after remediation.
