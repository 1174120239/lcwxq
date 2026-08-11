# LCXQY API 调用手册

更新时间：2026-08-08

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

默认使用 `application/x-www-form-urlencoded`。token 放在表单或 query 的 `token` 字段；**不要**改成 `Authorization: Bearer`，旧客户端和兼容层并不依赖它。

```bash
curl -sS -X POST 'https://api.lcxqy.cn/SFreeUsers/userStatus' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'token=你的登录token'
```

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

def form_post(path, data):
    response = requests.post(BASE + path, data=data, timeout=15)
    response.raise_for_status()  # 网络/网关失败才会走这里
    body = response.json()
    if isinstance(body, dict) and body.get("code") == 0:
        raise RuntimeError(body.get("msg", "业务失败"))
    return body

token = "替换为实际 token"
result = form_post("SFreeUsers/userStatus", {"token": token})
print(result)
```

`HTTP 200` 不等于业务成功。标准业务失败仍可能是 `{"code":0,"msg":"原因"}`；只有网络错误、代理错误或未捕获异常才通常是 4xx/5xx。

### 2.3 参数约定

| 参数 | 说明 |
|---|---|
| `token` | 登录态。不能由客户端传 uid 代替。 |
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
| `SFreeUsers/userRegister` | GET/POST / 注册策略 | `params.name,password,mail,phone,code,inviteCode,campusId,gradeId` | 公网新 | `campusId/gradeId` 必填且必须当前启用；服务端决定角色和初始数值；邀请码返利进入 assets；成功后不自动登录。 |
| `SFreeUsers/userLogin` | POST / 账号密码 | `params.name,password` | 旧端 | 生产旧登录可能只有 Redis session；不要仅查 MySQL `authCode` 判断登录。 |
| `SFreeUsers/phoneLogin` | GET/POST / 短信码 | `phone,code` | 旧端 | 验证码发送仍在旧端；登录成功兼容写 MySQL 和 Redis。 |
| `SFreeUsers/userFoget` | GET/POST / 邮箱验证码 | `params.name,code,password` | 公网新 | 路径拼写为历史 `Foget`；成功后撤销关联会话。 |
| `SFreeUsers/userEdit` | GET/POST / token | `params.uid` 和资料白名单 | 公网新 | 只能编辑自己。简介最多 255 字并保留换行，显式传空字符串可清空；不得传 assets/points/experience/VIP/角色；改密码、邮箱会撤销会话。 |
| `SFreeUsers/setClientId` | GET/POST / token | `clientId` | 公网新 | 推送标识；空字符串表示清除。 |
| `SFreeUsers/signOut` | GET/POST / token | `token` | 旧端 | 只退出当前 token，不是全设备登出。 |
| `SFreeUsers/userStatus` | GET/POST / token | `token` | 公网新 | 成功返回用户和原 token，并包含 `campusId/campus/gradeId/grade`；失效为 `code=0`。 |
| `SFreeUsers/userInfo` | GET/POST / 可匿名 | `uid` 或 `token` | 公网新 | `uid` 优先；资料投影包含 `campusId/campus/gradeId/grade`，停用历史选项仍正常显示。 |
| `SFreeUsers/userData` | GET/POST / 可匿名 | `uid` 或 `token` | 旧端 | 本人不传 `uid` 时评论计数包含已发布和待审核动态评论（space type=3），查看他人仅统计已发布评论；不再统计文章评论。旧字段为 `contentsNum/commentsNum/fanNum/followNum`，同时返回简写字段。 |
| `SFreeUsers/RegSendCode` | GET/POST / 注册策略 | 旧端参数待抓包确认 | 旧端 | 邮箱注册验证码发送；新后端只兼容消费验证码。 |
| `SFreeUsers/sendSMS` | GET/POST / 手机号策略 | 旧端参数待抓包确认 | 旧端 | 短信验证码发送，不能伪造供应商请求。 |
| `SFreeUsers/SendCode` | GET/POST / 登录态/策略 | 旧端参数待抓包确认 | 旧端 | 历史通用验证码发送。 |
| `SFreeUsers/apiLogin` | GET/POST / 第三方凭据 | 旧端参数待抓包确认 | 旧端 | 不要信任客户端直接传来的 openId；重建时必须校验提供方 code/token。 |
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

### 3.3 站内信、关注与用户管理

| 路径 | 方法/鉴权 | 参数 | 路由 | 调用与注意点 |
|---|---|---|---|---|
| `SFreeUsers/inbox` | GET/POST / token | `type,page,limit` | 公网新 | 读取不会自动设为已读；`spaceComment` 表示动态作者收到的评论或评论回复，`value` 是原动态 id，`cid` 是新动态评论 id；动态点赞不产生站内信。 |
| `SFreeUsers/unreadNum` | GET/POST / token | `token` | 公网新 | 不包括旧聊天未读数。 |
| `SFreeUsers/setRead` | GET/POST / token | `type` | 公网新 | `all/comment/finance/system/fan`；`comment` 同时标记文章评论和动态评论（`spaceComment`）；chat 为历史兼容，返回 0；可重复调用。 |
| `SFreeUsers/sendUser` | GET/POST / administrator | `uid,text` | 代码新/公网旧 | 写持久化 system inbox，不保证调用推送厂商。 |
| `SFreeUsers/follow` | GET/POST / token | `touid,type` | 旧端 | `type=1` 关注、`0` 取消；首次关注写粉丝通知。 |
| `SFreeUsers/isFollow` | GET/POST / token | `touid` | 旧端 | 已关注为 `code=1`，未关注为 `code=0`，不是 `data` 布尔值。 |
| `SFreeUsers/followList` | GET/POST / 无 | `uid,page,limit` | 旧端 | 关注列表含脱敏 `userJson`。 |
| `SFreeUsers/fanList` | GET/POST / 无 | `touid,page,limit` | 旧端 | 保留历史参数名 `touid`。 |
| `SFreeUsers/userList` | GET/POST / 可选 staff | `searchParams,searchKey,order,page,limit,token` | 旧端 | 匿名结果脱敏；`order` 白名单，limit 最大 50。 |
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
| `SFreeSpace/addSpace` | GET/POST / token | `text,pic,type,toid,onlyMe,topicIds` | 公网新 | type 仅 0..5；type=0 无图片时正文去除首尾空白后至少 4 字，有图片时正文可为空或不足 4 字；type=3 是评论/回复，正文至少 1 字且 `toid` 指向被回复的动态或评论，同一用户 20 秒内对同一目标提交相同正文按重复请求处理而不重复落库；`topicIds` 是最多 3 个话题 mid，逗号分隔；type=6 插件明确拒绝；开启审核则 status=0。 |
| `SFreeSpace/editSpace` | GET/POST / 作者或 staff | `id,text` 和可选字段，含 `topicIds` | 公网新 | 类型不可变；staff 编辑保留作者，不重复发经验；传 `topicIds=0` 表示清空该动态的话题。 |
| `SFreeSpace/spaceInfo` | GET/POST / 可选 token | `id,token` | 公网新 | 统一执行私密、待审、锁定可见性；返回对象新增 `topics` 数组；成功读取会增加浏览量。 |
| `SFreeSpace/spaceList` | GET/POST / 可选 token | `searchParams,searchKey,order,page,limit,isManage` | 公网新 | `isManage` 只对 staff 有效；普通列表默认排除 type=3 回复；兼容单个 `topicId`，`topicIds` 可传数组或逗号分隔 id，去重后最多 3 个并按 AND 匹配。 |
| `SFreeSpace/followSpace` | GET/POST / token | `page,limit` | 公网新 | 只读已关注用户的公开、非回复动态。前端别名见下一行。 |
| `SFreeSpace/myFollowSpace` | GET/POST / token | `page,limit` | 公网新 | `followSpace` 的前端别名；仅当前页 count。 |
| `SFreeSpace/spaceDelete` | GET/POST / 作者或 staff | `id` | 公网新 | 只删主行，不级联历史回复、转发、spaceLike，不扣经验。 |
| `SFreeSpace/spaceLikes` | GET/POST / token | `id` | 公网新 | uid+space id 持久切换点赞状态；返回 data=1 表示已点赞，data=0 表示已取消点赞。 |
| `SFreeSpace/spaceReview` | GET/POST / staff | `id,type` | 公网新 | `type=1` 通过，`0` 拒绝并删主行，写系统通知。 |
| `SFreeSpace/spaceLock` | GET/POST / staff | `id,type` | 公网新 | `type=2` 锁定、`1` 解锁；待审不可锁，锁定后不能回复或转发。 |
| `SFreeSpace/topicList` | GET/POST / 可选 token | `token,searchKey` | 公网新 | 按名称/描述关键词模糊搜索，返回 `data.all/hot/official/followed`；每项含动态数、关注数和当前用户关注状态。关注列表只在登录后返回。 |
| `SFreeSpace/topicCreate` | GET/POST / token | `token,name` | 公网新 | 用户自建话题；名称自动去掉首尾 `#` 和空白，只允许中英文、数字、下划线、短横线，1-24 字；创建后自动关注。 |
| `SFreeSpace/topicFollow` | GET/POST / token | `token,mid,type` | 公网新 | `type=1` 关注，`type=0` 取消；幂等处理，不会重复插入关注。 |
| `SFreeSpace/userReplies` | GET/POST / 可选 token | `uid,page,limit,token` | 代码新/公网旧 | 按时间倒序返回指定用户发表的动态评论；未传 uid 时必须登录。每项以 `originalState=visible/deleted/forbidden` 区分原动态，并在可见时返回作者和最多 180 字摘要。 |
| `SFreeSpace/reportAdd` | POST / token | `id,reason,detail` | 代码新/公网旧 | 只能举报公开主动态，不能举报自己的动态；`reason` 为广告营销、人身攻击、色情低俗、违法违规或其他。同一用户对同一动态只保留一条举报，重复提交返回业务失败。 |
| `SFreeSpace/reportList` | GET/POST / staff | `token,status,page,limit` | 代码新/公网旧 | 管理端举报队列；`status=0` 待处理、`1` 已处理、`2` 已驳回，返回举报人、动态摘要和动态是否已删除。 |
| `SFreeSpace/reportReview` | POST / staff | `token,id,action,note` | 代码新/公网旧 | `action=delete` 通过举报并删除原动态，同时关闭该动态的全部待处理举报；`action=dismiss` 只驳回当前举报。记录审核人、结果、说明和时间。 |

