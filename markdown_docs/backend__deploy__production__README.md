# Production Side-by-Side Deployment

This deployment keeps the closed-source API on `127.0.0.1:8081` and starts the
replacement API on `127.0.0.1:18082`.

The replacement service reads the existing database username and password from
`/opt/application.properties` at process startup. Secrets are not copied into
the deployment directory or systemd unit.

The Nginx rollout contains only exact gray routes. Public `contentsList`,
`commentsList`, `metasList`, `selectContents`, `adsConfig`, and anonymous
`adsList` use port 18082. `SFreeSpace/spaceList` and
`SFreeSpace/spaceInfo` use port 18082 for both anonymous and token-bearing
requests after the Redis-only session bridge smoke test passed. The isolated
`SFreeSpace/spaceLikes` write uses port 18082 after two-way old/new compatibility
and public disposable-data tests. `SFreeSpace/followSpace` and the frontend's
`SFreeSpace/myFollowSpace` alias also use port 18082, with private/reply leakage
removed. `SFreeSpace/addSpace` uses port 18082 after shared Redis quota and
public disposable-data tests. `editSpace`, `spaceReview`, `spaceLock`, and
`spaceDelete` each have an independent exact route and rollback boundary after
direct legacy/new, Redis-only-session, and public disposable-data tests.
`SFreeContents/contentsInfo` also uses port 18082 for anonymous and token-bearing
requests after exact raw-payload and shared read-key tests. Ordinary
`SFreeContents/contentsAdd` post/video publishing uses port 18082 after direct
row, relationship, Redis quota, audit, and public-route tests. Paid content,
drafts, linked Space posts, attached shop rows, and unknown content types are
delegated by the replacement to port 8081 with `X-Starfree-Delegate` auditing.
Ordinary `SFreeContents/contentsUpdate` post/video edits also use port 18082.
They preserve the existing type and Markdown mode, restore frontend newline
placeholders, pre-validate relationships, and invalidate shared legacy detail
and page-one caches. Paid, draft, shop-linked, and unsupported edits are
delegated to port 8081.
The wallet economy is also active on port 18082. Exact routes cover rewards,
daily clock, seven-day sign-in, manual asset/point adjustments, withdrawals,
wallet and finance reads, shop purchases, VIP purchases, and advertising
purchases/renewals. Advertising reward start, client verification, and signed
server callbacks are implemented by the replacement with owner checks, daily
limits, global `trans_id` idempotency, and MyISAM compensation. Official payment
creation, card redemption, and payment callbacks are still executed by port
8081; their exact routes pass through port 18082 first so the same global MySQL
economy lock is held while the legacy bytes are forwarded. Token-bearing
`contentsList`, unmigrated advertising management, comment/meta routes, upload,
chat, plugins, and every other unmigrated endpoint stay on port 8081 through the
existing `location ^~ /` legacy proxy. `SFreeUsers/userRegister` is an exact
replacement route with PHPass-compatible passwords, durable replay, invite
consumption, and optional fixed invite rebates. `RegSendCode` remains on port
8081, so existing mail delivery and templates are unchanged. `regConfig`,
`userFoget`, `userEdit`, and `setClientId` are also exact replacement routes.
Email/SMS delivery remains on port 8081; account maintenance consumes its
serialized Redis codes and revokes both MySQL and Redis-only login sessions.

## Server Files

