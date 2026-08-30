# LCXQY API 调用手册

更新时间：2026-08-12

本手册面向前端、管理端、自动化脚本和集成开发，说明当前客户端实际使用的 API 如何调用。它覆盖 `utils/api.js` 中的 **157 个唯一 API 路径**，并额外记录支付回调和内部兼容路径，区分新后端、公网切流、混合委托和旧端能力。

这不是 OpenAPI 自动导出文件。历史接口参数并不完全统一，因此以当前前端和新后端兼容行为为准；对未重建旧接口，本文只记录已确认的参数，不伪造未知请求体。

## 1. 地址、路由和术语

| 名称 | 地址/含义 | 使用规则 |
|---|---|---|
| 生产 API | `https://api.lcxqy.cn/` | App、H5 和外部程序默认使用此地址。 |
| 本地新后端 | `http://localhost:18082/` | 只用于本机联调；真机上的 localhost 不是开发电脑。 |
| 新后端进程 | `127.0.0.1:18082` | 生产仅由 Nginx 转发，不能把端口直接暴露公网。 |
| 旧 Java API | `127.0.0.1:8081` | 未切流或未重建接口的兼容来源。 |
| PHP admin | `https://admin.lcxqy.cn/` | 运营后台和 `Api/api.php?act=...` 配置入口，不属于本 API 重建范围。 |

本文中的路由状态：

| 状态 | 含义 |
|---|---|
| `公网新` | 生产 Nginx 已精确路由到新后端。 |
| `条件新` | 匿名等特定请求已走新后端，带 token 或特殊形态仍可能走旧端。 |
| `代码新/公网旧` | 新 JAR 已有实现，但生产公开请求尚未切换，当前仍应按旧端行为验证。 |
| `旧端` | 没有独立新实现，或明确继续由旧端处理。 |

生产响应头 `X-Starfree-Backend` 可用于排查实际路由。不要根据“新 JAR 中有 Controller”推断公网站点已经切流。

## 2. 通用请求协议

### 2.1 表单编码和鉴权

默认使用 `application/x-www-form-urlencoded`。新客户端应把登录态放在
`Authorization: Bearer <token>` 请求头；新后端会把 Bearer token 映射为兼容的 `token`
参数，旧客户端暂时仍可使用表单或 query 中的 `token` 字段。不要在新代码中继续使用
GET query 传 token，避免登录态进入 Nginx 日志、浏览器历史和 Referer。

```bash
curl -sS -X POST 'https://api.lcxqy.cn/SFreeUsers/userStatus' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -H 'Authorization: Bearer 你的登录token'
```

当前登录 token 固定为 `sf2_` 加 60 位小写十六进制随机串，总长度 64 字符。安全版本上线后，旧格式
token 一律无效，用户必须重新登录；客户端不得解析 token 或从用户名、时间推导 token。

复杂参数放入 `params`，其值仍是一个 JSON 字符串，而不是 JSON request body：

```bash
curl -sS -X POST 'https://api.lcxqy.cn/SFreeContents/contentsAdd' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'token=你的登录token' \
  --data-urlencode 'params={"title":"测试标题","category":"1","tag":""}' \
  --data-urlencode 'text=正文第一行||rn||正文第二行' \
  --data-urlencode 'isMd=0'
```

多数读取接口可用 GET；写接口应优先 POST。表中标记 `GET/POST` 的历史接口当前不强制方法，但新代码接入不要把写操作做成浏览器可预取的 GET。

### 2.2 Python 调用模板

```python
import json
import requests

BASE = "https://api.lcxqy.cn/"

def form_post(path, data, token=None):
    headers = {"Authorization": f"Bearer {token}"} if token else {}
    response = requests.post(BASE + path, data=data, headers=headers, timeout=15)
    response.raise_for_status()  # 网络/网关失败才会走这里
    body = response.json()
    if isinstance(body, dict) and body.get("code") == 0:
        raise RuntimeError(body.get("msg", "业务失败"))
    return body

token = "替换为实际 token"
result = form_post("SFreeUsers/userStatus", {}, token=token)
print(result)
```

`HTTP 200` 不等于业务成功。标准业务失败仍可能是 `{"code":0,"msg":"原因"}`；只有网络错误、代理错误或未捕获异常才通常是 4xx/5xx。

### 2.3 参数约定

| 参数 | 说明 |
|---|---|
| `token` | 登录态。优先通过 Bearer Header 传输；不能由客户端传 uid 代替。 |
| `params` | JSON 字符串，常放创建/编辑对象。非法 JSON 通常会被兼容解析为空对象，然后以业务错误返回。 |
| `searchParams` | JSON 字符串，常放列表筛选条件。 |
| `key` | 历史主键别名，内容、评论、订单等场景含义不同。保留原名。 |
| `page` / `limit` | 分页，从页面实际约定开始；服务端会限制部分管理列表的最大 `limit`。 |
| `requestId` | 经济写操作的幂等键。一次用户动作生成一次；网络重试必须复用。 |
| `isMd` | `1` 表示 Markdown，`0` 表示普通正文。 |

### 2.4 响应类型

标准包装：

```json
{"code":1,"msg":"请求成功","data":{}}
```

列表一般增加 `count` 和 `total`。`count` 是当前页条数，`total` 是匹配总数；少数历史列表只有 `count`。以下路由是非包装响应，调用方必须分支处理：

| 路径 | 成功响应 |
|---|---|
| `SFreeContents/contentsInfo` | 裸文章对象 |
| `SFreeAds/adsInfo` | 裸广告对象 |
| `SFreeShop/shopInfo` | 裸商品对象，无权或不存在通常为 `{}` |
| `SFreeContents/ImagePexels` | Pexels 原生 JSON，顶层 `photos` |
| `SFreeContents/foreverblog` | 提供方原生响应 |
| `StarFreeSystem/vipTypeList` | 顶层 `vip`、`count` |
| `SFreeEconomy/signinConfig` | 裸签到配置对象 |
| `SFreeEconomy/signinStreak` | 裸 `{"leiji":n}` |
| `pay/payorderList` | 顶层 `paydata`、`count`、`total` |
| `SFreeUserlog/addLog` 的 `clock` | 顶层 `clockData` |
| `SFreeUserlog/adsServerNotify` | 裸 `{"isValid":true/false}` |

### 2.5 幂等和经济边界

下列动作必须带稳定的 `requestId`：打赏、广告购买/续期、余额或 points 调整、商城购买、VIP 购买、提现申请/审核，以及其他会影响资产、积分、库存、VIP 或广告期限的操作。

```javascript
// 前端已有 API.createRequestId(prefix)。超时重试时不要重新生成。
const requestId = API.createRequestId('shop');
```

资产 `assets`、积分 `points`、经验 `experience` 是三套独立数值。官方充值与支付回调仍是旧支付边界，不能用后台的 `userRecharge` 代替真实充值。

## 3. 系统、账号和通知

### 3.1 系统

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `health/live` | GET / 无 | 无 | 代码新 | 只验证 JVM/MVC 存活，不访问数据库。 |
| `health` | GET / 无 | 无 | 代码新 | 执行数据库就绪检查；生产应限制公网访问。 |
| `/` | GET / 无 | 无 | 代码新 | 返回服务标识，不能代替健康检查。 |
| `/**` | 任意 | 原样 | 代理 | 未精确匹配的 API 继续代理到旧端；支付部分会在经济锁下转发。 |

