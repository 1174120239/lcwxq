# StarFree Backend Rebuild Status

Updated: 2026-08-02

## Development Runtime

- Service: Spring Boot 2.7.18, Java 8 compatible bytecode
- Local API: `http://127.0.0.1:18082`
- Local database: MySQL schema `lcxqy_dev`
- Frontend release API setting: `utils/api.js` points to `https://api.lcxqy.cn/`
- Existing PHP admin remains at `https://admin.lcxqy.cn/`

Port `8082` is already used by a local QQ process, so the replacement backend uses
`18082` during development.

Start the local service from the project root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/scripts/start-local.ps1
```

The script stores the password only in
`backend/.local/run/application-secrets.yml`. The whole `backend/.local`
directory is ignored and must not be committed or deployed.

## Implemented Locally

- Health: `/health`, `/health/live`
- Authentication: local `userLogin`, token rotation, `signOut`, and Typecho
  phpass password verification
- Optional legacy Redis session bridge for sharing new-login tokens with the
  retained payment, verification, upload, and chat endpoints
- Users: `userStatus`, `userInfo`, `userData`, `userRegister`, `regConfig`,
  `userFoget`, `userEdit`, `setClientId`, `userList`, `phoneLogin`,
  `manageUserEdit`, `userDelete`, QR approval, invitation generation/list/export,
  system messages, ban/unban/history, per-user cleanup, restriction, and VIP gift
- Contents: list, info, add, update, delete, configured audit status, daily
  post experience cap, delete experience deduction, and local moderation
- Comments: list, add, delete, configured audit status, daily review
  experience cap, delete permission rules, and local moderation
- Metas: list, info, select contents, add/edit/delete, recommendation, uniqueness,
  parent-cycle prevention, article relationship cleanup, dynamic-topic cleanup,
  and legacy cache invalidation
- User interactions: paged bookmark details, legacy `logid`, add/remove log,
  duplicate operation rejection, administrator removal permissions, buyer/seller
  order reads, and guarded administrator cleanup selectors
- Economy: clock-in rewards, one clock-in per day, reward transfers, guarded
  balance deduction, paylogs, finance inbox entries, experience and points
- Social and notifications: follow/unfollow, follow/fan lists, local inbox
  paging, comment-content links, unread counts, and notification read state
- Advertising: config/list/info, purchase/edit/delete, audit and renewal,
  owner/staff authorization, slot/expiry checks, guarded asset deductions,
  paylogs, advertising reward start/client/server callbacks, global callback
  idempotency, and explicit MyISAM compensation
- Space: add/edit/review/lock, info/list/delete/likes, follow lists, private and
  pending visibility, staff-only management views, reply-counter privacy, dynamic
  topics, topic follow/unfollow, topic-filtered Space lists, and explicit
  rejection of plugin type 6
- Content extensions: recommendation/top/swiper flags, owner/staff string-field
  upsert, public delete config, staff dashboard counts, comment-participation
  check, fixed-host Pexels and ForeverBlog feeds, shared cache and rate limiting
- Shop catalog and administration: list/info/add/edit/delete/audit/mount,
  token-derived ownership, paid-value visibility, SQL allowlists, moderation,
  cross-owner mount prevention, legacy cache invalidation, and public VIP packages
- Frontend coverage: 106 of 133 unique `utils/api.js` paths have dedicated local
  implementations; 27 still require the retained API
- CORS for the H5 development frontend
- Legacy-compatible business error envelopes (`code: 0`)
- Reusable disposable-record integration tests:
  `backend/scripts/test-local-economy.ps1`
  `backend/scripts/test-local-ads.ps1`
  `backend/scripts/test-local-ads-reward.ps1`
  `backend/scripts/test-local-space.ps1`
  `backend/scripts/test-local-account-maintenance.ps1`

## Legacy Fallback

Routes that are not implemented by a local controller are proxied to
`https://api.lcxqy.cn`. The 27 remaining frontend paths are six verification or
social-login/bind routes, `upload/full`, eleven chat routes, and nine official
payment/card routes. Payment callbacks are also legacy but are not part of the
133 frontend-path count.

The fallback is transitional. Each migrated module should replace its proxy
route with a local controller and tests.

## Production Gray Rollout

- Replacement service: `127.0.0.1:18082`, managed by
  `starfree-replacement.service` and enabled at boot