- `/opt/starfree-replacement/starfree-replacement.jar`
- `/opt/starfree-replacement/start.sh`
- `/opt/starfree-replacement/verify-redis-session.sh`
- `/opt/starfree-replacement/verify-space-cutover.sh`
- `/opt/starfree-replacement/verify-space-like.sh`
- `/opt/starfree-replacement/verify-space-follow.sh`
- `/opt/starfree-replacement/verify-space-add.sh`
- `/opt/starfree-replacement/verify-space-edit.sh`
- `/opt/starfree-replacement/verify-space-review.sh`
- `/opt/starfree-replacement/verify-space-lock.sh`
- `/opt/starfree-replacement/verify-space-delete.sh`
- `/opt/starfree-replacement/verify-contents-info.sh`
- `/opt/starfree-replacement/verify-contents-info-cutover.sh`
- `/opt/starfree-replacement/verify-contents-add.sh`
- `/opt/starfree-replacement/verify-contents-add-cutover.sh`
- `/opt/starfree-replacement/verify-contents-update.sh`
- `/opt/starfree-replacement/verify-contents-update-cutover.sh`
- `/opt/starfree-replacement/001_economy_operation_journal.sql`
- `/opt/starfree-replacement/apply-economy-migration.sh`
- `/opt/starfree-replacement/verify-economy.sh`
- `/opt/starfree-replacement/verify-ads-reward.sh`
- `/opt/starfree-replacement/verify-user-registration.sh`
- `/opt/starfree-replacement/verify-account-maintenance.sh`
- `/opt/starfree-replacement/cutover-economy-route.sh`
- `/opt/starfree-replacement/promote-ads-reward-routes.sh`
- `/opt/starfree-replacement/promote-user-registration-route.sh`
- `/opt/starfree-replacement/promote-account-maintenance-routes.sh`
- `/opt/starfree-replacement/cutover-space-token-read.sh`
- `/opt/starfree-replacement/cutover-space-like.sh`
- `/opt/starfree-replacement/cutover-space-follow.sh`
- `/opt/starfree-replacement/cutover-space-add.sh`
- `/opt/starfree-replacement/cutover-space-edit.sh`
- `/opt/starfree-replacement/cutover-space-review.sh`
- `/opt/starfree-replacement/cutover-space-lock.sh`
- `/opt/starfree-replacement/cutover-space-delete.sh`
- `/opt/starfree-replacement/cutover-contents-info.sh`
- `/opt/starfree-replacement/cutover-contents-add.sh`
- `/opt/starfree-replacement/cutover-contents-update.sh`
- `/etc/systemd/system/starfree-replacement.service`
- `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf`

Upload a candidate as `starfree-replacement.jar.new`, then deploy it with the
expected SHA-256. The script keeps a timestamped rollback JAR and restores it
automatically if the replacement health check does not recover within 40
seconds.

```bash
bash deploy-jar.sh <expected-sha256>
```

## Health Checks

```bash
curl -fsS http://127.0.0.1:18082/health
curl -fsS http://127.0.0.1:18082/SFreeUsers/regConfig
curl -fsS http://127.0.0.1:18082/SFreeAds/adsConfig
curl -fsS 'http://127.0.0.1:18082/SFreeAds/adsList?searchParams=%7B%7D&limit=8&page=1'
bash /opt/starfree-replacement/verify-space-cutover.sh
bash /opt/starfree-replacement/verify-space-like.sh
bash /opt/starfree-replacement/verify-space-follow.sh
bash /opt/starfree-replacement/verify-space-add.sh
bash /opt/starfree-replacement/verify-space-edit.sh
bash /opt/starfree-replacement/verify-space-review.sh
bash /opt/starfree-replacement/verify-space-lock.sh
bash /opt/starfree-replacement/verify-space-delete.sh
bash /opt/starfree-replacement/verify-contents-info-cutover.sh
bash /opt/starfree-replacement/verify-contents-add-cutover.sh
bash /opt/starfree-replacement/verify-contents-update-cutover.sh
bash /opt/starfree-replacement/verify-economy.sh
bash /opt/starfree-replacement/verify-ads-reward.sh
bash /opt/starfree-replacement/verify-user-registration.sh
LOGIN_URL=https://api.lcxqy.cn \
  bash /opt/starfree-replacement/verify-account-maintenance.sh
REGISTER_URL=https://api.lcxqy.cn \
  EXPECTED_BACKEND=replacement-user-register \
  bash /opt/starfree-replacement/verify-user-registration.sh
REPLACEMENT_URL=https://api.lcxqy.cn \
  bash /opt/starfree-replacement/verify-account-maintenance.sh
REPLACEMENT_URL=https://api.lcxqy.cn \
  HEALTH_URL=http://127.0.0.1:18082/health \
  bash /opt/starfree-replacement/verify-economy.sh
REPLACEMENT_URL=https://api.lcxqy.cn \
  bash /opt/starfree-replacement/verify-ads-reward.sh
```

