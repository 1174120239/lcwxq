## Change

Describe the user-visible behavior and the modules changed.

## Risk

- [ ] Authentication, ownership, and staff permissions were reviewed.
- [ ] Review state, duplicate requests, cache invalidation, and audit effects were reviewed.
- [ ] Points, payment, VIP, or withdrawal behavior is unchanged or has idempotency tests.
- [ ] No password, token, private key, production config, database snapshot, or build output is included.

## Verification

- [ ] `./workflow.cmd check all` passes locally.
- [ ] Relevant manual frontend flow was checked in HBuilderX when UI files changed.
- [ ] API and project documentation was updated when a contract or behavior changed.
- [ ] CI passes before merge.

## Release

- [ ] This change can be released independently.
- [ ] Database migration is not required, or it has a separate reviewed maintenance plan.
- [ ] Nginx route changes are not required, or they have a separate backup and rollback plan.
- [ ] Production release will use the exact merged `origin/main` commit.