- Legacy service: unchanged on `127.0.0.1:8081`
- Public requests without a token use the replacement for `contentsList`,
  `commentsList`, `metasList`, and `selectContents`
- Anonymous and token-bearing `SFreeContents/contentsInfo` requests use the
  replacement after raw-payload, CommonMark, pending-visibility, and shared
  Redis read-key compatibility tests
- `adsConfig` and anonymous `adsList` requests use the replacement for public
  advertisement reads
- `adsList` requests carrying a query-string `token` stay on the legacy
  service, so user and moderation views are not part of this rollout
- Ordinary `post` and `video` requests for `contentsAdd` and `contentsUpdate`
  use the replacement. Paid, draft, Space/shop-linked, malformed, and
  unsupported content requests are delegated internally to the legacy service
- Token-bearing `contentsList`, advertising, comment-list, and meta-content
  routes remain on the legacy API under Nginx
- Advertising purchases, renewals, and all three reward routes use replacement
  exact locations. Remaining advertising-management routes use the legacy
  catch-all
- Both anonymous and token-bearing `SFreeSpace/spaceList` and `spaceInfo` reads
  use the replacement
- `SFreeSpace/spaceLikes` is the first isolated Space write on the replacement;
  `SFreeSpace/addSpace` is now independently routed to the replacement
- `editSpace`, `spaceReview`, `spaceLock`, and `spaceDelete` are independently
  routed to the replacement after route-specific Redis-only and disposable-data
  tests; all ten rebuilt Space routes are now active
- Authenticated `SFreeSpace/followSpace` and the frontend's
  `SFreeSpace/myFollowSpace` alias use the replacement and exclude followed
  users' private rows and replies
- `SFreeUsers/userRegister`, `regConfig`, `userFoget`, `userEdit`, and
  `setClientId` use exact replacement routes. Email/SMS delivery remains on
  the legacy catch-all; the replacement consumes the same serialized Redis
  codes and revokes both MySQL-backed and Redis-only sessions
- User administration, meta administration, content extensions/external feeds,
  order/cleanup routes, shop catalog/management, and `vipTypeList` are currently
  local-only. No production JAR or Nginx route was changed for these additions.
- Response header `X-Starfree-Backend` identifies each gray boundary:
  `replacement-public-read`, `replacement-space-like`,
  `replacement-space-follow`, `replacement-space-add`,
  `replacement-space-edit`, `replacement-space-review`,
  `replacement-space-lock`, `replacement-space-delete`,
  `replacement-content-info`, `replacement-content-add`, or
  `replacement-content-update`, plus the four `replacement-account-*`
  headers; retained token routes report `legacy-token`
- Replacement rollback JARs are retained in `/opt/starfree-replacement`; the
  original `/opt/StarFreeApi.jar` and PHP admin were not changed

## Explicitly Excluded

Plugin endpoints and PHP plugin directories are not part of this rebuild.

## Verified


- MySQL snapshot imported: 33 tables; the local database still contains the
  original single user after disposable test cleanup
- Local database connection succeeds
- Content list and detail return the snapshot post
- Comment list returns the snapshot comment
- Meta list and user lookup return valid legacy envelopes
- Login rotates tokens, invalidates the old token, omits sensitive fields, and
  logout invalidates the active token
- Economy and social integration covers follow/fan listing, comment inbox
  navigation, unread/read state, post/comment creation, bookmark status/list and
  removal, content/comment moderation, reward balances, three paylogs, clock-in
  deduplication, user delete restrictions, administrator deletion, and frontend
  `key` parameters
- Advertising integration covers purchase, guarded asset deduction,
  pending/private visibility, staff audit, edit-to-review, owner/staff renewal,
  deletion, paylogs, and automatic disposable-record cleanup
- Advertising reward integration rejects cross-user `logid` claims, verifies
  client and signed server callbacks, acknowledges `trans_id` replay without a
  second credit, enforces the daily limit, and rejects an empty callback secret
- Legacy `regConfig` fallback returns the original API response
- CORS preflight from `http://localhost:8080` succeeds
- Maven tests: 188 passed, 0 failed, 0 errors, including content-detail raw-payload,
  full-body, CommonMark, pending-visibility, and Redis read-key coverage;
  explicit duplicate-Space-like,
  legacy Redis abuse-control, MyISAM publishing compensation, and
  followed-feed visibility regressions, plus lock authorization, pending-row,
  duplicate-state, and delete owner/staff/notification-failure coverage