动态话题复用 `starfree_metas.type='tag'` 作为话题目录，但动态和话题的关系不走文章用的 `starfree_relationships`，而是写入 `starfree_space_topics`，避免文章 cid 和动态 id 数字碰撞。后台“分类/话题”页面的“新增话题”会创建官方话题；用户在发布页输入的新话题会创建为用户话题，并写 `starfree_topic_meta.is_official=0`。后台将该话题设为推荐后，也会出现在官方话题区。

动态举报表由 `backend/database/migrations/008_space_reports.sql` 创建。未执行迁移前不能启用上述三个举报路由；发布默认不执行该迁移。

### 校园问答

校园问答使用独立表，不复用帖子、动态或文章评论。普通登录用户可从发布面板提交问题，服务端强制进入待审核状态；管理员或编辑在管理控制台审核、编辑和发布。登录用户也可以回答、点赞回答、评论回答和回复评论。

| 接口 | 方法/权限 | 参数 | 落点 | 说明 |
|---|---|---|---|---|
| `SFreeQa/questionList` | GET/POST / 无 | `page,limit,keyword,recommended` | 代码新/公网旧 | 只返回已发布问题；推荐、排序值和更新时间共同决定顺序。 |
| `SFreeQa/questionAdd` | POST / token | `params={title,description,topic}` | 代码新/公网旧 | 标题 4-160 字，说明最多 5000 字，话题最多 80 字；服务端固定 `status=0,recommended=0,sortOrder=0,createdBy=当前用户`，忽略客户端伪造的管理字段，20 秒内相同标题和说明拒绝重复提交。 |
| `SFreeQa/questionInfo` | GET/POST / 可选 token | `id,token` | 代码新/公网旧 | 普通用户只能读取已发布问题；staff 可预览停用问题。 |
| `SFreeQa/answerList` | GET/POST / 可选 token | `questionId,page,limit,sort,token` | 代码新/公网旧 | `sort=latest` 按时间，其他值按点赞和时间；登录时返回 `isLiked`。 |
| `SFreeQa/answerAdd` | POST / token | `params={questionId,text}` | 代码新/公网旧 | 回答至少 4 字、最多 5000 字；20 秒内相同回答拒绝重复提交。 |
| `SFreeQa/answerEdit` | POST / owner/staff | `params={id,text}` | 代码新/公网旧 | 仅回答作者或 staff 可修改。 |
| `SFreeQa/answerDelete` | POST / owner/staff | `id,token` | 代码新/公网旧 | 逻辑删除回答并清理点赞关系，不硬删历史正文。 |
| `SFreeQa/answerLike` | POST / token | `answerId,token` | 代码新/公网旧 | uid+answer id 唯一，重复点击在点赞和取消之间切换并返回最新计数。 |
| `SFreeQa/commentList` | GET/POST / 无 | `answerId,page,limit` | 代码新/公网旧 | 根评论分页，返回页内完整 `children` 回复树。 |
| `SFreeQa/commentAdd` | POST / token | `params={answerId,parentId,text}` | 代码新/公网旧 | `parentId=0` 评论回答；非 0 回复评论；至少 1 字，20 秒内相同内容防重复。 |
| `SFreeQa/commentDelete` | POST / owner/staff | `id,token` | 代码新/公网旧 | 作者或 staff 可删除；删除根评论时隐藏整条回复树。 |
| `SFreeQa/questionManage` | GET/POST / staff | `token,page,limit,keyword,status` | 代码新/公网旧 | 后台读取发布和停用问题，支持关键词与状态筛选。 |
| `SFreeQa/questionSave` | POST / staff | `params={id,title,description,topic,coverUrl,recommended,sortOrder,status}` | 代码新/公网旧 | id 为空新增，否则修改；问题标题至少 4 字。 |
| `SFreeQa/questionStatus` | POST / staff | `id,status,token` | 代码新/公网旧 | `status=1` 发布，`0` 停用；不提供硬删除接口。 |

问答通知写入 `starfree_inbox`：新回答使用 `qaAnswer`，回答评论或评论回复使用 `qaComment`，`value` 保存问题 id，消息中心和 UniPush 点击均可回到问题详情。数据库迁移为 `backend/database/migrations/007_campus_qa.sql`，未执行迁移前不能启用这些路由。

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

`upload/full` 仍由旧端处理。已确认的前端协议是 multipart，文件字段名为 `file`，token 是普通表单字段：

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

前端还会直接访问 `https://admin.lcxqy.cn/Api/api.php?act=...` 的 PHP 配置接口，例如 `getPlugins`、`usercount`、`appdata`、`opset`、`fenlei`、`vip`、`adimg2`、`logininfo`、`chongzhiset`、`viphide`、`qzxz`、`musicpic`、`likeall`，以及更新、广告、客服/群链接和 mp3 页面。

这些不是 `api.lcxqy.cn` 的 Spring API，也不在后端重建范围。项目明确不做插件功能：Space `type=6` 不支持；未知插件形态内容由旧端处理，不应猜测表结构或写入格式。

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
