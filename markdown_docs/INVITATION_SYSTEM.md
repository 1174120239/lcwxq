# 轻量邀请分享系统

## 业务范围

系统只做一层邀请：每个用户拥有一个可重复使用的邀请码。新用户注册时携带邀请码，注册成功后绑定邀请人，并按注册成功当时的配置给邀请人增加积分和经验。同一个新用户只能产生一次邀请奖励。

系统不包含多级返佣、提现、余额兑换、年终奖励或社区充值。旧的后台批量邀请码接口仍保留，旧邀请码注册和原有资产返利协议不改变。

## 接口

| 路径 | 鉴权 | 作用 |
|---|---|---|
| `GET/POST /SFreeInvitation/config` | 无 | 返回邀请开关、奖励数值、Android/iOS 下载地址；可传 `inviteCode` 获取邀请人公开资料。 |
| `GET/POST /SFreeInvitation/me` | `token` | 为当前用户创建或读取唯一邀请码，返回分享统计和最近邀请记录。 |
| `GET/POST /SFreeUsers/userRegister` | 注册策略 | `params.inviteCode` 可以使用用户邀请码；成功后奖励积分和经验。 |

用户邀请页使用 `utils/api.js` 的 `WEB_URL` 生成：

```text
WEB_URL#/pages/user/invitation?invite=LYXXXXXXXX
```

分享页支持未登录注册、已登录查看邀请码、复制邀请链接、直接打开下载地址和复制下载地址到浏览器。个人中心的“分享”卡片位于“签到”卡片左侧。

## 数据表

迁移文件：`backend/database/migrations/008_simple_invitation.sql`。

- `lcxqy_invitation_config`：奖励积分、奖励经验、开关和两个客户端下载地址。
- `lcxqy_invitation_codes`：`uid` 与唯一邀请码的关系。
- `lcxqy_invitation_records`：邀请成功记录和奖励快照，`invitee_uid` 唯一。

后台页面为 `admin/invitation.php`，在“功能设置 → 邀请分享”中维护配置并查看最近 50 条记录。下载地址仅接受 HTTP(S) URL；空地址表示对应平台不展示。

## 安全与一致性

- 邀请人 UID 由服务端邀请码查表得到，不能由前端传入。
- 新邀请码不消耗，允许邀请多人；奖励记录以新用户 UID 唯一，重复注册请求不会重复发奖。
- 注册奖励使用注册时的积分/经验配置快照，后续修改配置不影响历史记录。
- 旧的一次性邀请码仅在原有 `isInvite` 配置开启时按旧逻辑处理，原有资产返利保持兼容。
- 迁移必须在读取新表的 JAR 和切流前执行；生产默认不在发布脚本中自动执行迁移。

## 验收

1. 执行迁移后访问 `/SFreeInvitation/config`，确认 `code=1` 和下载地址字段存在。
2. 登录调用 `/SFreeInvitation/me`，确认同一 UID 多次请求返回相同邀请码。
3. 使用该邀请码注册一个新用户，确认邀请人积分、经验各增加一次，`lcxqy_invitation_records` 只有一行。
4. 重复提交同一注册请求或重复使用邀请码注册其他用户，确认只能为每个新用户分别奖励一次。
5. 打开分享页，分别检查 Android/iOS 的下载、复制链接提示，以及手机窄屏下按钮没有溢出。
6. 管理后台修改奖励和下载地址，刷新分享页确认新配置生效，已有邀请记录的奖励快照不变。
