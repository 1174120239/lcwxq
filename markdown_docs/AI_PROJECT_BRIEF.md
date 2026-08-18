# 聊一论坛项目技术手册

> 更新日期：2026-08-12
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
- 除匿名动态（原 ng_music 插件功能，2026-08-08 按用户要求原生实现）外，插件功能不在重建范围，动态 type=6 明确拒绝。
- 充值、短信验证码、文件上传、聊天和部分第三方登录仍使用旧后端；邮箱验证码发送已在新后端重建，待精确路由切流。
- 积分、签到、奖励、提现、旧商城、VIP 和广告经济逻辑已在新后端实现，并保留旧支付入口；客户端原商城入口已替换为完全免费的“校园互助”，不调用余额、积分、库存、订单或购买逻辑。
- 轻量邀请分享已加入：用户邀请码、注册成功后的积分/经验奖励、分享页和后台软件下载地址配置；不扩展为多级返佣或提现系统。
- 动态已支持浏览量、话题、话题关注、纯文字、纯图片、审核、锁定、删除、精华、列表置顶、横幅置顶和按话题筛选。
- 后端当前全量测试为 364 个，Failures=0，Errors=0，Skipped=0。

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
- Redis；生产登录态必须启用，Redis TTL 是会话有效期权威。

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
| SPRING_MAIL_HOST、SPRING_MAIL_PORT | 邮箱验证码和通知邮件的 SMTP 地址与端口 |
| SPRING_MAIL_USERNAME、SPRING_MAIL_PASSWORD、SPRING_MAIL_FROM | SMTP 账号、授权码和发件地址，只能配置在运行环境 |
| SPRING_MAIL_CONNECTION_TIMEOUT、SPRING_MAIL_READ_TIMEOUT、SPRING_MAIL_WRITE_TIMEOUT | SMTP 连接、读取和写入超时毫秒数，默认 10/15/15 秒 |
| VERIFICATION_EMAIL_ENABLED | 是否允许新后端发送邮箱验证码，默认 true |
| VERIFICATION_EMAIL_MAX_CONCURRENT | 验证码 SMTP 同时发送上限，默认 2，防止请求堆积和供应商限频 |
| VERIFICATION_EMAIL_AUTHENTICATION_BACKOFF_SECONDS | SMTP 认证失败后的全局退避秒数，默认 300，退避期间不再连接供应商 |
| VERIFICATION_EMAIL_MINIMUM_ATTEMPT_INTERVAL_MILLIS | 两次真实 SMTP 尝试的最小间隔毫秒数，默认 1000 |

生产 JAR 会在运行环境未显式提供 SMTP 值时，仅从 `/opt/application.properties`
兼容读取 `spring.mail.host/port/username/password/from`；不会导入该文件中的端口、数据库、
Redis 或其他旧端设置。环境变量和命令行参数始终优先。

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
| 008_simple_invitation.sql | 轻量邀请配置、用户邀请码和奖励记录 |
| 002_space_views.sql | 动态浏览量 |
| 003_space_topics.sql | 动态话题、关注和关联 |
| 004_campus_identity.sql | 校区/入学年份选项及用户稳定引用 id |
| 005_ng_music_anonymous.sql | 匿名动态专用账号、真实发布者映射与后台配置 |
| 006_qqbot_dynamic_ai.sql | NapCat 个人 QQ 动态助手配置、绑定、群同步、幂等和投递日志 |
| 007_campus_qa.sql | 校园问答问题、回答、回答点赞、评论和回复 |
| 009_space_reports.sql | 动态举报、处理状态和 staff 审核审计 |
| 010_admin_password_hash.sql | 将 PHP admin 密码列扩为 `VARCHAR(255)`，允许安全保存 `password_hash()` 结果 |
| 011_dynamic_core_extensions.sql | 用户可选资料、动态投票、AI 审核配置/队列和动态分析事件表 |
| 012_space_presentation.sql | 动态精华、列表置顶、横幅置顶、排序和生效时间字段 |
| 013_ai_moderation_complete.sql | 统一动态/提问/评论 AI 审核历史、人工改判日志、每日评论巡检配置和总结 |
| 014_lost_and_found.sql | 校园互助信息、公开评论、QQ 定向授权、审核日志和管理配置 |