- Local service health: `UP`, database `lcxqy_dev`
- Production public content/comment response key sets match the legacy API at
  envelope, item, author, and linked-content levels
- Public HTTPS and CORS checks pass; token-bearing list requests report
  `legacy-token`; an unmigrated `regConfig` request still returns HTTP 200 from
  the legacy catch-all
- Redis key inspection confirms legacy Java serialization and hash sessions
- Production Redis-only smoke testing clears the disposable user's MySQL
  `authCode`, then confirms both APIs can resolve the Redis session; a private
  Space row remains hidden anonymously and visible to its owner
- The same Redis-only token passes through public HTTPS/Nginx for both
  `spaceInfo` and `spaceList`, reports `replacement-public-read`, and the
  disposable user, private row, and Redis session are removed afterward
- Production replacement JAR deployed at SHA-256
  `6be4be712159a402800220cd7225efa627898d18e3196117af655d0f680f1e38`; service
  `starfree-replacement.service` is active on PID 25127 with database `lcxqy`
- Production route verification confirms public content/meta requests use
  `replacement-public-read`; public `adsConfig` and anonymous `adsList` use the
  replacement; token-bearing advertising/content lists use `legacy-token`; and
  local `regConfig` fallback still returns the legacy response
- Space-like production tests cover replacement-first/legacy-duplicate,
  legacy-first/replacement-duplicate, and a public Redis-only first/duplicate
  pair. The counter and durable `spaceLike` log counts match in every run.
- `addSpace` production tests use a Redis-only token for public/private posts,
  reject plugin type 6, verify audit/experience/user fields, and prove the old
  backend advances the same Java-serialized `_spaceNum` counter from 2 to 3
- Post-cutover audits confirm the replacement service remains active on PID
  25127, Nginx syntax passes, ten exact Space locations and three migrated
  content locations are present, all route headers identify the intended
  backend, and no disposable SQL or Redis records remain

The current snapshot config has `postExp`, `reviewExp`, `deleteExp`, `clock`,
`clockExp`, and `clockPoints` set to zero. The rule framework is active without
inventing new reward values.

## Known Gaps

- Space like compatibility was confirmed from the legacy JAR bytecode: it uses
  the authenticated user id plus Space id and a durable
  `starfree_userlog(type='spaceLike')` row. It does not use the request IP,
  User-Agent, a Redis like key, or a 24-hour TTL. The replacement follows the
  same rule and adds a MySQL named lock plus compensation for MyISAM safety.
- `spaceDelete` intentionally preserves the closed backend's main-row-only SQL.
  Replies, forwards, and `spaceLike` logs may reference a deleted parent; a
  future cascade cleanup requires an explicit migration and orphan-data audit.
- The replacement intentionally permits owner self-delete because the frontend
  exposes that action and the old controller contained the same owner branch.
  The closed API's `purview="1"` AOP made its branch unreachable in production.
- Advertising purchase and owner renewal use explicit compensation and unique
  trade numbers, but MyISAM still cannot guarantee atomic rollback if the
  process or database stops between statements.
- Ordinary content updates pre-validate every relationship ID and compensate
  article/relationship writes where possible. The replacement intentionally
  fixes the legacy editor's lost Markdown marker, literal `||rn||` storage, and
  video-to-post type change instead of preserving those defects.
- Snapshot tables use MyISAM, so Spring transactions cannot provide full
  multi-statement atomicity. Evaluate and rehearse an InnoDB migration before
  production cutover.
- Shop add/edit/delete and optional product-Space creation span MyISAM rows and
  best-effort Redis/notification projections. MySQL is authoritative; real-MySQL
  integration and residue checks are required before any production route change.
- The unchanged owner-product and management pages omit token on some shop-list
  requests. The local implementation preserves non-sensitive uid/status metadata
  compatibility but never exposes paid `value` without owner/staff authorization.

## Next Priority

1. Rehearse the MyISAM-to-InnoDB migration against a fresh snapshot and test rollback.
2. Inventory orphaned Space replies/forwards/like logs and design a separate,
   reversible cleanup migration without changing endpoint behavior.
3. Run disposable real-MySQL shop add/edit/audit/mount/purchase/detail/delete
   tests, including old Redis cache interoperability, before packaging a JAR.
