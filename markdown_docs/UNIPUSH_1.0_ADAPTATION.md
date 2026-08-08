# UniPush 1.0 推送适配说明（本地记录）

> 本文档是本地工作记录，按用户要求**未推送 GitHub**。记录动态评论/点赞推送（UniPush 1.0 / 个推 REST v1）的适配、部署与验证过程。

## 1. 结论

- 推送链路已打通：App 注册 clientId → 后端 v1 鉴权 → 个推 `push_single` → 设备在线时实时送达（`successed_online`）。
- 站内信（消息中心）与推送是两条独立通道：消息中心数据来自 `starfree_inbox`，推送由后端调个推发送。
- 此前"消息中心有条目但收不到推送"的根因是后端 v1 鉴权 token 解析 bug，推送被静默放弃，且不报错。

## 2. 问题链（为什么一直收不到推送）

1. **服务器没有 unipush 配置**：`/opt/application.properties` 与运行进程环境均无 `unipush.*`，后端 `enabled=false`，一次推送请求都没发过。
2. **协议不匹配**：后端原实现是个推 REST v2（`/v2/{appid}/auth`、`/v2/{appid}/push/single/cid`），而 App 是 UniPush 1.0（v1 协议：`/v1/{appid}/auth_sign`、`/v1/{appid}/push_single`），接口不兼容。
3. **点赞无通知**：`SpaceService.like()` 只写点赞日志，不写站内信、不推送。
4. **关键 bug（v1 鉴权解析）**：个推 v1 `auth_sign` 返回的 `auth_token` 在**顶层**，而后端解析器只认 v2 的 `data.auth_token` 嵌套结构 → 鉴权返回空 token → `sendComment` 静默 return，无异常无日志。

## 3. 代码改动（commit b672615 / ae78f29，均在本地或已推分支）

- `UniPushService.java`：
  - 支持 `unipush.protocol=v1|v2`，v1 走 `auth_sign` + `push_single`，v2 保持原逻辑。
  - `auth_token` 解析兼容顶层与 `data` 嵌套两种返回结构。
  - 推送响应写 INFO 日志（含 taskid），鉴权无 token 时写 WARN，便于排障。
- `SpaceService.java`：评论通知走 `writeSpaceInbox`（站内信 + 推送 + 邮件）。点赞只写点赞日志并加计数，**不产生任何通知**（2026-08-08 按用户要求移除，站内信/推送/邮件均不发）。
- `UserInteractionService.java` / `pages/user/inbox.vue`：保留 `spaceLike` 的过滤/展示兼容（仅用于历史数据），新点赞不再产生 `spaceLike` 行。
- `application.yml`：新增 `unipush.protocol`（默认 v2，生产 v1）。
- 服务器 `/opt/starfree-replacement/start.sh`：读取 `unipush.protocol`（已带备份修改，`bash -n` 通过）。
- 测试：`UniPushServiceTest`（v1 真实返回格式断言、v2 回归）、`SpaceServiceTest`（点赞通知、自赞不通知）。

## 4. 部署记录

| 项 | 值 |
|---|---|
| 生产 JAR | 本地 commit `ae78f29` 构建，SHA256 `4f0add03...` |
| 部署前 JAR | `70c007c2...` → `f4e5c234...` → `4f0add03...` |
| 备份目录 | `/srv/lcxqy/backups/manual-unipush-20260807-224758`、`manual-authfix-20260808-000321` |
| 配置文件备份 | `/srv/lcxqy/backups/application.properties.bak-unipush-20260807-224505` |
| 服务 | `starfree-replacement.service` active，`http://127.0.0.1:18082/health` 通过 |
| 推送凭据位置 | `/opt/application.properties`（不在仓库） |

> 注意：GitHub 当前只有 `47c1d7d`，本地 `ae78f29`（鉴权修复）按用户要求**未推送**。

## 5. 服务器配置要点（/opt/application.properties）

