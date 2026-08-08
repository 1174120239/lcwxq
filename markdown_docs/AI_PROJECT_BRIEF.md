# 聊一论坛项目技术手册

> 更新日期：2026-08-05
>
> 适用仓库：1174120239/lcwxq
>
> 读者：项目维护者、二次开发人员和接手的 AI

本文是项目当前状态的主技术文档。它不记录逐日操作流水；功能变化应直接更新对应章节。接口参数见 [API_USAGE_GUIDE.md](API_USAGE_GUIDE.md)，生产操作见 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)。

## 1. 当前结论

- 前端是 HBuilderX / uni-app / Vue 2 项目。
- 新后端位于 backend/starfree-replacement，使用 Spring Boot 2.7.18，编译为 Java 8 兼容字节码。
- 生产环境保留旧闭源 Java API；仓库中的 `backend/legacy-api/dist/StarFreeApi.jar` 是从生产服务器核验的可部署副本，新后端通过 Nginx 精确路由逐步接管接口。
- PHP admin 管理后台继续使用，不重建。
- 插件功能不在重建范围，动态 type=6 明确拒绝。
- 充值、验证码发送、文件上传、聊天和部分第三方登录仍使用旧后端。
- 积分、签到、奖励、提现、商城、VIP 和广告经济逻辑已在新后端实现，并保留旧支付入口。
- 动态已支持浏览量、话题、话题关注、纯文字、纯图片、审核、锁定、删除和按话题筛选。
- 后端当前全量测试为 199 个，Failures=0，Errors=0，Skipped=0。

## 2. 系统架构

~~~text
uni-app 前端
  ├─ https://api.lcxqy.cn/      Nginx API 入口
  │    ├─ 127.0.0.1:18082      新 Spring Boot 后端
  │    └─ 127.0.0.1:8081       旧闭源 Java API
  ├─ https://admin.lcxqy.cn/    PHP admin 与配置接口
  └─ https://prev.lcxqy.cn/     H5/分享页面

新旧后端共享：
  ├─ MySQL lcxqy
  └─ Redis 登录态、限额与缓存
~~~

Nginx 只对已验证路径建立精确 location。未匹配路径继续进入旧端兜底。

## 3. 项目目录

| 路径 | 内容 |
|---|---|
| pages、components、utils | uni-app 页面、组件和 API 封装 |
| static | 前端图片、字体、主题和应用图标 |
| uni_modules、js_sdk | 前端依赖源码 |
| admin/starfree-admin/source | PHP 管理后台程序和静态资源；真实 `Config_DB.php` 不提交 |
| backend/legacy-api | 旧闭源 API JAR、配置模板、systemd 服务和安装脚本 |
| backend/starfree-replacement | 新后端源码、配置和测试 |
| backend/database/migrations | 有序数据库迁移 |
| backend/deploy/production | systemd、部署、切流和验收脚本 |
| backend/scripts | 本地启动和可清理集成测试 |
| backend/reference | 旧 Java API 的 Mapper 等必要接口参考；不作为源码构建入口 |
| markdown_docs | 项目自有文档 |

不进入 Git：

- unpackage、target、APK 和构建缓存。
- tools 和 backend/.local 本地工具/运行目录。
- 数据库快照。
- 服务器、数据库和 Redis 私密凭据。
- `backend/legacy-api/config/application.properties`、`admin/starfree-admin/source/Config_DB.php` 和 `.user.ini`。
- 签名证书、密钥和服务器上的其他未核验可执行文件。

## 4. 本地开发

### 4.1 前端

使用 HBuilderX 打开仓库根目录。生产 API 配置在 utils/api.js：

~~~text
API_URL  = https://api.lcxqy.cn/
STAR_URL = https://admin.lcxqy.cn/
WEB_URL  = https://prev.lcxqy.cn/
~~~

本地联调可临时把 API_URL 改为 http://localhost:18082/，正式发行前必须改回生产值。

manifest.json 使用的应用图标位于 static/branding/icons，不再依赖被忽略的 unpackage 目录。

### 4.2 后端环境

要求：

- Java 8 或更高版本。
- Maven 3.9+。
- MySQL。
- Redis；只在验证旧登录态兼容时必须启用。

主要环境变量：

| 变量 | 说明 |
|---|---|
| APP_PORT | 默认 18082 |
| DB_HOST、DB_PORT、DB_NAME | 数据库连接 |
| DB_USERNAME、DB_PASSWORD | 数据库账号 |
| REDIS_HOST、REDIS_PORT、REDIS_PASSWORD | Redis 连接 |
| LEGACY_API_BASE_URL | 本地旧 API 或生产旧端地址 |
| LEGACY_REDIS_ENABLED | 是否读取旧 Redis 登录态 |
| LEGACY_REDIS_PREFIX | 旧 Redis key 前缀 |