### 3.2 账号、登录与资料

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeUsers/regConfig` | GET/POST / 无 | 无 | 公网新 | `data` 含 `isEmail/isInvite/isPhone`。先读配置再显示注册表单。 |
| `SFreeUsers/campusIdentityOptions` | GET/POST / 无 | 无 | 公网新 | 返回当前启用的 `campuses/grades`，注册表单必须从这里取稳定选项 id，不能在前端写死。 |
| `SFreeUsers/campusIdentityManage` | GET/POST / staff | `token` | 公网新 | 返回启用和停用选项及 `userCount`，用于校区/年级管理。 |
| `SFreeUsers/campusIdentitySave` | GET/POST / staff | `token,params.id,type,name,sortOrder,enabled` | 公网新 | 新增或修改名称、排序和启用状态；不提供硬删除。改名会同步影响所有引用该 id 的用户显示。 |
| `SFreeUsers/userRegister` | GET/POST / 注册策略 | `params.name,password,mail,phone,code,inviteCode,campusId,gradeId` | 公网新 | 密码为 8-128 位且必须同时含字母和数字，并拒绝常见弱密码；`campusId/gradeId` 必填且必须当前启用；服务端决定角色和初始数值；成功后不自动登录。 |
| `SFreeUsers/userLogin` | POST / 账号密码 | `params.name,password` | 代码新/公网旧 | 安全切流后签发 `sf2_` 随机 token；Redis 启用时 TTL 是登录态权威，普通会话 90 天无操作过期并滑动续期，不能用 MySQL `authCode` 恢复过期会话。 |
| `SFreeUsers/phoneLogin` | GET/POST / 短信码 | `phone,code` | 旧端 | 验证码发送仍在旧端；登录成功兼容写 MySQL 和 Redis。 |
| `SFreeUsers/userFoget` | GET/POST / 邮箱验证码 | `params.name,code,password` | 公网新 | 路径拼写为历史 `Foget`；新密码执行统一强度策略；成功后撤销关联会话。 |
| `SFreeUsers/userEdit` | GET/POST / token | `params.uid` 和资料白名单 | 公网新 | 只能编辑自己。设置新密码时必须同时提交 `currentPassword` 并验证原密码；空 `password` 表示不改密。简介最多 255 字并保留换行；可选 `gender,birthday,showGender,showBirthday` 保存用户资料及公开开关；不得传资产、VIP 或角色字段；改密码、邮箱会撤销会话。 |
| `SFreeUsers/setClientId` | GET/POST / token | `clientId` | 公网新 | 推送标识；空字符串表示清除。 |
| `SFreeUsers/signOut` | GET/POST / token | `token` | 旧端 | 只退出当前 token，不是全设备登出。 |
| `SFreeUsers/userStatus` | GET/POST / token | `token` | 公网新 | 成功返回用户和原 token，并包含 `campusId/campus/gradeId/grade`，同时按阈值续期活跃会话；失效为 `code=0`。 |
| `SFreeUsers/userInfo` | GET/POST / 可匿名 | `uid` 或 `token` | 公网新 | 本人 token 读取本人时可返回完整资料；匿名或跨账号读取仅返回公开字段，不含邮箱、手机号、地址、余额、积分、IP、登录时间、clientId 和内部标识。公开投影包含 `campusId/campus/gradeId/grade`，以及按 `showGender/showBirthday` 控制的性别和生日。 |
| `SFreeUsers/userData` | GET/POST / 可匿名 | `uid` 或 `token` | 旧端 | 本人不传 `uid` 时评论计数包含已发布和待审核动态评论（space type=3），查看他人仅统计已发布评论；不再统计文章评论。旧字段为 `contentsNum/commentsNum/fanNum/followNum`，同时返回简写字段。 |
| `SFreeUsers/RegSendCode` | GET/POST / 注册策略 | `params.mail` | 代码新/公网旧 | 注册和修改邮箱共用；校验邮箱格式及是否已注册，发送六位验证码并写共享 Redis `starfree_sendCode<mail>`，有效期 30 分钟、同收件人 60 秒冷却。切流后为公网新。 |
| `SFreeUsers/sendSMS` | GET/POST / 手机号策略 | 旧端参数待抓包确认 | 旧端 | 短信验证码发送，不能伪造供应商请求。 |
| `SFreeUsers/SendCode` | GET/POST / 无 | `params.name` | 代码新/公网旧 | 找回密码验证码；name 可为用户名或邮箱，实际发送到账号已绑定邮箱，但兼容写入 `starfree_sendCode<用户名>`。有效期 30 分钟、同账号 60 秒冷却。切流后为公网新。 |
| `SFreeUsers/apiLogin` | GET/POST / 第三方凭据 | 旧端参数待抓包确认 | 代码禁用/公网旧 | 安全切流后拒绝调用，防止未校验的第三方身份签发登录态；重新启用前必须服务端验证提供方 code/token。 |
| `SFreeUsers/apiBind` | GET/POST / token+第三方凭据 | 旧端参数待抓包确认 | 旧端 | 社会化绑定，重建时校验 audience、过期和回调状态。 |
| `SFreeUsers/userBindStatus` | GET/POST / token | 旧端参数待抓包确认 | 旧端 | 查询第三方绑定状态。 |
| `SFreeUsers/setScan` | GET/POST / token | `codeContent` | 代码新/公网旧 | 只能批准 Redis 已存在的二维码 nonce，不能用它创建 nonce。 |

注册示例：

```python
form_post("SFreeUsers/userRegister", {
    "params": json.dumps({
        "name": "new_user", "password": "密码", "mail": "user@example.com",
        "code": "邮箱验证码", "inviteCode": "可选邀请码",
        "campusId": 2, "gradeId": 4
    }, ensure_ascii=False)
})
```

邮箱验证码发送成功只返回 `code=1,msg=邮件发送成功`，不会返回验证码。SMTP 未配置、QQ 拒绝登录、连接失败和投递失败会返回不同的业务消息；发送失败会删除刚写入的验证码，SMTP 失败保留收件人冷却，只有本地模板或运行错误才释放冷却。认证失败后默认全局退避 300 秒，退避期间不连接 SMTP；真实 SMTP 尝试默认至少间隔 1000 毫秒。邮件正文优先使用后台 `starfree_emailtemplate.verifyTemplate`，兼容 `{{userName}}` 和 `{{code}}` 占位符；模板为空时使用内置模板。

### 3.3 站内信、关注与用户管理

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeUsers/inbox` | GET/POST / token | `type,page,limit` | 公网新 | 读取不会自动设为已读；`spaceComment` 表示动态作者收到的评论或评论回复，`value` 是原动态 id，`cid` 是新动态评论 id；问答通知额外返回 `answerId`，`qaComment` 返回 `commentId`，可从消息中心直接定位到回答和评论；`lostFoundComment` 表示校园互助评论/回复，`lostFoundContact` 表示有人定向分享了联系方式，二者的 `value` 是互助信息 id、`cid` 是评论 id，并可从消息中心打开互助详情；动态点赞不产生站内信。 |
| `SFreeUsers/unreadNum` | GET/POST / token | `token` | 公网新 | 不包括旧聊天未读数。 |
| `SFreeUsers/setRead` | GET/POST / token | `id,type` | 公网新 | 传 `id` 时只标记当前用户的该条通知；未传 `id` 时支持 `all/comment/finance/system/fan` 批量已读，`comment` 同时标记文章评论、动态评论（`spaceComment`）和校园互助评论/联系方式提醒（`lostFoundComment`、`lostFoundContact`）；chat 为历史兼容，返回 0；可重复调用。 |
| `SFreeUsers/sendUser` | GET/POST / administrator | `uid,text` | 代码新/公网旧 | 写持久化 system inbox，不保证调用推送厂商。 |
| `SFreeUsers/follow` | GET/POST / token | `touid,type` | 旧端 | `type=1` 关注、`0` 取消；首次关注写粉丝通知。 |
| `SFreeUsers/isFollow` | GET/POST / token | `touid` | 旧端 | 已关注为 `code=1`，未关注为 `code=0`，不是 `data` 布尔值。 |
| `SFreeUsers/followList` | GET/POST / 无 | `uid,page,limit` | 代码新/公网旧 | 关注列表只含脱敏 `userJson`，不返回双方 IP、登录时间或 clientId。 |
| `SFreeUsers/fanList` | GET/POST / 无 | `touid,page,limit` | 代码新/公网旧 | 保留历史参数名 `touid`；用户投影遵循公开字段白名单。 |
| `SFreeUsers/userList` | GET/POST / 可选 staff | `searchParams,searchKey,order,page,limit,token` | 代码新/公网旧 | 普通和匿名结果均不返回角色、邮箱、IP、登录时间、clientId 等敏感字段；staff 管理视图保留角色和管理字段；`order` 白名单，limit 最大 50。 |
| `SFreeUsers/manageUserEdit` | GET/POST / staff | `token,params.uid或name` | 代码新/公网旧 | 白名单更新，含 `campusId/gradeId`；只能新分配启用选项，但用户已引用的停用历史选项可原样保留；改角色、密码或敏感标识会撤销所有会话。 |
| `SFreeUsers/userDelete` | GET/POST / administrator | `token,key` | 代码新/公网旧 | 删除账号/绑定/session，保留内容、评论和支付审计数据。 |
| `SFreeUsers/banUser` | GET/POST / staff | `uid,time,type,text` | 代码新/公网旧 | 写 violation、延长禁言并撤销 session；禁止越权封禁。 |
| `SFreeUsers/unblockUser` | GET/POST / administrator | `uid` | 代码新/公网旧 | 解除当前封禁，保留历史。 |
| `SFreeUsers/violationList` | GET/POST / 无 | `searchParams,page,limit` | 代码新/公网旧 | 公开列表仅返回脱敏用户信息。 |
| `SFreeUsers/userClean` | GET/POST / administrator | `uid,clean=1..5` | 代码新/公网旧 | 永久分类型清理文章、评论、动态、商品或打卡；MyISAM 无级联，先备份。 |
| `SFreeUsers/restrict` | GET/POST / administrator | `uid,type=0或1` | 代码新/公网旧 | 维护共享的 Redis silence key。 |
| `SFreeUsers/giftVIP` | GET/POST / staff | `uid,day` | 代码新/公网旧 | 赠送 VIP，不扣 assets/points，仍写零金额流水。 |
| `SFreeUsers/madeInvitation` | GET/POST / administrator | `num=1..100` | 代码新/公网旧 | 服务端生成高随机邀请码。 |
| `SFreeUsers/invitationList` | GET/POST / administrator | `searchParams.status,page,limit` | 代码新/公网旧 | limit 最大 50。 |
| `SFreeUsers/invitationExcel` | GET/POST / administrator | `limit<=10000` | 代码新/公网旧 | 返回 UTF-8 制表符文本 `.xls`，不是 JSON。 |

