# Backend Workspace

This directory contains the replacement Java backend and its supporting
artifacts. The existing uni-app frontend remains at the repository root.

- `starfree-replacement/`: Spring Boot source, tests, local configuration, and
  Maven build output.
- `database/`: local database snapshots and future migration work.
- `deploy/production/`: systemd, Nginx, deployment, route verification, and
  rollback scripts.
- `docs/`: compatibility findings, current migration status, and operational
  decisions.
- `scripts/`: local startup and disposable integration tests.
- `reference/`: downloaded legacy artifacts retained for comparison.
- `.local/`: ignored tools, secrets, runtime files, decompilation output, and
  downloaded production snapshots. Do not deploy or commit this directory.

Current implementation and production route status are recorded in
`docs/REBUILD_STATUS.md`; production procedures are in
`deploy/production/README.md`.