本地启动：

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File backend/scripts/start-local.ps1
~~~

该脚本把本地数据库密码写入被忽略的 backend/.local/run/application-secrets.yml，不应复制到源码配置。

测试和构建：

~~~powershell
mvn -f backend/starfree-replacement/pom.xml test
mvn -f backend/starfree-replacement/pom.xml clean package
~~~

### 4.3 数据库快照

生产快照只用于专用本地开发库，不能导入生产，也不能提交 Git。结构变化必须写入 backend/database/migrations。

当前迁移：

| 文件 | 作用 |
|---|---|
| 001_economy_operation_journal.sql | 经济操作幂等日志 |
| 002_space_views.sql | 动态浏览量 |
| 003_space_topics.sql | 动态话题、关注和关联 |
| 004_campus_identity.sql | 校区/入学年份选项及用户稳定引用 id |

## 5. 请求和响应约定

### 5.1 请求

兼容接口通常同时接受 GET 和 POST，前端主要使用 application/x-www-form-urlencoded。

常见字段：

- token：登录态。
- params：JSON 字符串。
- searchParams：列表筛选 JSON 字符串。
- page、limit：分页。
- requestId：经济或可重试写操作的幂等号。

不要擅自把表单接口改成只接收 JSON。旧前端、PHP admin 和脚本依赖现有编码。

### 5.2 响应

标准成功：

~~~json
{"code":1,"msg":"操作成功","data":{}}
~~~

标准业务失败：

~~~json
{"code":0,"msg":"失败原因"}
~~~

注意：

- HTTP 200 仍可能是业务失败。
- contentsInfo 等少数兼容接口存在裸对象成功响应，调用方必须按 API 手册处理。
- 列表成功返回空数组时，前端必须清空旧缓存，避免后台删帖后仍显示失效卡片。
- 不同接口的 id、uid、cid、mid 和 logid 不能混用。

### 5.3 幂等

经济、奖励、支付转发和 Bot 发帖等可重试操作必须使用 requestId。网络重试复用同一个 requestId；用户重新发起的新操作生成新值。

## 6. 登录态和安全

新后端兼容两类登录态：

1. 用户表 authCode。
2. 旧 Redis 中的 Java 序列化或 hash session。

因此：

- 不能只查 MySQL 判断 token 无效。
- 登录会轮换 token。
- 退出、改密和敏感资料修改要同时撤销 MySQL 与 Redis 登录态。
- Redis session bridge 只用于兼容保留的旧接口。
- 接口返回用户信息时不得暴露密码散列、支付密钥和内部 token。

仓库禁止保存：

- SSH、MySQL、Redis 和支付明文密码。
- API key、Bot secret、QQ 空间 Cookie。
- 生产数据库快照。
- 用户账号、验证码和真实测试数据。

本机服务器连接信息保存在被 Git 忽略的 markdown_docs/private/SERVER_ACCESS.local.md。

## 7. 数据库与一致性

历史主表大量使用 MyISAM，Spring 事务不能保证多语句原子性。

新后端采用以下策略：

- 关键经济操作使用全局 MySQL 命名锁。
- requestId 写入 InnoDB 幂等日志。
- 先校验余额、权限、关系和状态，再执行写入。
- 多表写入失败时执行明确补偿。
- MySQL 是权威数据，Redis 只做登录态、限额和缓存。
- 不在未验证时把多表写入改成异步。

金额边界：

- assets：可提现余额。
- points：积分。
- experience：经验。

三者不能互换，也不能因为前端文案相似而写错字段。

动态话题使用：

- starfree_metas.type='tag'：话题目录。
- starfree_topic_meta：话题创建者和官方属性。
- starfree_space_topics：动态与话题多对多关系。
- starfree_topic_follows：用户关注话题。

动态话题不能复用文章的 starfree_relationships，因为文章 cid 和动态 id 是不同序列。

校园身份使用：

- `starfree_identity_options.type='campus'/'grade'`：校区和入学年份选项目录。
- `starfree_users.campus_option_id/grade_option_id`：用户对稳定选项 id 的引用。
- 注册只能选择启用项；已使用选项通过停用退出新注册，不能硬删除。
- 改名统一影响所有引用该选项的显示，停用不改变已有用户的历史资料。

## 8. 后端功能边界

### 8.1 新后端主要模块