## 5. 请求和响应约定

### 5.1 请求

兼容接口通常同时接受 GET 和 POST，前端主要使用 application/x-www-form-urlencoded。新客户端
优先用 `Authorization: Bearer <token>` 传登录态；新后端仍接受历史 `token` 表单/query 参数，
用于兼容旧客户端和旧端代理。

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

新登录 token 为 `sf2_` 加 60 位小写十六进制随机串，由 30 字节 `SecureRandom` 生成，
总长度固定为 64 字符以兼容共享用户表的 `authCode` 列，并且与用户名和时间无关。旧格式 token
全部拒绝，因此安全版本上线时所有用户必须重新登录。

生产启用 Redis session bridge 后，Redis 中存在且未超过 TTL 的 session 是登录态唯一权威；
Redis 中不存在的 token 不能再用 MySQL `authCode` 复活。只有未启用 bridge 的本地测试环境
回退查询 MySQL。普通登录态采用 90 天无操作过期，并在剩余时间低于一半时滑动续期；网站加载、
App 启动或回到前台也会静默校验。登录会轮换 token，退出、改密和敏感资料修改同时撤销 MySQL
与 Redis 登录态。

公开用户资料统一使用字段白名单。匿名和跨账号读取不得返回角色、邮箱、手机、地址、资产、积分、
IP、local、登录时间、clientId 或内部 token；本人读取才可获得完整资料。文章、动态、关系和
通知中的嵌套用户对象遵守同一边界。所有设置新密码的入口执行 8-128 位、同时包含字母和数字、
拒绝常见弱密码的策略；本人改密还必须验证原密码。

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
| security/user | 登录、退出、注册、邮箱验证码发送、找回、资料、token 轮换、用户管理、校区和入学年份维护 |
| content | 列表、详情、普通新增/更新、删除、审核、推荐/置顶/轮播 |
| comment | 列表、新增、删除、审核 |
| meta | 分类/标签增删改查、推荐、关系清理 |
| log | 收藏、互动日志、订单读取和清理 |
| space | 动态读写、AI 实时审核、人工改判、锁定、删除、点赞、关注、浏览量、话题、精华/置顶展示、用户举报和 staff 举报审核 |
| qa | 用户提问 AI 实时审核、回答、点赞回答、评论和回复；staff 查看 AI 原因并改判问题；问答站内通知与 UniPush |
| ads | 广告读写、购买、审核、续费和奖励回调 |
| economy | 积分、经验、余额、签到、奖励、提现、财务记录 |
| shop | 商品、商城、VIP 和购买 |
| lostfound | 免费校园互助：求助/提供帮助、公开评论、审核、状态管理、Lv 门槛和接收者专属 QQ 授权 |
| anonymous | 匿名动态：公开配置、匿名发布、归属查询、管理端配置 |
| bot | NapCat 个人 QQ 动态助手：DeepSeek 受限意图规划与多轮聊天、持久化绑定续接、发动态、群内引用同步动态评论、资料修改、积分/签到状态、签到、群动态同步和 QQ 空间每日动态合集 |
| proxy | 未迁移接口和受控旧端委托 |

### 8.2 混合处理

- contentsAdd/contentsUpdate 只在普通 post/video 场景由新端完整写入；付费、草稿、商品关联和未知类型可委托旧端。
- 官方充值、卡密和支付回调保留旧实现，但登录用户发起请求必须先经过新端 token/角色守卫；
  支付供应商回调不使用用户 token，按原签名边界转发。
- token-bearing 列表、广告管理等路径是否进入新端取决于精确 Nginx 路由。
- 旧聊天、上传、社会化绑定和支付创建仍由 8081 执行业务，但安全切流后公网必须先进入
  18082 代理；staff/administrator 专用接口由新端在转发前校验角色。

### 8.3 仍依赖旧端