### 3.4 轻量邀请分享

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeInvitation/config` | GET/POST / 无 | 可选 `inviteCode` | 公网新 | 返回 `enabled/rewardPoints/rewardExperience/androidDownloadUrl/iosDownloadUrl`；传有效邀请码时附邀请人公开资料。 |
| `SFreeInvitation/me` | GET/POST / token | `token` | 公网新 | 返回当前用户唯一邀请码、成功邀请数、积分/经验累计值和最近记录；服务端从 token 得到 UID。 |

轻量邀请只奖励邀请人的积分和经验，不产生提现或多级返佣。注册时的 `params.inviteCode` 可以是用户邀请码；奖励记录按被邀请人 UID 幂等。

## 4. 内容、评论和分类

### 4.1 内容

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeContents/contentsList` | GET/POST / 可选 token | `searchParams,searchKey,order,page,limit,random,token` | 条件新 | 匿名新、带 token 旧；普通用户仅 publish；`random=1` 查询成本高。 |
| `SFreeContents/contentsInfo` | GET/POST / 可选 token | `key` 或 `cid`，可选 `isMd` | 公网新 | 成功是裸文章对象。IP+UA 900 秒只增加一次浏览量；本次计数成功时返回自增后的 views。 |
| `SFreeContents/contentsAdd` | POST / token | `params,text,isMd`，前端还传 `isSpace` | 公网新+内部委托 | 仅普通 `post/video` 由新端写；付费、草稿、动态、商品、未知类型原样委托旧端。 |
| `SFreeContents/contentsUpdate` | POST / token | `params.cid/title,text,isMd` | 公网新+内部委托 | 普通 post/video 新写；保留原 type 和 Markdown；其他形态委托旧端。 |
| `SFreeContents/contentsDelete` | GET/POST / 作者或 staff | `key` 或 `cid` | 代码新/公网旧 | 删除内容和关系，可能按配置扣经验；多表 MyISAM 写入需要对账。 |
| `SFreeContents/contentsAudit` | GET/POST / staff | `key/id` 与审核动作 | 代码新/公网旧 | 审核、经验、通知；非法或重复状态为 `code=0`。 |
| `SFreeContents/rewardList` | GET/POST / 无 | `id,page,limit` | 公网新 | 读取打赏日志；内容删除后历史日志仍可能存在。 |
| `SFreeContents/isCommnet` | GET/POST / token | `key=cid` | 代码新/公网旧 | 保留历史拼写；作者或已评论返回 `code=1`。 |
| `SFreeContents/toRecommend` | GET/POST / staff | `key,recommend=0或1` | 代码新/公网旧 | 文章推荐位，刷新 modified/cache。 |
| `SFreeContents/addTop` | GET/POST / staff | `key,istop=0或1` | 代码新/公网旧 | 仅修改置顶。 |
| `SFreeContents/addSwiper` | GET/POST / staff | `key,isswiper=0或1` | 代码新/公网旧 | 仅修改轮播资格，不验证图片。 |
| `SFreeContents/setFields` | GET/POST / 作者或 staff | `cid,name,strvalue` | 代码新/公网旧 | 固定字段 upsert；禁止保留字和任意 SQL 列。 |
| `SFreeContents/contentConfig` | GET/POST / 无 | 无 | 代码新/公网旧 | 只应公开 `allowDelete`，不得泄露 apiconfig 密钥。 |
| `SFreeContents/allData` | GET/POST / staff | `token` | 代码新/公网旧 | 后台实时统计，高频轮询成本较高。 |
| `SFreeContents/ImagePexels` | GET/POST / 无 | `page,searchKey` | 代码新/公网旧 | Pexels 原生响应；3 秒限流、6 小时缓存。 |
| `SFreeContents/foreverblog` | GET/POST / 无 | `page` | 代码新/公网旧 | 提供方原生响应，2 分钟缓存。 |

发布普通帖子示例，来自现有 `pages/user/post.vue` 的协议：

```python
article = form_post("SFreeContents/contentsAdd", {
    "token": token,
    "params": json.dumps({
        "title": "至少五个字符的标题", "category": "1", "tag": "",
        # 新后端可选："type": "post"；未写时按普通帖子兼容
    }, ensure_ascii=False),
    "text": "至少二十字正文第一行||rn||正文第二行",
    "isMd": "0",
    "isSpace": "0",
})
```

标题在新端最长 200 字，正文最长 60000 字。Markdown 正文传 `isMd=1`，服务端使用 `<!--markdown-->` 标识保存；客户端换行使用 `||rn||`。发布成功后不要假定返回 `cid`，需要时按业务标记或列表回读确认。管理后台直接改数据库不会清新后端缓存，因此页面收到空列表时必须直接采用空结果，不能保留浏览器旧数据。

### 4.2 评论

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeComments/commentsList` | GET/POST / 可选 token | `searchParams,searchKey,order,page,limit,token` | 条件新 | 匿名新、带 token 旧；返回 `count/total`。 |
| `SFreeComments/commentsAdd` | GET/POST / token | `params,text,pic` | 代码新/公网旧 | 校验目标和审核；可能返回 waiting，不能立刻当作公开评论。 |
| `SFreeComments/commentsDelete` | GET/POST / 作者或 staff | `key` 或 `coid` | 代码新/公网旧 | 修正评论数，按配置处理经验。 |
| `SFreeComments/commentsAudit` | GET/POST / staff | `key,type` | 代码新/公网旧 | 更新可见性、计数、经验；重复审核拒绝。 |

### 4.3 分类和标签

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeMetas/metasList` | GET/POST / 无 | `searchParams,searchKey,order,page,limit` | 公网新 | 仅有当前页 `count`，不是总数。 |
| `SFreeMetas/metaInfo` | GET/POST / 无 | `key`、`mid` 或 `slug` | 代码新/公网旧 | id 优先；不存在为 `code=0`。 |
| `SFreeMetas/selectContents` | GET/POST / 可选 token | 内容筛选、分页、token | 条件新 | 匿名新、带 token 旧；仅当前页 count。 |
| `SFreeMetas/addMeta` | GET/POST / administrator | `params.name,slug,type` | 代码新/公网旧 | 仅 category/tag；管理端新增“话题”本质上传 `type=tag`，会进入动态官方话题池。 |
| `SFreeMetas/editMeta` | GET/POST / administrator | `params.mid` 和白名单字段 | 代码新/公网旧 | 不允许改 type/count；父级不得形成循环。 |
| `SFreeMetas/deleteMeta` | GET/POST / administrator | `id` | 代码新/公网旧 | 清 meta、文章关系、动态话题关系、话题关注和话题扩展资料，不删除文章或动态主行。 |
| `SFreeMetas/toRecommend` | GET/POST / administrator | `key,recommend=0或1` | 代码新/公网旧 | 是分类推荐，不是文章推荐。 |