The second request must be served through the replacement fallback and return
the old API response from port 8081. The advertising requests must return the
same public envelope and field sets as direct legacy requests. The cutover
audit checks service health, disposable-record cleanup, Nginx syntax, all ten
exact Space routes, every route-specific backend header, both followed-feed
aliases, and all disposable test prefixes.

## Operational Cautions

- `spaceDelete` intentionally removes only the selected Space row, matching the
  closed backend's actual SQL. Replies, forwards, and `spaceLike` logs can
  therefore reference a missing parent. Any future cascade cleanup must be a
  separately rehearsed data migration, not an endpoint side effect.
- The replacement intentionally fixes the closed API's `purview="1"` AOP bug:
  owners can use the self-delete button already shown by the frontend. Staff can
  still delete any Space row, and deletion by staff writes a system notice.
- `starfree_space` and related legacy tables are MyISAM. Multi-table writes are
  explicitly compensated where possible, but they are not fully transactional
  across a process or database crash.
- Moderation changes are authoritative once the Space row is updated. Inbox
  notification insertion is best effort so a notice failure cannot cause the
  client to retry an already completed lock/review/delete operation.
- Keep port 18082 bound to loopback. Public traffic must pass through Nginx so
  exact-route boundaries and `X-Starfree-Backend` auditing remain effective.
- `contentsInfo` is intentionally different from most controllers: success is
  the raw article object, not a `code/data` envelope. It preserves the complete
  body and increments views only once per IP/User-Agent within the shared
  Java-serialized 900-second Redis window.
- `contentsAdd` is intentionally a guarded hybrid route. Only structurally valid
  ordinary `post` and `video` forms are written by the replacement. Ambiguous or
  closed-feature forms are sent to the loopback legacy API without reconstructing
  those payment, draft, Space, or shop workflows.
- `contentsUpdate` uses the same guarded hybrid boundary. It preserves an
  existing post/video type and Markdown mode when the frontend omits those
  fields, converts `||rn||` to LF, validates meta IDs before MyISAM writes, and
  evicts legacy `contentsInfo` plus page-one `contentsList` caches. Cache cleanup
  is best effort after an authoritative article write.
- `assets` is the wallet balance, `points` is the task/shop-offset balance, and
  `experience` is level progress. Never substitute one column for another.
- The legacy balance, paylog, and userlog tables are MyISAM. Every replacement
  balance write must use `EconomyLockExecutor`, write an idempotency entry in
  `starfree_economy_operations`, and compensate completed MyISAM projections on
  failure. Any `needs_review` operation requires manual reconciliation.
- Keep official payment internals, verification, upload, chat, and plugin routes
  on the legacy API. Shop/VIP and paid-ad balance writes are replacement routes.
  Plugin type 6 remains intentionally rejected by replacement publishing/editing.
- Server-side advertising rewards fail closed when `adsVideoType=1` and
  `adsSecuritykey` is empty. The callback secret must match the advertising
  provider; never use the old publicly forgeable `SHA256(":" + trans_id)` state.
- Run disposable production smoke scripts serially. Their `csa_`, `cse_`,
  `csr_`, `csk_`, `csd_`, `cci_`, `cca_`, `ccu_`, `ceu_`, `cea_`, `cr_`, and
  `cri_` prefixes and `economy shop/vip/ad` names are reserved for these
  scripts. Each cleanup
  removes rows only by its resolved disposable IDs.
- Registration accepts only server-owned fields. Client `assets`, `points`,
  `experience`, `vip`, group, and timestamps are ignored. A failure before the
  journal commit is compensated in reverse order; an ambiguous journal commit
  preserves the MyISAM rows and marks the operation `needs_review`.