- 短信验证码发送；邮箱验证码已在新后端实现，公网是否生效取决于精确路由。
- QQ、微信、微博等社会化登录/绑定。
- upload/full。
- 私聊、群聊和聊天管理。
- 官方支付创建、卡密和原支付回调。
- 插件接口和未知插件内容（匿名动态已在新后端原生实现，不依赖 PHP 插件）。

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
- 动态评论在详情页底部弹层内完成发送，发送期间锁定提交以避免重复评论；评论回复在当前页按连接线树状展开，不再跳转独立回复详情页。
- 动态详情显示完整正文和全部图片，图片预览保持在当前业务页内。
- 注册必须从后台启用项中选择校区和入学年份；后台可新增、改名、排序和停用选项。
- 用户选择的校区会在动态列表、关注动态、动态详情、自己主页和他人主页以低对比度辅助文字展示；入学年份只在自己主页和他人主页以更弱的辅助文字展示，不在动态卡片上增加视觉噪音。
- 动态首页顶部最多同时选择 3 个话题，标签保持高亮并按 AND 模式筛选；点击或滚动下方动态流会收起筛选面板。
- 动态列表只折叠经判定的长正文并提供“查看全文”，详情页始终解除正文高度限制。
- 动态作者可从列表或详情页编辑、删除自己的动态；后端仍以 token 解析出的 uid 检查所有权，staff 操作使用同一接口并保留原作者。
- 动态管理页仅向 administrator/editor 提供精华、列表置顶和横幅置顶控制；置顶可选长期、24 小时、7 天或 30 天，重复设置会按最新操作提高展示顺序。待审、私密、锁定和回复动态不能启用展示状态。
- 动态页“全部”视图最多展示 3 条横幅和 3 条紧凑列表置顶，横幅优先使用动态首图、无图时使用正文；置顶项从普通首屏查询中排除。话题、关注、视频、图集和精华筛选不混入横幅，旧文章置顶数据不再用于动态页。
- 动态列表和详情使用低对比度“精华/置顶/横幅”标识；过期置顶的配置仍可在管理页识别，但公开接口把其有效 `pinType` 归零。
- 其他登录用户可在动态列表或详情页选择固定原因举报公开动态；同一用户不能重复举报同一动态，也不能举报自己的动态。
- 举报入口使用统一底部面板，展示原因说明、选中状态、隐私提示和提交状态；动态列表与详情页共用同一交互和日间/夜间样式。
- 发布页的“添加组件”默认保持为一行工具入口，选择投票后才打开分组编辑面板；投票支持标题、简介、2--6 个不重复选项、单选或限项多选。已发布投票先展示选项，提交后切换为带百分比和票数的结果条，并在列表、详情和窄屏个人动态卡片中复用同一组件。
- 管理控制台为 administrator/editor 提供“风险审核”，可处理用户举报，也可查看 AI 通过/拒绝/异常原因并反复公开或隐藏动态；AI 人工改判不物理删除内容，每次操作单独留痕。
- 原“动态审核”开关是全部 AI 风险审核的总门控；关闭后配置、密钥和历史继续保留。AI 子开关分别控制动态、提问和每日评论巡检，评论巡检默认北京时间 03:30，覆盖动态评论、问答回答和问答评论并生成每日总结。
- 问答、动态和普通帖子评论支持长按复制；评论头像和名称可进入对方主页，已注销用户只提示不可进入。
- 个人简介保留换行并支持显式清空。

### 9.2 内容与后台

- 帖子管理使用新后端时必须保留后台所需状态和权限字段。
- 推荐、置顶、轮播字段由内容扩展接口维护。
- 后台话题管理复用 tag/meta 管理页；校区和年级使用独立的稳定选项目录。
- PHP admin 配置接口仍直接访问 admin.lcxqy.cn；“功能设置 → 校园互助”提供迁移 014 的开关、等级、审核、联系方式限额和有效期设置，以及互助信息审核、解决、重开和关闭。PHP 页面只展示公开互助内容，不读取或展示 QQ、邮箱或联系方式。
- PHP admin 统一启用 `HttpOnly`、`Secure`、`SameSite=Lax` 严格会话 Cookie，登录后轮换
  session id；历史 MD5 管理员密码在密码列完成 010 迁移后于成功登录时升级为 `password_hash()`。