## 5. 收藏、打赏、动态和广告

### 5.1 互动日志与奖励

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeUserlog/markList` | GET/POST / token | `page,limit` | 代码新/公网旧 | 收藏内容列表，含删除所需 `logid`；total 可含已删文章历史。 |
| `SFreeUserlog/isMark` | GET/POST / token | `cid` | 代码新/公网旧 | `data.isMark/logid`；未收藏时 logid=-1。 |
| `SFreeUserlog/addLog` | GET/POST / token | `params.type,cid,num,toid`，`requestId` | 公网新 | type 只允许 `mark/reward/likes/clock`。打赏必须正数 num 和 requestId。 |
| `SFreeUserlog/removeLog` | GET/POST / token | `key`（日志 id） | 代码新/公网旧 | 普通用户只能删自己的收藏，不能撤销打赏或签到。 |
| `SFreeUserlog/orderList` | GET/POST / token | `token` | 代码新/公网旧 | token 决定买家，最多 60 条；不信任请求中额外传入的 uid。 |
| `SFreeUserlog/orderSellList` | GET/POST / token | `page,limit` | 代码新/公网旧 | token 决定卖家；买家邮箱/地址仅在卖家鉴权结果中返回。 |
| `SFreeUserlog/dataClean` | GET/POST / administrator | `clean=1..8` | 代码新/公网旧 | 永久清理，经济锁内执行；selector 6 每次最多 500 个严格空闲账号。 |
| `SFreeUserlog/adsGift` | GET/POST / token | `appkey` | 公网新 | 创建待完成奖励，返回 adpid/logid，尚不加 assets。 |
| `SFreeUserlog/adsGiftNotify` | GET/POST / token | `logid` | 公网新 | 客户端确认模式；同一完成 logid 不重复加资产。 |
| `SFreeUserlog/adsServerNotify` | GET/POST / 厂商签名 | `trans_id,user_id,sign` | 公网新 | 返回裸 isValid；trans_id 全局幂等；未配置密钥必须拒绝。 |

`addLog` 中：`mark` 和 `likes` 都持久去重；`reward` 从打赏者 assets 扣款并给作者入账；`clock` 是旧的每日打卡，返回 `clockData`。外部广告回调只应由已验证厂商调用，不能把 appkey 或签名密钥放到前端。

### 5.2 动态（Space）

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeSpace/addSpace` | GET/POST / token | `text,pic,type,toid,onlyMe,topicIds,poll` | 公网新 | type 仅 0..5；正文允许受限 Markdown 和 `[color]`/`[align]`/`[u]` 扩展标记，详情按安全富文本渲染；最多 9 张图片按 `pic` 的 `||` 顺序保存。其余校验、投票和审核规则不变。 |
| `SFreeSpace/editSpace` | GET/POST / 作者或 staff | `id,text` 和可选字段，含 `topicIds` | 公网新 | 类型不可变；staff 编辑保留作者，不重复发经验；传 `topicIds=0` 表示清空该动态的话题。 |
| `SFreeSpace/spaceInfo` | GET/POST / 可选 token | `id,token` | 公网新 | 统一执行私密、待审、锁定可见性；返回对象含 `topics`、`featured`、当前有效的 `pinType`、配置值 `pinConfiguredType` 及置顶顺序/时间；成功读取会增加浏览量。 |
| `SFreeSpace/spaceList` | GET/POST / 可选 token | `searchParams,searchKey,order,page,limit,isManage` | 公网新 | `isManage` 只对 staff 有效；普通列表默认排除 type=3 回复；兼容单个 `topicId`，`topicIds` 可传数组或逗号分隔 id，去重后最多 3 个并按 AND 匹配。`searchParams.featured=0/1` 可筛选精华，`excludePresented=1` 排除当前有效的列表/横幅置顶，用于首页去重。 |
| `SFreeSpace/followSpace` | GET/POST / token | `page,limit` | 公网新 | 只读已关注用户的公开、非回复动态。前端别名见下一行。 |
| `SFreeSpace/myFollowSpace` | GET/POST / token | `page,limit` | 公网新 | `followSpace` 的前端别名；仅当前页 count。 |
| `SFreeSpace/spaceDelete` | GET/POST / 作者或 staff | `id` | 公网新 | 只删主行，不级联历史回复、转发、spaceLike，不扣经验。 |
| `SFreeSpace/spaceLikes` | GET/POST / token | `id` | 公网新 | uid+space id 持久切换点赞状态；返回 data=1 表示已点赞，data=0 表示已取消点赞。 |
| `SFreeSpace/spaceReview` | GET/POST / staff | `id,type` | 公网新 | `type=1` 通过，`0` 拒绝并删主行，写系统通知。 |
| `SFreeSpace/spaceLock` | GET/POST / staff | `id,type` | 公网新 | `type=2` 锁定、`1` 解锁；待审不可锁，锁定后不能回复或转发，并清除精华和置顶展示状态。 |
| `SFreeSpace/spacePresentation` | POST / staff | `token,id,featured,pinType,pinOrder,pinStartTime,pinEndTime` | 代码新/公网旧 | `featured` 为 0/1；`pinType=0/1/2` 分别表示普通、列表置顶、横幅置顶。仅公开、已发布、非回复动态可启用；开始/结束时间为 Unix 秒，0 表示不限制，结束必须晚于开始和当前时间。字段独立于文章推荐/置顶。 |
| `SFreeSpace/spacePresentationList` | GET/POST / 可选 token | `token` | 代码新/公网旧 | 返回 `data.banner` 和 `data.list`，各最多 3 条，仅含仍在有效期内的公开已发布主动态，按 `pinOrder`、修改时间和 id 稳定倒序。 |
| `SFreeSpace/topicList` | GET/POST / 可选 token | `token,searchKey` | 公网新 | 按名称/描述关键词模糊搜索，返回 `data.all/hot/official/followed`；每项含动态数、关注数和当前用户关注状态。关注列表只在登录后返回。 |
| `SFreeSpace/topicCreate` | GET/POST / token | `token,name` | 公网新 | 用户自建话题；名称自动去掉首尾 `#` 和空白，只允许中英文、数字、下划线、短横线，1-24 字；创建后自动关注。 |
| `SFreeSpace/topicFollow` | GET/POST / token | `token,mid,type` | 公网新 | `type=1` 关注，`type=0` 取消；幂等处理，不会重复插入关注。 |
| `SFreeSpace/userReplies` | GET/POST / 可选 token | `uid,page,limit,token` | 代码新/公网旧 | 按时间倒序返回指定用户发表的动态评论；未传 uid 时必须登录。每项以 `originalState=visible/deleted/forbidden` 区分原动态，并在可见时返回作者和最多 180 字摘要。 |
| `SFreeSpace/reportAdd` | POST / token | `id,reason,detail` | 代码新/公网旧 | 只能举报公开主动态，不能举报自己的动态；`reason` 为广告营销、人身攻击、色情低俗、违法违规或其他。同一用户对同一动态只保留一条举报，重复提交返回业务失败。 |
| `SFreeSpace/reportList` | GET/POST / staff | `token,status,source,decision,contentStatus,page,limit` | 代码新/公网旧 | 默认返回举报队列；`status=0` 待处理、`1` 已处理、`2` 已驳回。`source=ai` 时返回每条动态的最新 AI 审核记录，可按 `decision=approved/rejected/error` 和当前动态状态筛选。 |
| `SFreeSpace/reportReview` | POST / staff | `token,id,action,note,source` | 代码新/公网旧 | 普通举报不传或传 `source=report`；`action=delete` 处理举报并删除原动态，`action=dismiss` 驳回举报。AI 记录传 `source=ai`，`action=approve` 公开动态，`action=hide` 隐藏但不删除动态；每次人工改判追加独立操作记录。 |
| `SFreeSpace/pollVote` | POST / token | `pollId,optionIds` | 代码新/公网新 | 对公开主动态匿名投票；单选只能 1 项，多选不得超过上限；同一账号不可修改或重复提交。不返回参与者身份。成功响应中的 `totalVotes/options[].votes/options[].selected` 用于渲染结果条。 |