- Account maintenance accepts only profile fields used by the frontend. It
  never changes wallet, points, experience, VIP, group, or username. Keep
  `SendCode`, `RegSendCode`, and SMS delivery on the legacy catch-all until
  their provider and template integrations are rebuilt.
- Password reset must revoke Redis aliases by username, mail, and phone. The
  retained public `userLogin` creates Redis-only sessions without updating
  `starfree_users.authCode`; checking that column alone is insufficient.

## Rollback

To roll back only authenticated Space reads, restore the timestamped include
backup created by the cutover script, run `nginx -t`, and reload Nginx. The
2026-07-26 cutover backup is:

```text
/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-20260726-224520
```

To roll back only `spaceLikes`, restore this later backup, then test and reload
Nginx:

```bash
cp -p /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-like-20260726-232718 \
  /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf
nginx -t && nginx -s reload
```

The JAR deployed with the Space-like cutover can be rolled back independently
from `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260726-232613`.

To roll back only the two followed-feed routes, restore the latest follow
backup and reload Nginx:

```bash
cp -p /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-follow-20260726-235034 \
  /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf
nginx -t && nginx -s reload
```

The matching JAR rollback is
`/opt/starfree-replacement/starfree-replacement.jar.rollback-20260726-234702`.

To roll back only `addSpace`, restore the add-specific Nginx backup and reload:

```bash
cp -p /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-add-20260727-083712 \
  /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf
nginx -t && nginx -s reload
```

The matching pre-add JAR is
`/opt/starfree-replacement/starfree-replacement.jar.rollback-20260727-083154`.

The later Space write routes have independent Nginx rollback files. Restore
the required file to the active include, then run `nginx -t && nginx -s reload`:

```text
editSpace:   /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-edit-20260727-085240
spaceReview: /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-review-20260727-090200
spaceLock:   /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-lock-20260727-091527
spaceDelete: /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-delete-20260727-094419
contentsInfo: /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-contents-info-20260727-102358
contentsAdd:  /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-contents-add-20260727-115506
contentsUpdate: /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-contents-update-20260727-124142
adsReward: /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-ads-reward-20260727-152252
userRegister: /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-user-register-20260728-131629
accountMaintenance: /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-account-maintenance-20260728-140012
```

Economy route backups are cumulative snapshots. The pre-economy Nginx snapshot
is
`/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-economy-pay-scancode-20260727-142529`.
Restoring it removes all 31 economy locations, not only the first payment route.
For a one-route rollback, remove only that marked `# Economy route` location,
then run `nginx -t && nginx -s reload`; do not restore an earlier cumulative
snapshot unless all later economy routes should also be removed. A full economy
rollback also requires restoring the frontend's former sign-in URLs because the
current frontend calls `/SFreeEconomy/*`. The InnoDB journal table can remain.

As of 2026-07-29, the current production JAR SHA-256 is
`fc425cf687cf0eb6c153b511e01b6aceb5401fbde40260b2a7142d6cf481ab68`.
The automatic pre-deployment rollback file is
`/opt/starfree-replacement/starfree-replacement.jar.rollback-20260729-085326`;
it contains the previously active `74285a...0339` build and is compatible with
the active routes. Do not restore the older intermediate
`starfree-replacement.jar.rollback-20260728-135839` while account routes remain
active; that backup predates the Redis-only reset-session correction.
The current Nginx include SHA-256 is
`2fcf7e198dfcd15a1b7eca5024fd323a3540c40fa05b6bc058bf6e1d42427ba7`.

The 2026-07-29 deployment changed only the JAR; it did not change or reload
Nginx and did not publish frontend files. The service is active as PID `15011`.
Serial direct/public production tests passed Redis-only authentication, every
Space route, content detail/add/update, economy, advertising reward, user
registration, and account maintenance. Final disposable SQL/Redis residue is
zero, and `starfree_economy_operations` has `started=0` and `needs_review=0`.

Removing or renaming the entire replacement include returns all gray routes to
the existing port-8081 catch-all, but current `/SFreeEconomy/*` frontend calls
then require a matching frontend rollback. Stopping `starfree-replacement.service`
is optional after all traffic has returned to the old service.