4. Keep payment, SMS/email verification, upload, chat, and social provider flows
   on the legacy fallback until their compatibility boundaries are explicitly tested.

## 2026-07-28 Local API Expansion (JAR Deployed; Public Routes Not Cut Over)

- Added 15 user administration routes, four meta management routes, nine content
  extension/feed routes, three order/cleanup routes, seven shop catalog/management
  routes, and `StarFreeSystem/vipTypeList`.
- Management writes use fixed field/SQL allowlists and cursor-based invalidation
  of legacy Java-serialized Redis projections; no `KEYS *` operation is used.
- Pexels uses a fixed provider host, database-loaded credential, three-second
  shared client throttle, and six-hour cache. ForeverBlog uses a fixed host and
  two-minute cache. Neither endpoint accepts a caller-controlled target URL.
- Shop paid value is visible only to seller, staff, or a token-bound persisted
  buyer; uid/status/cid/sellNum/created cannot be forged during add/edit. Mounting
  another user's product/article is rejected outside staff correction.
- Targeted shop/cache compatibility tests passed 15/15. Full Maven regression
  passed 185/185 with zero failures and errors.
- These routes were not deployed during the implementation phase. They are now
  present in the 2026-07-29 production JAR on port 18082, but no new Nginx exact
  routes were added for them. Public traffic therefore continues to follow the
  existing replacement locations or the port-8081 catch-all.

## 2026-07-28 Deleted-Content Cache and Frontend Fix (Backend Deployed; Frontend Not Published)

- Home now treats a successful empty response as authoritative for global,
  category, recommended, ranking, carousel, and pinned content projections.
  Corrupt localStorage JSON is discarded instead of breaking page startup.
- Home refreshes once on every `onShow`; duplicate `onLoad`/`mounted` refreshes
  were removed. Category pagination no longer appends global posts.
- Article and video detail pages remove stale post/comment projections, stop the
  loading overlay, show a deleted-content message, and navigate away when
  `contentsInfo` returns no article. Transient network failures retain cached
  content and show a retry message instead of falsely declaring deletion.
- `pages/contents/videoInfo` is now registered in `pages.json`; HBuilderX emits
  the corresponding H5 route chunk and video-card navigation is valid.
- Replacement content delete and audit now invalidate detail, all content-list,
  and category-list legacy Redis projections. Delete also refreshes affected
  meta counts after removing relationships.
- Clean Maven packaging passed 185/185 with zero failures/errors and produced a
  local candidate JAR. HBuilderX dev compilation serves the home, article, and
  video chunks on port 8080. Local health reports `UP` with database `lcxqy_dev`.
- The backend cache invalidation is included in the 2026-07-29 production JAR.
  The H5 fixes remain in the local source tree and were not published by this
  deployment. Nginx was not changed, so the route hash remains unchanged.

## 2026-07-29 Final JAR Deployment and Production Regression

- Deployed candidate SHA-256
  `fc425cf687cf0eb6c153b511e01b6aceb5401fbde40260b2a7142d6cf481ab68`
  with `/opt/starfree-replacement/deploy-jar.sh`. The server independently
  verified the uploaded `.new` file before replacement.
- The service recovered automatically and is active as PID `15011` on
  `127.0.0.1:18082`. Both `/health/live` and `/health` report `UP` against the
  production `lcxqy` database. Startup completed without application errors.
- The deployment-created pre-release rollback JAR is
  `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260729-085326`.
  It contains the previously active `74285a...0339` account-maintenance build.
- No Nginx route or frontend artifact was changed. The active include remains
  SHA-256 `2fcf7e198dfcd15a1b7eca5024fd323a3540c40fa05b6bc058bf6e1d42427ba7`,
  and `nginx -t` passes.
- Serial production regression passed Redis-only sessions; all Space reads and
  writes; content detail, add, and update; economy operations; disabled
  server-ad reward behavior; public registration; and account maintenance.
  Public tests checked the expected `X-Starfree-Backend` route headers.
- Final audits report zero disposable Space/content SQL and Redis residue.
  Registration and account cleanup passed, and the economy journal has
  `needs_review=0` and `started=0`.

## 2026-07-26 Space Authenticated-Read Cutover

- Nginx include hash: `4fc3843f6ea781b9e10dc39822bd581315f96db294695f8efb91c550de08f5e6`
- Rollback config: `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-20260726-224520`
- Anonymous and token-bearing `spaceList` and `spaceInfo` are served by
  `replacement-public-read`.