动态话题复用 `starfree_metas.type='tag'` 作为话题目录，但动态和话题的关系不走文章用的 `starfree_relationships`，而是写入 `starfree_space_topics`，避免文章 cid 和动态 id 数字碰撞。后台“分类/话题”页面的“新增话题”会创建官方话题；用户在发布页输入的新话题会创建为用户话题，并写 `starfree_topic_meta.is_official=0`。后台将该话题设为推荐后，也会出现在官方话题区。

动态举报表由 `backend/database/migrations/009_space_reports.sql` 创建。未执行迁移前不能启用上述三个举报路由；发布默认不执行该迁移。

动态精华、列表置顶和横幅置顶字段由 `backend/database/migrations/012_space_presentation.sql` 添加。该迁移是幂等的增量迁移，不改变旧 API 已使用字段；未执行迁移前不能部署包含上述展示字段查询的新后端，通用发布流程不会自动执行迁移。

AI 风险审核由原 `starfree_apiconfig.spaceAudit` 作为总门控。总开关关闭时动态和提问直接发布，AI 配置与历史保留但不执行；总开关开启后，动态和提问先写隐藏状态，按后台的动态/提问子开关交给 AI，通过后公开，拒绝或服务异常时保留内容并等待人工改判。图片和视频动态只审核附带文字，不做视觉识别，也不会仅因存在附件转人工。动态评论先公开，按北京时间配置每日扫描；巡检范围还包括问答回答和问答评论，风险项可配置自动隐藏或只记录。统一审核历史、人工操作日志和每日总结由 `backend/database/migrations/013_ai_moderation_complete.sql` 创建。

### 校园问答

校园问答使用独立表，不复用帖子、动态或文章评论。普通登录用户可从发布面板提交问题，服务端强制进入待审核状态；管理员或编辑在管理控制台审核、编辑和发布。登录用户也可以回答、点赞回答、评论回答和回复评论。

| 接口 | 方法/权限 | 参数 | 落点 | 说明 |
|---|---|---|---|---|
| `SFreeQa/questionList` | GET/POST / 无 | `page,limit,keyword,recommended` | 代码新/公网旧 | 只返回已发布问题；`recommended=1` 只返回首页推荐问答，主页不得回退混入普通问答；推荐、排序值和更新时间共同决定顺序。 |
| `SFreeQa/questionAdd` | POST / token | `params={title,description,topic,imageUrls}` | 代码新/公网旧 | 标题 4-160 字，说明最多 5000 字，说明允许受限 Markdown；`imageUrls` 最多 9 个并按数组顺序保存，首图兼容回填 `coverUrl`。审核和重复提交规则不变。需先执行迁移 015。 |
| `SFreeQa/questionInfo` | GET/POST / 可选 token | `id,token` | 代码新/公网旧 | 普通用户只能读取已发布问题；staff 可预览停用问题。 |
| `SFreeQa/answerList` | GET/POST / 可选 token | `questionId,page,limit,sort,token` | 代码新/公网旧 | `sort=latest` 按时间，其他值按点赞和时间；登录时返回 `isLiked`。 |
| `SFreeQa/answerAdd` | POST / token | `params={questionId,text}` | 代码新/公网旧 | 回答至少 4 字、最多 5000 字；20 秒内相同回答拒绝重复提交。 |
| `SFreeQa/answerEdit` | POST / owner/staff | `params={id,text}` | 代码新/公网旧 | 仅回答作者或 staff 可修改。 |
| `SFreeQa/answerDelete` | POST / owner/staff | `id,token` | 代码新/公网旧 | 逻辑删除回答并清理点赞关系，不硬删历史正文。 |
| `SFreeQa/answerLike` | POST / token | `answerId,token` | 代码新/公网旧 | uid+answer id 唯一，重复点击在点赞和取消之间切换并返回最新计数。 |
| `SFreeQa/commentList` | GET/POST / 无 | `answerId,page,limit` | 代码新/公网旧 | 根评论分页，返回页内完整 `children` 回复树；前端最多展示 3 层，后续回复按时间平铺。 |
| `SFreeQa/commentAdd` | POST / token | `params={answerId,parentId,text}` | 代码新/公网旧 | `parentId=0` 评论回答；非 0 回复评论；至少 1 字，20 秒内相同内容防重复。问答通知响应同时提供 `answerId` 与 `commentId`，可直接定位回复。 |
| `SFreeQa/commentDelete` | POST / owner/staff | `id,token` | 代码新/公网旧 | 作者或 staff 可删除；删除根评论时隐藏整条回复树。 |
| `SFreeQa/questionManage` | GET/POST / staff | `token,page,limit,keyword,status` | 代码新/公网旧 | 后台读取发布和停用问题，支持关键词与状态筛选。 |
| `SFreeQa/questionSave` | POST / staff | `params={id,title,description,topic,coverUrl,recommended,sortOrder,status}` | 代码新/公网旧 | id 为空新增，否则修改；问题标题至少 4 字。 |
| `SFreeQa/questionStatus` | POST / staff | `id,status,token` | 代码新/公网旧 | `status=1` 发布，`0` 隐藏；不提供硬删除接口。AI 审核过的提问每次状态改判都会写入人工操作日志。 |

问答通知写入 `starfree_inbox`：新回答使用 `qaAnswer`，回答评论或评论回复使用 `qaComment`，`value` 保存问题 id。新通知的 `qaComment.cid` 保存评论 id，接口同时返回 `answerId/commentId`；旧通知仍按原 `cid=answerId` 兼容读取。消息中心和 UniPush 点击可回到对应回答与评论。数据库迁移为 `backend/database/migrations/007_campus_qa.sql`，未执行迁移前不能启用这些路由。

### QQ 动态助手（NapCat 个人账号）

以下接口由 AstrBot 插件调用，插件运行在 NapCat 个人 QQ 账号接入链路上，不是 QQ 官方机器人号。统一使用 `botSecret` + `platform=qq`，除绑定页外返回旧协议 `{code,msg,data}`。Bot 只开放动态能力，不暴露帖子/文章接口。