```properties
unipush.enabled=true
unipush.protocol=v1
unipush.app-id=...
unipush.app-key=...
unipush.app-secret=<MasterSecret>
unipush.api-base=https://restapi.getui.com
```

- `unipush.app-secret` 必须填**个推 MasterSecret**（v1/v2 鉴权签名 `sha256(appkey+timestamp+mastersecret)` 用），不要填 AppSecret，两者不同。
- 凭据仅存服务器运行配置，不写入仓库；本机服务器访问信息见 `markdown_docs/private/SERVER_ACCESS.local.md`（Git 忽略）。

## 6. 已验证结果

- v1 鉴权实测：`auth_sign` 返回 `{"result":"ok","auth_token":"..."}`，凭据正确、服务器到个推连通。
- 直推测试（绕过后端）：在线设备收到（`successed_online`），用户确认收到；离线时返回 `successed_offline`（24 小时离线窗口）。
- 评论/点赞落库正常：`starfree_inbox` 出现 `spaceComment`/`spaceLike`，消息中心可见。
- 修复前后端推送：因鉴权 token 解析为空被静默放弃（无日志），已定位并修复；修复后需复测确认。

## 7. 复测步骤

1. 云云（uid=2）手机打开 App 并保持前台在线。
2. 另一设备/H5 用测试号 12345 给云云的动态点赞或评论。
3. 云云手机应立即收到推送；消息中心出现"动态点赞/动态评论"。
4. 服务器核对：`journalctl -u starfree-replacement.service -f | grep "UniPush v1 single push response"`，回执含 taskid 与送达状态。

## 8. 剩余风险与待办

- **Android 离线送达**依赖厂商通道（小米/华为/OPPO/vivo）。未配置时，App 进程被杀或长时间锁屏，离线消息要等 App 重新打开/推送进程恢复才到；正式运营前建议在 DCloud UniPush 配置厂商通道。
- **iOS 未配置**（用户明确只要 Android）。
- 前端消息中心"动态点赞"展示依赖本次重新打包的 App 版本。
- 本地 `ae78f29` 待推送 GitHub（网络原因暂停，按用户要求暂不推送）。

## 9. 回滚

```bash
systemctl stop starfree-replacement.service
cp /srv/lcxqy/backups/manual-authfix-20260808-000321/starfree-replacement.jar \
   /opt/starfree-replacement/starfree-replacement.jar
systemctl start starfree-replacement.service
curl -fsS http://127.0.0.1:18082/health
```

回滚只恢复 JAR，`/opt/application.properties` 的 unipush 配置可保留（未配置时后端自动禁用推送）。

## 10. 动态邮件通知（本地已实现，未部署）

> 代码已完成并通过测试（本地 commit `4973924` + `0097ddb`，未推送 GitHub），生产已部署（`B7BFD9A9...`）。

- 新增 `EmailNotificationService`（Spring Mail），**动态评论**时给动态作者（以及被回复的评论者）发邮件，与站内信、UniPush 并行，纯尽力而为，失败不影响评论本身；点赞不触发邮件。
- 邮件主题：`【LCYZ】你的动态收到新评论`，正文复用站内信文案（如"评论了你的动态：…"）。
- 触发条件：`notification.email.enabled=true`（默认）且 `spring.mail.username/password` 非空且收件人有 `mail` 地址，否则自动跳过。
- 服务器 `/opt/application.properties` 已预留：
  - `spring.mail.host=smtp.qq.com`
  - `spring.mail.properties.mail.smtp.port=465`、`ssl.enable=true`
  - `spring.mail.username=`、`spring.mail.password=` **目前为空**，需要用户提供 QQ 邮箱 + SMTP 授权码后填写并部署。
- 注意：云云（uid=2）账号 `mail` 为空，测试邮件请用已填邮箱的账号（如 12345 的 QQ 邮箱）验证；或先给云云账号补邮箱。