- A valid Redis-only owner token can read its private disposable Space row via
  both public endpoints while anonymous access is rejected.
- Token-bearing `contentsList` still reports `legacy-token`; at the time of this
  read cutover all Space writes still used the legacy catch-all.
- Final audit result: `space_cutover_audit=PASS`, with zero disposable users and
  zero disposable Space rows.

## 2026-07-26 Space-Like Cutover

- Production JAR hash: `bc7a60dfe3ff9fd2349c55a33123cb5f6f3e6a19eb7da393801383024f7e1f2c`
- JAR rollback: `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260726-232613`
- Nginx include hash: `5a3ba35b233c9b2d7f5439f54eb626ac30facc8348e0e1355edabb67628b4b71`
- Nginx rollback: `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-like-20260726-232718`
- Legacy JAR bytecode and two-way production tests confirm durable
  `uid + Space id + type='spaceLike'` de-duplication shared by both backends.
- Public HTTPS testing with a Redis-only token confirms first like `code:1`,
  duplicate like `code:0`, `X-Starfree-Backend: replacement-space-like`, and
  matching counter/log totals.
- Final audit result: `space_cutover_audit=PASS`; `addSpace` and every other
  Space write still use the legacy catch-all, and disposable rows are zero.

## 2026-07-26 Followed-Space Cutover

- Production JAR hash: `0013862bcf6da0c6106873d9233831b95bf4dcbdba8c0306bf07938d85223aa6`
- JAR rollback: `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260726-234702`
- Nginx include hash: `02fe9b0856c21638b9f65fe20556aa29e8fc52e11a9cbe25e6bb943dc5e98334`
- Nginx rollback: `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-follow-20260726-235034`
- Old JAR bytecode and production disposable records confirm that legacy
  `/followSpace` returned followed users' `onlyMe=1` rows and reply rows. The
  replacement intentionally fixes this privacy leak and keeps only
  `status=1 AND onlyMe=0 AND type<>3` rows.
- Both `/followSpace` and the frontend's `/myFollowSpace` alias pass public HTTPS
  tests with a Redis-only token and report `replacement-space-follow`.
- Final audit result: `space_cutover_audit=PASS`; exactly five Space locations
  are active and all disposable users/dynamics are zero.

## 2026-07-27 Space Publishing Cutover

- Production JAR hash: `6a98c52cc1c597a24a9545e72f9ce2186d87973fae4169b1c35d1dcaa506ff67`
- JAR rollback: `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260727-083154`
- Nginx include hash: `186cba8c435573b53970c6ddfabf8b548f106373851351ce3992083f544444b4`
- Nginx rollback: `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-add-20260727-083712`
- The replacement and legacy backend share `_silence`, `_isAddSpace`,
  `_isIntercept`, and `_spaceNum` Java-serialized Redis keys. Failed MyISAM
  inserts release replacement quota reservations; successful inserts are not
  reported as failures when a secondary user/experience update fails.
- Direct and public disposable tests cover Redis-only login, public/private
  status, user activity fields, experience logs, plugin type 6 rejection,
  cross-backend quota reuse, response header, and cleanup.
- Final audit result: `space_cutover_audit=PASS`; exactly six Space locations
  are active, `editSpace` remains on the legacy catch-all, and all `csa_`
  disposable rows are zero.

## 2026-07-27 Space Edit Cutover

- Nginx pre-cutover hash/rollback:
  `186cba8c435573b53970c6ddfabf8b548f106373851351ce3992083f544444b4` at
  `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-edit-20260727-085240`.
- Redis-only owner, non-owner, and administrator tests confirm authorization;
  administrator edits preserve the original author and immutable Space type.
- Plugin rows, type spoofing, and replies targeting a locked Space are rejected.
- Public HTTPS reports `replacement-space-edit`, and disposable `cse_` rows and
  sessions are removed.

## 2026-07-27 Space Review Cutover

- Nginx pre-cutover hash/rollback:
  `01a0fb294e34a2ad2c0d71be50237f4e9d160a641e473ae55ef3ebe8fbf84179` at
  `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-review-20260727-090200`.
- Both runtimes reject non-staff callers. The replacement fixes the legacy
  pending-rejection bug, prevents duplicate approval, and writes system notices
  after the authoritative MyISAM change.