| 接口 | 方法/权限 | 参数 | 落点 | 说明 |
|---|---|---|---|---|
| `SFreeBot/config` | GET/POST / Bot secret | `botSecret,platform` | 公网新 | 返回 Bot 开关、工具开关、DeepSeek 模型、群同步配置、QQ 空间发布模式、立即发布任务状态、`chatInGroups` 群聊普通对话开关和 `commentSpace=true` 能力标记；不返回 DeepSeek Key。插件未读到评论标记时不得提交 `type=3`，避免旧后端把评论误发为普通动态。 |
| `SFreeBot/chat` | POST / Bot secret | `message` 或 `messages` JSON | 公网新 | 后端代理 DeepSeek Chat Completions；插件通过 `messages` 传入受限意图规划提示和最近对话，DeepSeek Key 只在后台配置表。 |
| `SFreeBot/bindChallenge` | GET/POST / Bot secret | `qqUserId,platform` | 公网新 | 生成短期一次性 QQ 绑定链接。 |
| `SFreeBot/bindPage` | GET / 无 | `token` | 公网新 | 独立 HTML 登录页；只用于绑定 QQ，不创建普通登录态。 |
| `SFreeBot/bindLogin` | POST / 无 | `token,account,password` | 公网新 | 校验论坛账号密码并写 `lcxqy_bot_bindings`；不调用 userLogin、不改 `authCode`、不写 Redis session。 |
| `SFreeBot/meStatus` | GET/POST / Bot secret | `qqUserId,platform` | 公网新 | 返回绑定状态、脱敏用户快照、积分/经验/余额和签到连续天数。 |
| `SFreeBot/signin` | GET/POST / Bot secret + 绑定 | `qqUserId,requestId` | 公网新 | 对绑定 uid 执行签到；沿用 uid+日期幂等。 |
| `SFreeBot/addSpace` | GET/POST / Bot secret + 绑定 | `qqUserId,requestId,text,pic,topicIds,onlyMe,type,toid,images` | 公网新 | 默认发布普通动态并强制 `type=0,toid=0`；带图动态使用 multipart，可重复提交最多 9 个 `images` 文件，单图最大 8 MB，后端经旧端上传服务换成论坛永久 URL 后写入 `pic`。群内引用云云同步动态评论时仅允许 `type=3` 且 `toid` 为正整数，强制公开并忽略图片/话题。两种操作都复用 `SpaceService` 的目标可见性、锁定、审核、违禁词、经验门槛、防刷、重复评论、奖励和通知逻辑。 |
| `SFreeBot/updateProfile` | GET/POST / Bot secret + 绑定 | `qqUserId,requestId,screenName,introduce,avatar,campusId,gradeId` | 公网新 | 只允许普通资料白名单字段；不允许改密码、邮箱、手机、积分、余额、经验、VIP、角色。 |
| `SFreeBot/registerGroup` | GET/POST / Bot secret | `groupId,groupName,unifiedMsgOrigin(可选)` | 公网新 | 登记 QQ 群动态同步目标；来源为空时自动生成 `lcxqy_onebot:GroupMessage:<groupId>`，后台只需维护群号、群名、开关和摘要策略。 |
| `SFreeBot/latestSpaces` | GET/POST / Bot secret | `groupId,afterId,limit` | 公网新 | 拉取公开、已审核、非私密、非回复动态，返回 H5 链接、摘要、图片和作者信息。 |
| `SFreeBot/delivery` | GET/POST / Bot secret | `groupId,spaceId,status,messageId,error` | 公网新 | 记录群投递结果；只有 `status=success` 推进群游标。 |
| `SFreeBot/qzoneBatch` | GET/POST / Bot secret | `botSecret,platform` | 公网新 | 返回 `scheduled/realtime` 发布模式、立即发布任务 token、当天是否已发布、图片模板配置和一批公开已审核非回复动态。游标为 0 时取最新一批，之后（包括立即任务）只取成功游标后的增量；不足批量上限时返回实际可发布数量。 |
| `SFreeBot/qzoneDelivery` | GET/POST / Bot secret | `status,maxSpaceId,tid,error,publishNowToken` | 公网新 | 回写 QQ 空间发布结果；成功时推进独立空间游标并记录当天、TID 和成功时间，匹配的一次性立即任务同时标记完成；失败只记录错误且保留任务重试。 |

QQ 空间由 AstrBot 插件通过 NapCat `get_cookies` 获取个人 QQ 空间会话，再调用 QQ 空间图片上传和说说发布 HTTP 接口，不使用 NapCat 不支持的 `send_qzone_msg`，也不使用 QQ 官方机器人接口。同一批动态每条生成一张带 P 编号的 1080x1350 PNG，并在一条说说中发布；正文只使用后台填写的简短文案。`scheduled` 模式按 `Asia/Shanghai` 每天定时一次，`realtime` 模式发现游标后有新动态即发布；后台“立刻发布”生成一次性任务。远程原图只允许公网 HTTP(S)，单图限制 8 MB。

数据库迁移：`backend/database/migrations/006_qqbot_dynamic_ai.sql`。

发布动态并绑定话题示例：

```python
form_post("SFreeSpace/addSpace", {
    "token": token,
    "text": "今天在校园里捡到一卡通",
    "pic": "",
    "type": "0",
    "onlyMe": "0",
    "topicIds": "12,18"
})
```

按话题读取动态示例：

```python
form_post("SFreeSpace/spaceList", {
    "token": token,
    "page": "1",
    "limit": "10",
    "searchParams": json.dumps({"topicIds": [12, 18]})
})
```

上例只返回同时包含话题 12 和 18 的动态；数组为空或未传话题条件时返回普通全量动态。

### 5.3 匿名动态

匿名动态是原 StarPro `ng_music` 插件功能的本土化实现（用户要求加入，非旧插件重建）。匿名
动态仍是 `starfree_space` 的普通动态，只是 uid 指向运营配置的专用匿名账号；真实发布者只
保存在 `starfree_anonymous_posts` 映射表，任何公开接口都不输出映射关系。

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeAnonymous/config` | GET/POST / 无 | 无 | 公网新 | 只返回 `data.enabled`，不泄露匿名账号身份；`enabled=false` 时前端直接提示未开放。 |
| `SFreeAnonymous/post` | GET/POST / token | `token,type,text,pic,topicIds` | 公网新 | 兼容旧前端 GET 表单；与 addSpace 同一套动态校验（经验门槛、实名/蓝V、违禁词、防刷、发布限额，均按真实用户计算）；type 仅 0/4，onlyMe/toid 强制为 0；`review=1` 或全局动态审核开启时 status=0（待审核），否则 status=1；成功 `data={status}`。不发放动态经验。 |
| `SFreeAnonymous/owner` | GET/POST / 动态主人或 staff | `token,sid`（兼容 `id/cid`） | 公网新 | 非匿名动态返回“该动态不是匿名动态”；只有动态主人或 staff 能查到真实发布者，返回 `data={uid,name,screenName}`，避免匿名身份被枚举。App“动态管理”页的“真实发布者”按钮即调用此接口。 |
| `SFreeAnonymous/admin/config` | GET/POST / administrator | `token,fid,review` | 公网新 | GET 返回完整配置及匿名账号 `anonymousName/anonymousExists`；POST 校验匿名账号存在、`review∈{0,1}` 后更新。 |

配置字段：

- `fid`：匿名发布账号 UID，`0` 表示未开放匿名动态。
- `review`：`1` 匿名动态进入待审核，`0` 按全局动态审核规则直接发布。

前端入口：首页发布面板“匿名动态”，复用 `pages/space/post` 发布页（`?anonymous=1`）。
数据库迁移：`backend/database/migrations/005_ng_music_anonymous.sql`。

### 5.4 广告

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeAds/adsConfig` | GET/POST / 无 | 无 | 公网新 | 返回三类广告价格和容量。 |
| `SFreeAds/adsList` | GET/POST / 可选 token | `searchParams,searchKey,page,limit,token` | 条件新 | 匿名新、带 token 旧；匿名仅看有效广告。 |
| `SFreeAds/adsInfo` | GET/POST / 可选 token | `id` | 代码新/公网旧 | 成功为裸广告；所有者/staff 可看非公开状态。 |
| `SFreeAds/addAds` | GET/POST / token | `params.day`、`requestId` | 公网新 | 从 assets 扣费；普通用户待审；价格/容量在锁内重查。 |
| `SFreeAds/editAds` | GET/POST / 所有者或 staff | `params.aid` 和广告字段 | 代码新/公网旧 | 普通用户编辑后重置待审；不续期，不扣费。 |
| `SFreeAds/deleteAds` | GET/POST / 所有者或 staff | `id` | 代码新/公网旧 | 删除不退款。 |
| `SFreeAds/auditAds` | GET/POST / staff | `id` | 代码新/公网旧 | 只设 status=1，不做退款。 |
| `SFreeAds/renewalAds` | GET/POST / administrator | `id,day,requestId` | 公网新 | 管理赠送天数，不从用户扣 assets，写流水。 |