| 模块 | 当前能力 |
|---|---|
| system | health、liveness |
| security/user | 登录、退出、注册、找回、资料、token 轮换、用户管理、校区和入学年份维护 |
| content | 列表、详情、普通新增/更新、删除、审核、推荐/置顶/轮播 |
| comment | 列表、新增、删除、审核 |
| meta | 分类/标签增删改查、推荐、关系清理 |
| log | 收藏、互动日志、订单读取和清理 |
| space | 动态读写、审核、锁定、删除、点赞、关注、浏览量、话题 |
| ads | 广告读写、购买、审核、续费和奖励回调 |
| economy | 积分、经验、余额、签到、奖励、提现、财务记录 |
| shop | 商品、商城、VIP 和购买 |
| proxy | 未迁移接口和受控旧端委托 |

### 8.2 混合处理

- contentsAdd/contentsUpdate 只在普通 post/video 场景由新端完整写入；付费、草稿、商品关联和未知类型可委托旧端。
- 官方充值、卡密和支付回调保留旧实现，但经济锁和日志可能由新端包裹。
- token-bearing 列表、广告管理等路径是否进入新端取决于精确 Nginx 路由。

### 8.3 仍依赖旧端

- 邮件/短信验证码发送。
- QQ、微信、微博等社会化登录/绑定。
- upload/full。
- 私聊、群聊和聊天管理。
- 官方支付创建、卡密和原支付回调。
- 插件接口和未知插件内容。

详细逐路径状态以 [API_USAGE_GUIDE.md](API_USAGE_GUIDE.md) 为准。

## 9. 前端当前行为

### 9.1 动态

- 支持纯文字、纯图片、图文和视频动态。
- 空正文且有图片可以发布；纯文字正文至少 4 字，页面同步显示字数和禁用原因；上传失败图片必须重试或删除后才能提交。
- 详情成功读取会增加浏览量。
- 列表卡片进入详情后本地同步浏览量，避免返回时仍显示旧值。
- 后台删除动态后，空列表必须覆盖旧缓存，详情无内容时不保留失效卡片。
- 发布和编辑支持最多 3 个话题；发布区下方常驻展示可横向滑动的官方推荐话题，点击即可选择或取消。
- 用户输入但未点击“添加”的话题，会在提交前自动创建/选中。
- 话题可搜索、关注和取消关注，并区分全部、热门、我关注的话题。
- 最多选择 3 个话题筛选动态，多个话题使用 AND 语义；从发现页直接进入时先显示话题信息和相关动态。
- 个人页评论栏读取动态评论历史，并明确标识原动态已删除或不可见。
- 动态详情显示完整正文和全部图片，图片预览保持在当前业务页内。
- 注册必须从后台启用项中选择校区和入学年份；后台可新增、改名、排序和停用选项。
- 用户选择的校区会在动态列表、关注动态、动态详情、自己主页和他人主页以低对比度辅助文字展示；入学年份只在自己主页和他人主页以更弱的辅助文字展示，不在动态卡片上增加视觉噪音。
- 动态首页顶部最多同时选择 3 个话题，标签保持高亮并按 AND 模式筛选；点击或滚动下方动态流会收起筛选面板。
- 动态列表只折叠经判定的长正文并提供“查看全文”，详情页始终解除正文高度限制。
- 个人简介保留换行并支持显式清空。

### 9.2 内容与后台

- 帖子管理使用新后端时必须保留后台所需状态和权限字段。
- 推荐、置顶、轮播字段由内容扩展接口维护。
- 后台话题管理复用 tag/meta 管理页；校区和年级使用独立的稳定选项目录。
- PHP admin 配置接口仍直接访问 admin.lcxqy.cn。

### 9.3 消息和聊天

站内通知仍保留；动态评论和回复会写入 `starfree_inbox` 的 `spaceComment` 类型并携带原动态 id，消息中心可直接打开动态详情；动态点赞只记录点赞日志和计数，不产生站内信、推送或邮件通知。UniApp Push 使用用户绑定的 `clientId`，后端 UniPush 发送器由 `UNIPUSH_ENABLED` 和运行时凭据控制，支持 `unipush.protocol=v1`（UniPush 1.0 / 个推 REST v1，`auth_sign` + `push_single`）与 `v2`（个推 REST v2）两种协议，生产按 App 打包版本选择 v1。推送失败不影响站内消息落库。私聊和群聊前端入口已收敛或隐藏。旧聊天接口未重建，不能把消息中心超时直接归因于新后端。

### 9.4 主题和布局

- 支持日间、夜间和自动主题。
- 桌面 H5 必须保持内容居中，轮播、资料页和发布页不能随宽屏向左偏移。
- 日间和夜间状态必须隔离，切换后不能残留另一主题背景。
- 核心操作保持原生、紧凑和校园论坛风格，避免过度装饰和大面积营销式卡片。