- Public HTTPS reports `replacement-space-review`, and disposable `csr_` rows
  and sessions are removed.

## 2026-07-27 Space Lock Cutover

- Production JAR hash remains
  `bc469c75a100b490268a2b1ca68a835732853e046aa5a9676dba308ad37f6524`;
  no JAR replacement was required because the verified lock implementation was
  already running.
- Nginx pre-cutover hash/rollback:
  `188716bf57e38df0e7d4c0cff05bfa2356af92a53feb848cf8bd4963c1c65a07` at
  `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-lock-20260727-091527`.
- New/legacy direct tests with Redis-only sessions confirm staff authorization,
  pending-row rejection, type 2 lock, type 1 unlock, duplicate-state rejection,
  and system notices. The public disposable lock reports
  `replacement-space-lock`.
- The post-lock Nginx hash was
  `c108d91b20c7a3cfd330789f1c06b29f89bc4623a70952c79f7b068e9143f029`.
  The local template and downloaded production snapshot match for all 15 exact
  locations.
- Final audit result: `space_cutover_audit=PASS`; exactly nine Space locations
  are active, `spaceDelete` remains on the legacy catch-all, and all disposable
  users, dynamics, notices, and Redis-backed login sessions are cleaned.

## 2026-07-27 Space Delete Cutover

- Production JAR hash:
  `7efd82e1f49d69a24cdf055ae55cf4e78ed9c34012bce1677702fb04ddcc72fe`;
  JAR rollback:
  `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260727-094328`.
- Nginx pre-cutover hash/rollback:
  `c108d91b20c7a3cfd330789f1c06b29f89bc4623a70952c79f7b068e9143f029`
  at `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-space-delete-20260727-094419`.
- Legacy AOP inspection and production testing confirm that `purview="1"`
  accidentally blocked the controller's owner-delete branch. The replacement
  intentionally restores owner self-delete because the client exposes it.
- Both runtimes reject non-owners. Staff deletion, system notices, unchanged
  experience, and main-row-only deletion are verified. Child Space rows and
  durable `spaceLike` logs remain in both runtimes by contract.
- Public HTTPS owner deletion reports `replacement-space-delete`. Current Nginx
  hash is `a5a1a5d3a2ee80465625f99337ca5bbe3dbdc7f655c19904ad1c8f6f448dd6e3`;
  the local template and downloaded production snapshot match for all 16 exact
  locations.
- Final audit result: `space_cutover_audit=PASS`; exactly ten Space locations
  are active, all `csd_` SQL and Redis residues are zero, and every rebuilt
  Space endpoint now has an independent production route and rollback point.

## 2026-07-27 Content Detail Cutover

- Production JAR hash:
  `d5aebd5a3fe37f26e68028560ffef447a74a27e8947765c1a9ddc8033de32752`;
  JAR rollback:
  `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260727-102114`.
- Nginx hash:
  `af7361534cec184b2a44d8c29e42ca01082035037dba671e2fb909f1e89a33e5`;
  rollback:
  `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-contents-info-20260727-102358`.
- Disposable old/new tests compare canonical `isMd=0` and `isMd=1` payloads,
  including full text, HTML and Markdown images, `template`, fields with `cid`,
  categories, tags, password omission, and the legacy raw top-level object.
- Anonymous callers are denied access to waiting content. A Redis-only
  administrator can read it through both runtimes.
- A legacy first read changed the disposable article from 17 to 18 views. A
  replacement duplicate read kept it at 18 through the same Java-serialized
  key with a 900-second TTL. Public HTTPS reports
  `replacement-content-info` and preserves the same rule.
- The local Nginx template and downloaded production snapshot match for all 17
  exact locations. Final result: `contents_info_cutover_audit=PASS`, with zero
  `cci` SQL or Redis residue.

## 2026-07-27 Content Publishing Cutover

- Production JAR hash:
  `23c742875f23e2688d7e93fbbb784cbab81b6056c252130d5e55195e6c674da4`;
  JAR rollback:
  `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260727-115250`.
- Nginx hash:
  `f05b0c139cc80c1e73a87aa37749bba48281400dd6993b6529be5631f3a1eaee`;
  rollback:
  `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-contents-add-20260727-115506`.
- Two-way old/new publishing tests confirm shared Redis post quotas, matching
  post/video defaults and relationships, contributor waiting status, and
  administrator publishing. Closed content features carry
  `X-Starfree-Delegate: legacy-contents-add`.