广告 type 为 0..2，购买天数 1..3650。广告购买、奖励和打赏均需按经济幂等规则处理。

### 5.5 校园互助

校园互助替代客户端原商城入口，但不复用商城订单、余额、积分、库存或 VIP 逻辑。公开信息和评论可匿名读取；默认达到 Lv2（当前为 50 经验）的登录用户才能发布、评论及定向发送/查看 QQ，最低等级和功能开关由管理端配置。等级和 QQ 均由后端从 token 对应用户重读，客户端字段不可信。

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeLostFound/config` | GET/POST / 可选 token | `token` | 代码新 | 公开配置；带 token 时返回 `currentLevel/eligible`。默认最低 Lv2。 |
| `SFreeLostFound/itemList` | GET/POST / 无 | `kind,category,state,keyword,page,limit` | 代码新 | 只列出审核通过且未过期的信息；kind 1 求助、2 提供帮助，category 1..5。 |
| `SFreeLostFound/itemInfo` | GET/POST / 可选 token | `id,token` | 代码新 | 公开 active/resolved；待审、拒绝和关闭仅 owner/staff 可读。 |
| `SFreeLostFound/itemAdd` | GET/POST / Lv 门槛 | `token,params={kind,category,title,description,imageUrl,imageUrls,location,occurredAt}` | 代码新 | 说明允许受限 Markdown；`imageUrls` 最多 9 个并按数组顺序保存，首图兼容回填 `imageUrl`；uid/status/review 字段服务端派生。需先执行迁移 015。 |
| `SFreeLostFound/itemEdit` | GET/POST / owner/staff | `token,params.id` 与内容字段 | 代码新 | 普通用户编辑后按配置重新审核；不能修改 uid 或伪造状态。 |
| `SFreeLostFound/itemStatus` | GET/POST / owner/staff | `token,id,action=resolve/reopen` | 代码新 | 只有 active 可解决、resolved 可重开；重开按审核配置进入 active/pending。 |
| `SFreeLostFound/itemDelete` | GET/POST / owner/staff | `token,id` | 代码新 | 软关闭为 status=4，不物理删除信息和审计记录。 |
| `SFreeLostFound/itemManage` | GET/POST / token | `token,status,uid,page,limit` | 代码新 | 普通用户强制只读自己的数据；staff 可按 uid/status 管理。 |
| `SFreeLostFound/itemAudit` | GET/POST / staff | `token,id,action=approve/reject,reason` | 代码新 | 通过只接受待审/已拒绝，拒绝只接受待审/进行中且理由必填；状态变化写 append-only audit，并尽力发送站内通知。 |
| `SFreeLostFound/commentList` | GET/POST / 无 | `itemId` | 代码新 | 公开树形评论；响应永不包含 QQ、邮箱或联系方式授权状态。 |
| `SFreeLostFound/commentAdd` | GET/POST / Lv 门槛 | `token,itemId,parentId,text` | 代码新 | 只允许未过期 active 信息；评论 2..1000 字，10 秒重复拦截；成功后向互助发布者发送站内消息、UniPush（已配置时）和邮件（SMTP 已配置时），回复还通知被回复评论者。 |
| `SFreeLostFound/commentDelete` | GET/POST / owner/staff | `token,commentId` | 代码新 | 软删除评论，同时撤销附着在该评论上的联系方式授权。 |
| `SFreeLostFound/contactShare` | GET/POST / Lv 门槛 | `token,itemId,commentId` | 代码新 | 单向授权；发布者和该评论作者之间有效。QQ 从发送者绑定的 `@qq.com` 邮箱解析，不接受客户端 QQ/receiverUid；成功后仅向接收者发送站内消息、UniPush（已配置时）和不含 QQ 明文的邮件提醒。 |
| `SFreeLostFound/contactAccess` | GET/POST / Lv 门槛 | `token,itemId` | 代码新 | 只向当前接收者返回收到的 QQ；发送者投影仅含“已发送”，不回显 QQ。其他用户无权读取。 |
| `SFreeLostFound/configManage` | GET/POST / staff | `token` | 代码新 | 管理端读取完整互助设置。 |
| `SFreeLostFound/configSave` | GET/POST / administrator | `token,params` | 代码新 | 设置开关、最低等级、审核、QQ 授权、每日上限和有效期；完全免费与私密可见性不是可配置项。 |

联系方式授权必须关联一条公开评论。评论者只能把自己的 QQ 发给发布者；发布者只能把自己的 QQ 发给选中的评论者。授权表不保存 QQ 明文，接收者读取时从发送者当前绑定邮箱解析。管理员接口也不提供批量读取 QQ 的能力。

## 6. 钱包、签到、商城、VIP 与支付

### 6.1 钱包、提现和财务读取

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeUsers/userRecharge` | GET/POST / staff | `key,num,type,rechargeType,requestId` | 公网新 | `type=0` 加、`1` 减；`rechargeType=0` assets、`1` points；不是官方充值。 |
| `SFreeUsers/userWithdraw` | GET/POST / token | `num,requestId` | 公网新 | 创建待审提现记录，申请时不扣 assets。 |
| `SFreeUsers/withdrawList` | GET/POST / token | `searchParams.page,limit` | 公网新 | 普通用户只看自己；administrator 可看全站并见收款 pay。 |
| `SFreeUsers/withdrawStatus` | GET/POST / administrator | `key,type` | 公网新 | `type=1` 通过且扣 assets，`0` 拒绝；不处理线下实际付款。 |
| `pay/payorderList` | GET/POST / token | `token` | 公网新 | 最近 30 条，顶层 `paydata/count/total`。 |
| `pay/financeList` | GET/POST / administrator | `searchParams.page,limit` | 公网新 | 全站 paylog，可按 uid/status/paytype 筛选。 |
| `pay/financeTotal` | GET/POST / administrator | `token` | 公网新 | recharge/trade/withdraw/income 汇总；新增 paytype 要同步统计规则。 |

提现状态：`cid=-1` 待审、`cid=0` 已通过、`cid=-2` 已拒绝。批准提现是扣款时点，外部转账的成功与否必须另行留痕。

### 6.2 七日签到

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeEconomy/signinConfig` | GET/POST / 无 | 无 | 公网新 | 裸 `assets_1day..7day`、`experience_1day..7day`。 |
| `SFreeEconomy/signinStreak` | GET/POST / token | `token` | 公网新 | 裸 `{leiji:n}`，只读连续天数。 |
| `SFreeEconomy/signin` | GET/POST / token | `token` | 公网新 | uid+日期天然幂等；生产时区必须是 Asia/Shanghai。 |

### 6.3 商城和 VIP

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeShop/buyShop` | GET/POST / token | `sid,isIntegral,fid,requestId` | 公网新 | assets 或 points 抵扣；库存、卖家收入、折扣、日志统一处理。 |
| `SFreeShop/isBuyShop` | GET/POST / token | `sid` | 公网新 | 已购买 `code=1`，未购买 `code=0`。 |
| `SFreeShop/buyVIP` | GET/POST / token | `day,requestId` | 公网新 | 从 assets 扣 vipPrice 并顺延 VIP。 |
| `SFreeShop/buyVIPpackage` | GET/POST / token | `id,requestId` | 公网新 | 服务端重读套餐价格和天数，客户端不得传或信任价格。 |
| `SFreeShop/vipInfo` | GET/POST / 无 | 无 | 公网新 | `vipDiscount,vipPrice,scale,vipDay`；不含套餐列表。 |
| `SFreeShop/shopList` | GET/POST / 可选 token | `searchParams,searchKey,order,page,limit` | 代码新/公网旧 | 列表有 count/total，筛选排序白名单。 |
| `SFreeShop/shopInfo` | GET/POST / 可选 token | `key` 或 `id` | 代码新/公网旧 | 裸商品；待审仅 owner/staff；商品 value 只给 owner/staff/已购者。 |
| `SFreeShop/addShop` | GET/POST / token | `params,text,isMd,isSpace` | 代码新/公网旧 | uid/status/cid/sellNum 服务端派生；类型 1..4。 |
| `SFreeShop/editShop` | GET/POST / owner/staff | `params.id` 和白名单字段 | 代码新/公网旧 | owner 编辑会重新审核；不能改 uid/cid/sellNum/created/status。 |
| `SFreeShop/deleteShop` | GET/POST / owner/staff | `key` 或 `id` | 代码新/公网旧 | 不级联购买日志；已售商品宜归档而非删除。 |
| `SFreeShop/auditShop` | GET/POST / staff | `key,type,reason` | 代码新/公网旧 | `type=0` 通过、`1` 拒绝；拒绝理由必填，重复操作幂等。 |
| `SFreeShop/mountShop` | GET/POST / owner/staff | `sid,cid` | 代码新/公网旧 | `cid=-1` 卸载；普通用户只能挂自己的 post/video。 |
| `StarFreeSystem/vipTypeList` | GET/POST / 无 | 无 | 旧端 | 顶层 `vip/count`；购买时以服务端套餐数据为准。 |