- 匿名动态入口在首页发布面板，复用动态发布页（`?anonymous=1`），支持图片、视频和最多 3 个话题；匿名动态以专用匿名账号发布，真实发布者只存服务端映射表，公开接口不返回映射；管理端“匿名动态”模块可配置匿名账号与审核开关。
- QQ 动态助手使用个人 QQ 号登录 NapCat，再通过 OneBot v11 反向 WebSocket 接 AstrBot；不是 QQ 官方机器人号。后端 `SFreeBot/*` 只开放动态工具，不开放帖子/文章能力。QQ 空间每日同步将每条公开已审核动态生成一张 `P1-P9` 编号图片，NapCat 提供空间凭据后由插件调用 QQ 空间上传/发布接口完成一条最多九图的说说；发布时间和图片模板由 PHP 后台配置，成功后才推进独立游标。
- 首页帖子流下方保留独立的“校园问答”问题卡片，并且只从后台标记为推荐的已发布问答中随机展示最多 4 条；动态页顶部另提供“普通动态 / 提问区”切换，提问区每次进入时重新随机排列已发布问答，当前浏览和详情返回期间保持顺序稳定。点击卡片进入问题详情，回答、回答评论和评论回复均在当前页完成。
- 发布面板提供“提出问题”入口。普通登录用户填写标题、问题说明和话题后提交，后端固定写入待审核状态并忽略客户端传入的发布、推荐、排序和创建人字段；管理员和编辑从管理控制台的“问答管理”审核、编辑、排序、推荐、发布或停用问题。
- 问答正文保留换行；回答至少 4 字，评论至少 1 字；前端发送期间锁定按钮，后端再做 20 秒相同内容防重复。
- 原商城入口统一显示为“校园互助”，分为寻求帮助和提供帮助，包含失物招领、物品借用、学习互助、校园生活和其他帮助；列表、详情、我的互助和发布页不展示价格、库存、VIP 或购买入口。
- 互助讨论只使用公开评论，不恢复私信。默认 Lv2（当前 50 经验）才能发布、评论和交换联系方式；开关、最低等级、审核、QQ 发送开关、每日上限和有效期由后台设置。
- QQ 不出现在公开信息或评论接口。发布者与评论者只能依附一条双方相关的公开评论，单向发送自己绑定 QQ 邮箱对应的 QQ 号；授权表不保存 QQ 明文，读取接口只向指定接收者返回，发送者只能看到“已发送”。删除评论同时撤销关联授权。
- 互助发布页沿用校园论坛的固定导航、底部主操作、分类底部面板、离开确认和上传/提交状态动画；从主 Tab 的发布按钮直接发布成功后，详情页的标题栏返回和 Android 物理返回均先进入校园互助列表，只有再从列表返回才回到首页；从互助列表或“我的互助”进入时仍按原页面栈返回。交互动效只用于反馈，不使用商城促销式视觉。

### 9.3 消息和聊天

站内通知仍保留；动态评论和回复会写入 `starfree_inbox` 的 `spaceComment` 类型并携带原动态 id 与评论 id，消息中心可直接打开并定位评论；问答新回答写入 `qaAnswer`，回答评论和评论回复写入 `qaComment`，消息数据同时提供问题、回答和评论定位字段，消息中心点击可展开对应回答并滚动到目标评论。校园互助评论/回复写入 `lostFoundComment`，定向联系方式分享写入 `lostFoundContact`，二者均携带互助信息 id 和评论 id；发布者、被回复评论者或联系方式接收者会同时得到站内信、已配置时的 UniPush 和已配置 SMTP 时的邮件提醒，邮件正文不包含 QQ 明文。点击一条未读消息时，前端通过 `setRead(id)` 只标记当前用户的该条通知，返回消息中心后该条红点不能恢复；失败时恢复本地未读状态，并由 `unreadNum` 重新校准。动态和问答评论树前端最多展示 3 级，3 级之后按创建时间平铺。动态点赞只记录点赞日志和计数，不产生站内信、推送或邮件通知。四个主 Tab 的消息入口必须同步 `SFreeUsers/unreadNum`：H5 使用原生 tabBar 红点，App 自定义底栏使用同一未读事件；进入消息中心保留未读状态，用户点击“全部已读”且 `setRead` 成功后才统一清除列表未读点和底栏红点，页面重新激活或收到推送时重新校准。UniApp Push 使用用户绑定的 `clientId`，后端 UniPush 发送器由 `UNIPUSH_ENABLED` 和运行时凭据控制，支持 `unipush.protocol=v1`（UniPush 1.0 / 个推 REST v1，`auth_sign` + `push_single`）与 `v2`（个推 REST v2）两种协议，生产按 App 打包版本选择 v1。推送失败不影响站内消息落库。私聊和群聊前端入口已收敛或隐藏。旧聊天接口未重建，不能把消息中心超时直接归因于新后端。