- Final result: `contents_add_cutover_audit=PASS`, with zero `cca_` SQL or Redis
  residue.

## 2026-07-27 Content Update Cutover

- Production JAR hash:
  `6be4be712159a402800220cd7225efa627898d18e3196117af655d0f680f1e38`;
  JAR rollback:
  `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260727-123402`.
- Nginx hash:
  `1fb8df13e923dddcc23a5f491c9b0320d5d8cb6617602ced84020753074111fe`;
  rollback:
  `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-contents-update-20260727-124142`.
- Production tests use Redis-only contributor/administrator sessions and cover
  authorization, pending/published status, post relationships and meta counts,
  numeric missing-meta rejection before MyISAM changes, legacy detail/page-one
  cache invalidation, preserved video type, Markdown mode, and real newlines.
- Paid, draft, shop-linked, and unsupported rows remain on port 8081 and carry
  `X-Starfree-Delegate: legacy-contents-update`. Public ordinary updates report
  `X-Starfree-Backend: replacement-content-update`.
- Final results: `contents_update_direct_audit=PASS` and
  `contents_update_cutover_audit=PASS`. All `ccu_` SQL and Redis residue is zero,
  and the content-add, content-info, and all Space regression audits still pass.

## 2026-07-27 Economy Cutover

- Production JAR SHA-256:
  `0c997c00b986ef08c68fd57cb4915f5e4355042f26d24d1a8fd425cb9bedc113`;
  pre-economy rollback JAR:
  `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260727-142132`.
- Added the InnoDB `starfree_economy_operations` journal with a unique
  `operation_key`. Replacement writes serialize on the global MySQL advisory
  lock and commit journal state before releasing it; MyISAM projection failures
  are compensated and unresolved operations become `needs_review`.
- Wallet `assets`, task/shop-offset `points`, and level `experience` remain
  separate. Replacement routes now own reward/clock, seven-day sign-in, manual
  adjustments, withdrawals, wallet/finance reads, shop purchases, VIP
  purchases, and paid-ad purchases/renewals.
- Official payment creation, card redemption, and payment callbacks still
  execute the closed API on port 8081. Their nine exact public routes first
  enter the replacement proxy so the shared economy lock is held while the
  original request and response bytes are forwarded. Advertising rewards were
  subsequently moved to the replacement in the cutover below.
- Nginx has 31 economy locations with no duplicate exact routes. Final include
  SHA-256:
  `ede449fdb078c70c79c710a909296e5ff78d1c7afb9dc05658626e95424bdb86`;
  pre-economy snapshot:
  `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-economy-pay-scancode-20260727-142529`.
- Direct-port and public-HTTPS production smoke tests passed reward replay,
  sign-in isolation, asset/point adjustments, shop stock and buyer/seller
  transfer, VIP replay, ad debit/renewal replay, withdrawal approval, and wallet
  payload compatibility. Disposable users, content, shop, VIP, ad, journal, and
  `needs_review` counts are all zero after cleanup.
- Post-deployment regressions passed: `space_cutover_audit=PASS`,
  `contents_info_cutover_audit=PASS`, `contents_add_cutover_audit=PASS`, and
  `contents_update_cutover_audit=PASS`, with zero SQL/Redis disposable residue.

## 2026-07-27 Advertising Reward Cutover

- Production JAR SHA-256:
  `52274025aed8c90ed4c4d8f39767122ad8f0f5045168c599783aec47907c21b9`;
  rollback JAR:
  `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260727-151747`.
- Client callbacks validate that the pending `adsGift` log belongs to the
  authenticated user. Server callbacks use constant-time signature comparison,
  global `trans_id` idempotency, durable daily logs, the shared economy lock,
  and explicit MyISAM compensation.
- The three public reward routes report `replacement-economy-replacement-*`.
  Nginx include SHA-256:
  `c255f391ac6ff0ac437c3b9eda1c1f1ede90776b5588f5dadf61fa513c5da0ec`;
  rollback config:
  `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-ads-reward-20260727-152252`.
- Local Maven tests are `105/105`. Local real-MySQL tests cover cross-user
  claims, client/server replay, cross-user transaction replay, daily limits,
  and empty-secret rejection. Direct-port and public production smoke tests
  passed with zero disposable residue and `needs_review=0`.