商城购买示例：

```python
request_id = "shop-20260731-0001"  # 超时重试时保持不变
form_post("SFreeShop/buyShop", {
    "token": token, "sid": "123", "isIntegral": "0",
    "fid": "0", "requestId": request_id,
})
```

### 6.4 官方支付和卡密（旧端）

以下路径继续使用旧支付逻辑，生产可能先经过新后端取得经济锁后再转发。它们**不是已重建支付接口**；因没有旧端源码，本手册不猜测具体支付签名、商户字段或回调格式。

| 路径 | 已知用途 | 路由 |
|---|---|---|
| `pay/scancodePayStar` | 支付宝当面付 | 旧端/锁代理 |
| `pay/WxPayStar` | 官方微信支付 | 旧端/锁代理 |
| `pay/tokenPay` | 卡密充值 | 旧端/锁代理 |
| `pay/EPayStar` | 易支付 | 旧端/锁代理 |
| `pay/qrCodeStar` | 充值二维码生成 | 旧端/锁代理 |
| `pay/tokenPayStar` | 卡密充值记录 | 旧端/锁代理 |
| `pay/tokenPayList` | 卡密列表 | 旧端/锁代理 |
| `pay/tokenPayExcel` | 卡密导出 | 旧端/锁代理 |
| `pay/madetoken` | 生成卡密 | 旧端/锁代理 |

回调 `pay/notify`、`pay/wxPayNotify`、`pay/EPayNotify` 也继续由旧端执行业务。对支付接口的改动必须先在支付沙箱验证签名、订单幂等与回调重放，不能只根据前端按钮猜参数。

## 7. 上传与聊天（仍依赖旧端）

### 7.1 上传

`upload/full` 仍由旧端处理。已确认的前端协议是 multipart，文件字段名为 `file`，token 是普通表单字段。
公网请求先由 replacement 校验登录态；Spring 解析 multipart 后，安全代理会使用原字段、文件名、
媒体类型和文件内容重新生成 multipart 边界，再转发给旧端，不能从已解析的原始输入流直接复制：

```bash
curl -sS -X POST 'https://api.lcxqy.cn/upload/full' \
  -F 'token=你的登录token' \
  -F 'file=@D:/path/to/image.jpg'
```

不要将其替换为 base64 JSON。上传后先检查旧端实际响应字段，再把 URL 写入帖子、评论或动态。

QQBot 不使用 `webinfo.key` 冒充上传 token。replacement 会为已绑定的论坛 UID 在共享旧 Redis 中创建最多 5 分钟有效的独立上传会话，调用结束后立即删除；该会话不写 `starfree_users.authCode`，也不改动账号到现有 token 的映射，因此不会让用户其他设备退出登录。`SFreeBot/config` 的 `imageUploadReady=true` 表示这项共享会话能力已启用。

### 7.2 聊天

| 路径 | 已知用途 | 已确认参数 | 状态 |
|---|---|---|---|
| `SFreeChat/getPrivateChat` | 私聊会话读取 | 需抓包确认 | 旧端 |
| `SFreeChat/sendMsg` | 发送消息 | 需抓包确认 | 旧端 |
| `SFreeChat/myChat` | 我的会话 | 需抓包确认 | 旧端 |
| `SFreeChat/msgList` | 消息列表 | 需抓包确认 | 旧端 |
| `SFreeChat/deleteChat` | 删除会话 | 需抓包确认 | 旧端 |
| `SFreeChat/deleteMsg` | 删除消息 | 需抓包确认 | 旧端 |
| `SFreeChat/createGroup` | 创建群聊 | 需抓包确认 | 旧端 |
| `SFreeChat/editGroup` | 编辑群聊 | 需抓包确认 | 旧端 |
| `SFreeChat/allChat` | 后台聊天管理 | 需抓包确认 | 旧端 |
| `SFreeChat/banChat` | 聊天禁言/管理 | 需抓包确认 | 旧端 |
| `SFreeChat/groupInfo` | 群资料读取 | 需抓包确认 | 旧端 |

“需抓包确认”表示当前代码只证明路径被调用，缺少旧后端源码，不能据此实现兼容服务。需要重建时，先用测试账号在浏览器开发者工具或 uni.request 日志记录表单字段、正常和失败响应、权限边界、删除语义与未读计数。

## 8. PHP admin 专用接口和插件边界

前端还会直接访问 `https://admin.lcxqy.cn/Api/api.php?act=...` 的 PHP 配置接口，例如 `getPlugins`、`usercount`、`appdata`、`opset`、`fenlei`、`vip`、`adimg2`、`logininfo`、`chongzhiset`、`viphide`、`qzxz`、`musicpic`、`likeall`，以及更新、广告、客服/群链接和 mp3 页面。独立下载/介绍站使用 `act=versionList` 读取 `*_admin_update` 的公开版本投影；该接口只返回版本名称、版本号、更新描述、下载链接和更新类型，不读取后台账号信息。

这些不是 `api.lcxqy.cn` 的 Spring API，也不在后端重建范围。项目明确不做插件功能：Space `type=6` 不支持；未知插件形态内容由旧端处理，不应猜测表结构或写入格式。

PHP 后台的“功能设置 → 校园互助”（`/admin/mutualAid.php`）是迁移 014 的运营界面，直接使用后台既有数据库连接读取配置和互助信息，并在状态变更时写入 `starfree_lost_found_actions` 审计记录。该页面只展示发布者公开名称、互助正文和状态，不读取或展示 QQ、邮箱或任何联系方式；设置保存和审核操作均要求 PHP 管理员会话、POST 和 CSRF 校验。后台发布不需要再次执行数据库迁移。

## 9. 接入检查清单

1. 正式发布前确认 `utils/api.js` 的 `API_URL` 是 `https://api.lcxqy.cn/`，不能保留 `localhost:18082`。
2. 先判断 JSON 的 `code`，再读取 `data`；对本手册列出的裸响应单独处理。
3. 读取列表成功返回空数组时立即清空页面缓存，尤其是后台删帖后的主页列表。
4. 经济操作创建并持久化本次 `requestId`；网络重试复用，用户重新发起新动作才生成新的。
5. 不能把 token、支付参数、第三方签名、Pexels 密钥或生产连接凭据写进前端、机器人群消息或日志。
6. 新增或切流接口前，在本地 18082 与生产精确 Nginx location 分别验证；查看 `X-Starfree-Backend` 证明实际落点。
7. 对旧端“参数待抓包确认”接口先做非破坏性测试，不在生产用猜测字段创建支付、聊天、账号绑定或删除操作。

## 10. 维护索引

| 文档 | 用途 |
|---|---|
| [AI_PROJECT_BRIEF.md](AI_PROJECT_BRIEF.md) | 架构、数据模型、功能边界、前端行为和已知风险。 |
| [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) | 生产部署、切流、验收与回滚。 |
| [QQBOT_INTEGRATION_GUIDE.md](QQBOT_INTEGRATION_GUIDE.md) | AstrBot/QQ 帖子同步方案。 |

前端路径原始定义见 `../utils/api.js`，请求封装见 `../utils/net.js`，普通发帖的真实表单构造见 `../pages/user/post.vue`。