## 10. 生产路由与部署

生产遵循：

- 新端 18082、旧端 8081 并行。
- 每条新路由独立切换和回滚。
- X-Starfree-Backend 用于证明实际落点。
- X-Starfree-Delegate 用于识别新端内部转发。
- 数据库迁移先于读取新结构的 JAR。
- Nginx 修改必须先备份并通过 nginx -t。

完整流程见 [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md)。

## 11. 测试

### 11.1 自动测试

当前 Maven 结果：

~~~text
Tests run: 199
Failures: 0
Errors: 0
Skipped: 0
~~~

测试覆盖控制器兼容、权限、审核、幂等、Redis 登录态、MyISAM 补偿、动态可见性、内容委托和经济操作。

### 11.2 本地集成脚本

| 脚本 | 范围 |
|---|---|
| test-local-space.ps1 | 动态 |
| test-local-economy.ps1 | 经济 |
| test-local-ads.ps1 | 广告 |
| test-local-ads-reward.ps1 | 广告奖励 |
| test-local-account-maintenance.ps1 | 注册和账号维护 |

脚本创建的数据必须可识别、可清理，不应依赖真实用户。

### 11.3 前端验证

HBuilderX 编译后至少检查：

- 首页、动态详情、发布和编辑。
- 登录、注册、个人资料和站内消息。
- 管理端帖子、分类、话题和轮播设置。
- 桌面宽屏与移动端。
- 日间和夜间主题。
- 空列表、无权限、token 失效和网络超时。

## 12. 常见故障

### 接口 HTTP 200，但页面提示失败

检查 JSON code/msg，不要只判断 HTTP 状态。

### 登录在旧端有效，新端无效

检查 LEGACY_REDIS_ENABLED、Redis 连接、前缀、序列化格式和 session TTL。

### 后台删帖后首页仍显示

检查列表接口是否排除已删状态、前端空数组是否覆盖缓存，以及 CDN/Redis/本地缓存是否失效。

### 动态浏览量一直为 0

确认 002_space_views.sql 已执行，spaceInfo 路由进入新端，返回对象包含 views。

### 话题发布后消失

确认提交前已把输入框内容创建为话题，addSpace/editSpace 发送 topicIds，数据库存在 starfree_space_topics 关系。

### 消息页超时

区分站内通知与旧聊天接口；查看 utils/api.js 的真实路径和 X-Starfree-Backend。

### Nginx 修改不生效

检查 include 是否被主站配置引用，执行 nginx -t 和 reload，再检查响应头。

### 余额不一致

确认操作字段是 assets、points 还是 experience，检查经济锁、幂等日志、paylog 和补偿状态。

## 13. 已知风险

- MyISAM 多表写入仍存在进程中断窗口。
- 动态删除保留部分旧端语义，历史回复、转发或点赞日志可能形成孤儿记录。
- upload/full、聊天、验证码和官方支付仍依赖闭源后端。
- 商城跨表写入上线前仍需要真实 MySQL 的可清理集成测试。
- 旧端缓存 key 和序列化格式改变会影响新旧互通。
- 插件类型和未知历史数据不能凭猜测重建。

优先级：

1. 评估 MyISAM 到 InnoDB 的可回滚迁移。
2. 审计动态孤儿关系，单独设计清理迁移。
3. 补齐商城真实数据库测试。
4. 继续抓包和测试旧端剩余接口，再决定是否迁移。

## 14. 二次开发流程

1. 在 utils/api.js 和 API 手册确认路径、参数和调用方。
2. 判断请求当前由新端、旧端还是混合委托处理。
3. 阅读对应 Controller、Service、测试和 Nginx 脚本。
4. 先写正常、权限、失败、重复和兼容测试。
5. 本地数据库只使用可清理数据。
6. 接口新增 requestId、权限、审核和缓存失效规则。
7. 更新 API 手册和本技术手册对应章节。
8. 数据库变化新增有序 migration。
9. 部署时按数据库、JAR、Nginx、验收顺序执行。

不要继续追加“某日做了什么”的流水章节。Git 历史已经承担变更记录，文档只保留当前有效事实和操作方法。

## 15. 交接检查

- git status 干净，main 与 origin/main 一致。
- README 和 markdown_docs 链接有效。
- 生产凭据不在 Git 历史。
- Maven 测试通过。
- 前端 API_URL 为生产地址。
- 数据库迁移和生产表结构一致。
- starfree-replacement.service active。
- /health 正常。
- Nginx 语法通过。
- 关键接口响应头符合预期。
- 旧端兜底仍能处理未迁移接口。