- Production currently has `adsVideoType=1`, `adsGiftNum=10`,
  `adsGiftAward=5`, and an empty `adsSecuritykey`. Server reward callbacks
  therefore fail closed until the same non-empty secret is configured in both
  the advertising provider and admin settings. This intentionally removes the
  old forgeable `SHA256(":" + trans_id)` behavior.
- Official recharge creation and payment notifications remain on port 8081.
  All Space and content cutover regression audits still pass.

## 2026-07-28 User Registration Cutover

- Rebuilt `SFreeUsers/userRegister`; `SFreeUsers/RegSendCode`, password recovery,
  official recharge, card redemption, and payment callbacks remain on port 8081.
- The closed registration method consumed invitation codes but never assigned
  `isUserInvite`, `inviteUserID`, or `inviteUserAssets`, so its configured fixed
  invite reward was unreachable. The replacement records `invitationUser` and
  applies `rebateNum` only when `rebateLevel` is 1 or 3.
- Registration uses an explicit field whitelist, PHPass `$P$B` hashes, the old
  Java-serialized `starfree_sendCode{mail}` Redis key, the global economy lock,
  deterministic account idempotency, and reverse-order MyISAM compensation.
  Ambiguous journal commits preserve completed projections and become
  `needs_review` rather than deleting a possibly committed account.
- Local Maven tests are `121/121`. A local real-MySQL test passed first register,
  replay, login, invite consumption, relationship, fixed rebate/paylog, and
  cleanup while proving client account values remain zero.
- Production direct-port and public-HTTPS smoke tests passed first register,
  consumed-code replay, generated-password login, route header, and cleanup.
  Production currently has `isEmail=1`, `isInvite=0`, `rebateLevel=0`, and
  `rebateNum=10`; no registration config was changed during testing.
- Production JAR SHA-256:
  `c7780d83c8b574c1ef23ff992bce79d5123432f48e403417db432e66f25cbf8e`;
  rollback JAR:
  `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260728-131534`.
- Nginx SHA-256:
  `b322e3cfe974e846f5c1160a07d23be5cde29ac76ed9176847c7038f03a6df97`;
  route rollback:
  `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-user-register-20260728-131629`.
- Final audit: one exact registration route, no `RegSendCode` route,
  `needs_review=0`, and zero disposable registration residue. Economy,
  advertising reward, Space, content detail, content add, and content update
  production regressions all pass.

## 2026-07-28 Account Maintenance Cutover

- Rebuilt `regConfig`, `userFoget`, `userEdit`, and `setClientId`; `SendCode`,
  `RegSendCode`, and SMS delivery remain on port 8081. The replacement reads
  and consumes the existing Java-serialized `_sendCode` and `_sendSMS` keys.
- Profile writes use an explicit field whitelist. Client attempts to change
  `name`, group, VIP, `assets`, `points`, `experience`, timestamps, or auth
  fields are ignored. The token uid must equal the submitted uid; mail, phone,
  and screen name uniqueness are checked against their correct columns.
- Password reset/edit generates PHPass `$P$B` hashes. Session revocation covers
  MySQL `authCode`, Redis session hashes, and username/mail/phone `userkey`
  aliases. This is required because the retained public `userLogin` writes a
  Redis-only session and does not populate `authCode`.
- Local Maven tests are `135/135`. Local real-MySQL smoke passed forged uid,
  protected balances, profile/payment/address fields, duplicate nickname,
  client ID, password change, token revocation, login, and zero residue.
- Production direct, mixed old-login/new-maintenance, and public HTTPS smoke
  tests passed email/phone code consumption, Redis-only authentication,
  profile writes, password edit/reset, every session revocation, and cleanup.
  All prior economy, advertising, registration, Space, and content regressions
  pass; `needs_review=0` and disposable account residue is zero.
- Production JAR SHA-256:
  `74285a0306471ea318d4521ba5052b47cd8f50fd5700fd28ebfaebf5edc90339`;
  pre-account rollback JAR:
  `/opt/starfree-replacement/starfree-replacement.jar.rollback-20260728-134736`.
  The intermediate `135839` backup contains the superseded first account build
  and must not be restored while account routes are active.
- Nginx SHA-256:
  `2fcf7e198dfcd15a1b7eca5024fd323a3540c40fa05b6bc058bf6e1d42427ba7`;
  route rollback:
  `/www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf.rollback-account-maintenance-20260728-140012`.