消息、聊天、动态及用户列表统一使用 `campus-avatar` 展示头像。公开名称优先使用 `screenName`，账号名只作后备；QQ 邮箱且未设置头像时使用 QQ 头像地址。头像字段为空、用户已注销或图片加载失败时显示姓名首字占位，纯数字账号使用通用用户图标，不能把 QQ 号首位数字当作头像。

### 9.4 主题和布局

- 支持日间、夜间和自动主题。
- 桌面 H5 必须保持内容居中，轮播、资料页和发布页不能随宽屏向左偏移。
- 日间和夜间状态必须隔离，切换后不能残留另一主题背景。
- 主题最终状态由 `utils/campusTheme.js` 解析，并同步页面壳、原生 TabBar、Vuex `AppStyle` 和带有 `campusThemeMode` 的页面；旧页面与新页面必须使用同一解析结果。
- 深夜模式采用深灰绿的背景、表面、输入和文字层级，统一覆盖旧 ColorUI 页面常见的白色卡片、列表、表单、弹窗和加载区域；不能使用纯黑大块或降低正文可读性。
- 核心操作保持原生、紧凑和校园论坛风格，避免过度装饰和大面积营销式卡片。
- 四个主 Tab（此刻、动态、消息、我的）只通过底部导航切换；页面级左右滑动切换已移除，避免正常滚动和操作时误触。

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
Tests run: 364
Failures: 0
Errors: 0
Skipped: 0
~~~

测试覆盖控制器兼容、权限、审核、幂等、Redis 登录态、MyISAM 补偿、动态可见性、动态精华/置顶权限与有效期查询、内容委托、匿名动态权限与隐私边界、普通用户问题待审核提交、动态举报去重与 staff 处理、经济操作、轻量邀请奖励、校园互助 Lv 门槛/所有权/状态流转/公开评论/QQ 接收者投影和评论删除撤销授权、评论/回复/联系方式分享的站内信/推送/邮件通知与重复分享幂等，以及邮箱验证码协议、限流、SMTP 异常分类、认证退避和失败清理。

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
- 通知、聊天、动态详情和用户列表在头像为空或图片请求失败时仍显示占位头像。
- 消息中心、问答评论、动态评论和普通帖子评论可从头像或名称进入对方主页；纯数字账号不显示数字头像。
- 动态作者在列表和详情页都能编辑、删除；其他用户可举报，重复举报和举报自己的动态会被拒绝。
- administrator/editor 可进入“举报审核”，分别验证驳回举报、删除原动态、原动态已删除和重复处理状态。
- 问答、动态和普通帖子评论长按后复制原始评论文字。
- 管理端帖子、分类、话题和轮播设置。
- 发布面板“提出问题”、未登录拦截、重复提交拦截和提交后的待审核提示。
- 动态页“普通动态 / 提问区”切换、提问区问答卡片分页、下拉刷新和返回位置恢复。
- 动态页“全部 / 精华”筛选、横幅自动轮播、列表置顶跳转、普通流去重，以及置顶到期后自动从公开展示区消失。
- 动态管理页使用 staff 管理视图读取全部待审/已发布/已锁定数据，并验证非 staff、待审、私密和回复动态无法伪造展示状态。
- 首页问答卡片、问题详情、回答排序、点赞、回答评论/回复，以及管理端待审核问题发布、编辑和停用。
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
- upload/full、聊天、短信验证码和官方支付仍依赖闭源后端。
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
