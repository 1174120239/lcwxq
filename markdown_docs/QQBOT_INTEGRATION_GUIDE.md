# NapCat 个人 QQ / AstrBot 动态助手实现说明

> 更新日期：2026-08-10
>
> 当前状态：后端、PHP 后台配置页、AstrBot/NapCat 插件已在本仓库实现；生产发布仍需按部署手册单独执行。

本文固定个人 QQ 账号助手的业务边界：**动态是唯一用户内容入口**。这里的 Bot 指 NapCat 登录的个人 QQ 号 + AstrBot 自动化助手，不是 QQ 官方机器人号。插件不提供帖子、文章、contents、content detail、文章评论、文章收藏等能力；用户表达“发帖”时，插件也必须解释为“发动态”。

## 1. 功能范围

已实现能力：

1. DeepSeek 聊天：个人 QQ 号收到消息后由 AstrBot 插件调用后端 `SFreeBot/chat`，DeepSeek Key 只保存在后端配置表。
2. QQ 绑定论坛账号：NapCat 账号助手生成绑定链接，用户在独立绑定页输入论坛账号密码；后端只验证密码并写 QQ 绑定关系，不创建普通登录 token，不改 `authCode`，不写 Redis session。
3. 动态工具：发动态、评论动态、修改资料、查询积分/签到状态、签到。
4. 群动态同步：后台配置或群命令登记 QQ 群后，插件轮询最新公开已审核动态并发送动态 H5 链接、摘要和图片。
5. 群内引用评论：用户引用云云同步的动态并输入正文，即以当前 QQ 已绑定的论坛账号评论该动态。
6. 幂等与投递：发动态、评论动态、改资料、签到记录 requestId；群同步只有发送成功后才推进游标。
7. 对话状态：草稿、绑定续接和最近对话保存在 AstrBot 插件数据目录，插件重启后仍可恢复。
8. QQ 空间每日图集：按后台设定的北京时间批量读取公开动态，每条动态生成一张带 P 编号的图片，并通过 NapCat 个人 QQ 发布一条空间说说。

明确不做：

- 不绕过动态审核、发帖频率、经验门槛、违禁词和封禁校验。
- 不保存用户论坛密码。
- 不用共享论坛 token 代表所有 QQ 用户。
- 不把普通帖子/文章接口暴露给 Bot。
- 不在插件里直接写 MySQL。

## 2. 组件结构

~~~text
个人电脑上的 NapCat 登录个人 QQ 号
      │ OneBot v11 反向 WebSocket（ws://127.0.0.1:8083/ws）
      ▼
同一台个人电脑上的 AstrBot 插件 integrations/qqbot/astrbot_plugin_lcxqy_dynamic_ai
      │ 表单协议 + Bot Secret
      ▼
公网 Spring Boot /SFreeBot/*
      │
      ├─ starfree_space：动态发布与同步源
      ├─ starfree_users：论坛账号、积分、经验、资料
      ├─ starfree_admin_Signinlog / starfree_paylog：签到
      └─ lcxqy_bot_*：绑定、配置、幂等、投递
~~~

后台配置页位于 `admin/starfree-admin/source/admin/qqBot.php`，直接维护 `lcxqy_bot_config` 与 `lcxqy_bot_group_sync`。

## 3. 数据表

迁移文件：`backend/database/migrations/006_qqbot_dynamic_ai.sql`。

新增表：

- `lcxqy_bot_config`：Bot 开关、Bot Secret、DeepSeek Key/Base/Model、H5 地址、同步间隔、工具开关，以及 QQ 空间发布时间、图片模板、独立游标和最近投递状态。
- `lcxqy_bot_bind_challenge`：一次性绑定 token，短期有效，只能使用一次。
- `lcxqy_bot_bindings`：QQ 用户与论坛 uid 的持久绑定。
- `lcxqy_bot_group_sync`：可同步群、`unified_msg_origin`、游标和摘要策略。
- `lcxqy_bot_operation_log`：requestId 幂等日志。
- `lcxqy_bot_delivery_log`：群投递结果；失败不推进游标。

Bot Secret 可以直接在后台 `QQ Bot设置` 中设置，输入新密码才更新，留空保持原值；后台只显示是否已配置，不回显原文。后端优先使用配置表中的 `bot_secret`，仅在配置表为空时使用环境变量 `LCXQY_QQBOT_SECRET` 兜底。设置后把同一个密码填入 AstrBot 插件的 `bot_secret`。

Secret 建议使用 16 位以上随机字符串，不要使用 QQ 密码或 DeepSeek Key。轮换时在后台设置新密码，再同步修改 AstrBot 插件并验证 `/SFreeBot/config`。

## 4. 后端接口

所有插件调用都带 `botSecret` 和 `platform=qq`，使用 `application/x-www-form-urlencoded`，返回旧协议 `{code,msg,data}`。

| 接口 | 用途 | 关键参数 |
|---|---|---|
| `POST /SFreeBot/config` | 读取 Bot 开关、工具开关、同步群、QQ 空间模式、立即发布任务和引用评论能力标记 | `botSecret,platform` |
| `POST /SFreeBot/chat` | DeepSeek 聊天代理 | `message` 或 `messages` JSON |
| `POST /SFreeBot/bindChallenge` | 生成 QQ 绑定链接 | `qqUserId` |
| `GET /SFreeBot/bindPage` | 独立绑定登录页 | `token` |
| `POST /SFreeBot/bindLogin` | 验证论坛账号密码并绑定 QQ | `token,account,password` |
| `POST /SFreeBot/meStatus` | 查询论坛账号、积分、经验、余额、签到连续天数 | `qqUserId` |
| `POST /SFreeBot/signin` | QQ 绑定用户签到 | `qqUserId,requestId` |
| `POST /SFreeBot/addSpace` | 发动态或评论动态 | 发动态使用 `qqUserId,requestId,text,pic,topicIds,onlyMe`；评论额外使用 `type=3,toid=<动态ID>` |
| `POST /SFreeBot/updateProfile` | 修改资料白名单字段 | `qqUserId,requestId,screenName,introduce,avatar,campusId,gradeId` |
| `POST /SFreeBot/registerGroup` | 登记当前群同步 | `groupId,groupName,unifiedMsgOrigin` |
| `POST /SFreeBot/latestSpaces` | 拉取公开已审核动态 | `groupId,afterId,limit` |
| `POST /SFreeBot/delivery` | 回写群投递结果 | `groupId,spaceId,status,messageId,error` |
| `POST /SFreeBot/qzoneBatch` | 读取 QQ 空间待发布批次、发布模式、立即任务 token 和图片模板配置 | `botSecret,platform` |
| `POST /SFreeBot/qzoneDelivery` | 回写 QQ 空间投递结果，并在成功时完成对应立即任务 | `status,maxSpaceId,tid,error,publishNowToken` |

`addSpace` 默认只写 `starfree_space.type=0` 普通动态，`toid=0`。引用评论场景额外允许 `type=3`，此时 `toid` 必须是正整数，`onlyMe` 强制为 0，图片和话题被忽略。两种场景都复用 `SpaceService` 既有校验；评论还会执行目标存在、公开、未锁定、20 秒重复评论和评论通知检查。图片字段沿用动态接口的 `pic` 字符串格式；图片上传仍由现有前端/旧上传能力承担，Bot 只提交已可用的图片 URL。

新后端在 `config` 返回顶层 `commentSpace=true`。插件提交评论前必须先确认该标记；旧后端没有标记时只提示后端尚未升级，绝不能继续调用 `addSpace`，以免旧实现把 `type=3` 强制改成普通动态。

## 5. 绑定流程

1. QQ 用户发送 `/绑定论坛`。
2. 插件调用 `SFreeBot/bindChallenge`，后端写入随机 `bind_token`。
3. Bot 回复 `/SFreeBot/bindPage?token=...`。
4. 用户在绑定页输入论坛账号/邮箱和密码。
5. 后端用 `PhpassPasswordVerifier` 校验 `starfree_users.password`。
6. 后端 upsert `lcxqy_bot_bindings(platform,qq_user_id,forum_uid)` 并标记 challenge 已使用。

该流程不会调用 `SFreeUsers/userLogin`，不会生成论坛 token，不修改 `authCode`，不会刷新或删除 Redis 登录态。

## 6. 对话与命令

插件目录：`integrations/qqbot/astrbot_plugin_lcxqy_dynamic_ai`。

普通用户优先直接使用自然语言：

~~~text
帮我发个动态
签到顺便看看积分
把昵称改成小明
绑定论坛账号
~~~

插件会在缺少动态内容或资料值时继续追问。DeepSeek 只负责输出白名单意图 JSON，实际接口路径、参数白名单、身份检查、预览和确认都由插件确定性代码执行，不允许模型任意调用接口。

旧命令继续兼容：

~~~text
/动态助手
/绑定论坛
/我的状态
/签到
/发动态 今天操场晚霞很好看
/修改资料 昵称 新昵称
/修改资料 简介 新简介
/绑定本群同步
~~~

发动态和修改资料是敏感写操作，插件会先生成预览。确认词支持 `确认`、`确认发布`、`确认修改`、`发吧`、`继续`、`可以`；取消词支持 `取消`、`算了`、`不发了`、`不改了`。

如果确认时发现 QQ 尚未绑定论坛账号，插件保留原草稿并发送绑定链接。用户完成登录后回复 `好了`，插件调用 `meStatus` 验证绑定，恢复原操作并再次询问是否继续，不会未经确认自动发布。

在群里引用云云同步的动态时，引用消息必须由云云本人发送，并包含精确的动态 H5 链接 `/pages/space/info?id=<动态ID>`。用户当前输入的正文会直接作为评论，不再增加发布预览；空正文会继续追问，超过 1500 字会要求精简。若 QQ 尚未绑定论坛账号，插件会保留动态 ID、评论正文和原 `requestId`，发送绑定链接；绑定完成后由用户回复 `继续` 才提交。

每个 QQ 私聊或群内用户会话保存最近 12 条对话和当前操作阶段，状态文件位于 `StarTools.get_data_dir("lcxqy_dynamic_ai")/state.json`，采用临时文件替换方式写入，默认保留 7 天。

普通聊天默认可在私聊中启用；群聊普通聊天默认关闭，避免群内每句话触发 AI。群同步不依赖普通聊天开关。

普通聊天人格为“云云”：亲切、机灵、略微傲娇，允许偶尔自然使用一次“喵”，但不重度堆叠猫语，也不输出大段动作描写。校园公共场景不接入成人露骨设定。回复默认控制为一到三句短句，通常不超过 120 个汉字；用户明确要求详细说明时才适当展开。

群聊触发规则由后台 `QQ Bot设置 -> 群聊普通对话` 控制。开启时，只有 @云云、消息中直接叫“云云”、引用云云上一条消息，或同一用户正在继续已开启的绑定/动态/资料操作时才处理；关闭时普通闲聊、纯 @ 和普通聊天引用不触发 AI，但动态同步、引用动态评论和明确的论坛操作不受影响。插件会定期刷新该开关，最长约 15 秒生效。

插件处理私聊、确认操作或功能命令时会终止 AstrBot 默认 LLM 事件链，避免一条 QQ 消息同时获得论坛助手和默认模型的两次回复。无斜杠命令（如 `发动态`）也不再进入普通聊天分支。

## 7. 群同步规则

后台新增同步群时只需填写 QQ 群号，群名可选，勾选启用即可。平台固定为 `qq`，`unified_msg_origin` 由插件根据当前 AstrBot OneBot 适配器自动确定，例如：

~~~text
001:GroupMessage:<QQ群号>
~~~

游标、最近成功时间和最近错误由系统维护。“绑定本群同步”命令仍可用于自动登记当前群，但不再是必需步骤。

- 只同步 `starfree_space.status=1` 且 `onlyMe=0` 的公开动态。
- 默认排除动态回复 `type=3`。
- 群游标为 0 时只发送当前最新一条动态，再从该位置继续监听，避免首次启用补发全部历史动态。
- 消息包含作者显示名、摘要、话题、动态 H5 链接和最多 N 张图片。
- 群友可直接引用这条同步消息并输入评论；插件只认云云本人发送且带上述精确 H5 链接的引用，引用其他群友或云云普通聊天消息不会触发论坛评论。
- 发送成功后调用 `SFreeBot/delivery status=success`，后端才推进 `cursor_space_id`。
- 发送失败只记录错误，不推进游标，下一轮可继续重试。

动态 H5 链接格式：

~~~text
https://prev.lcxqy.cn/#/pages/space/info?id=<spaceId>
~~~

实际域名由后台 `QQ Bot设置 -> 动态 H5 地址` 控制。

## 8. QQ 空间同步

后台 `QQ Bot设置 -> QQ 空间每日同步` 控制全部业务参数，AstrBot 插件只需保留后端地址、Bot Secret 和同步轮询开关。可调项包括：

- 启用开关和每天发布时间，固定按 `Asia/Shanghai` 判断。
- 发布模式：`按时间发布` 每天到点执行一次；`随时发布` 在发现游标后有新动态时执行。
- `立刻发布` 按钮生成一次性任务，绕过当天已发布限制；成功后才完成任务，失败保留并重试。
- 每批 1 到 9 条动态、每条摘要 20 到 200 字；QQ 空间单条说说最多使用 9 张图片。
- 是否使用动态首图、是否显示校区和话题。
- 图片标题、副标题、底部文案、简短说说正文、背景色、强调色、文字色、卡片色和可选背景图 URL。
- QQ 空间可见范围：所有人、QQ 好友或仅自己。

同步规则：

- 数据源只包含 `starfree_space.status=1 AND onlyMe=0 AND type<>3`，动态仍是唯一内容核心。
- 独立游标为 0 时取当前最新一批，避免补发全部历史；之后只取成功游标之后的增量。
- 同一批动态各生成一张 1080px 宽的竖版 PNG，按 `P1-P9` 编号。说说正文只使用后台填写的简短文案，不再自动追加 P 编号、作者和摘要清单。NapCat 只通过 OneBot `get_cookies(domain=user.qzone.qq.com)` 提供当前个人 QQ 的空间凭据；插件上传图片后直接调用 QQ 空间发布接口，不使用 NapCat 不支持的 `send_qzone_msg`。
- 图片显示作者、校区/入学年份、摘要、话题和可选首图；没有首图或下载失败时自动使用纯文字布局。
- 远程图片只允许公网 HTTP(S)，单图最大 8 MB，重定向目标也必须通过公网地址检查。
- 没有新动态时不发布；失败只记录错误且不推进游标，插件最早 15 分钟后重试。
- 发布成功后记录 `qzone_cursor_space_id`、`qzone_last_run_date`、`qzone_last_tid`、`qzone_last_success_at`，同一天不重复发布。

NapCat 动作参数：

~~~json
{
  "action": "get_cookies",
  "domain": "user.qzone.qq.com"
}
~~~

这里调用的是 NapCat 登录的个人 QQ 空间能力，不是 QQ 官方机器人接口。测试代码只验证渲染和调用协议，不会自动发布真实空间说说。

## 9. 部署配置

AstrBot 插件配置：

~~~json
{
  "backend_base_url": "https://api.lcxqy.cn",
  "bot_secret": "与后端一致",
  "platform": "qq",
  "chat_enabled": true,
  "sync_enabled": true,
  "sync_limit": 5
}
~~~

插件版本 `v0.3.3` 只发送后台设置的简短空间正文，不再自动追加图片索引清单；`v0.3.2` 使用 NapCat `get_cookies` 与 QQ 空间 HTTP 接口发布图集；`v0.3.1` 将 QQ 空间同步改为每条动态一张编号图片；`v0.3.0` 增加 QQ 空间每日同步。运行环境需要 `Pillow>=10.0.0`，AstrBot 安装插件时会读取 `requirements.txt`。`v0.2.5` 支持从论坛后台控制群聊普通对话，`v0.2.4` 开始支持群内引用同步动态直接评论，`v0.2.0` 开始要求 AstrBot 提供 `StarTools.get_data_dir`；旧版本 AstrBot 无该能力时插件仍可运行，但不会持久化会话，应优先升级服务器 AstrBot。

本地针对性测试：

~~~powershell
python -m unittest integrations.qqbot.tests.test_lcxqy_dynamic_ai -v
mvn -f backend/starfree-replacement/pom.xml -Dtest=BotServiceTest test
php -l admin/starfree-admin/source/admin/qqBot.php
php -l admin/starfree-admin/source/admin/qqBotPost.php
~~~

当前运行拓扑为个人电脑上的 NapCat 连接本机 AstrBot：NapCat 客户端 `001` 使用 `ws://127.0.0.1:8083/ws`，服务器客户端 `lcxqy-astrbot` 必须保持禁用。插件通过 `https://api.lcxqy.cn` 调用论坛后端。不要按 QQ 官方 Bot 号能力设计，也不要使用官方 Bot AppId/Secret 流程。不要把 QQ 密码、论坛用户密码、DeepSeek Key、Bot Secret 或 OneBot Token 写进仓库。

同一个 NapCat 账号只能保留一条承担回复的 AstrBot 消息链。本机与服务器 AstrBot 同时连接时，同一条消息会得到两套回复，表现为预览、LLM 回复和错误提示交错。需要切换回服务器 AstrBot 时，必须先停用本机连接，再启用 `wss://api.lcxqy.cn/onebot/v11/ws`，不能两边同时启用。

生产部署在迁移 006 和 replacement JAR 完成后，以 root 执行
`backend/deploy/production/promote-qqbot-routes.sh`。脚本维护 14 个
`location = /SFreeBot/...` 精确路由，备份原 Nginx include，并在 reload 后校验
`X-Starfree-Backend: replacement-qqbot`。服务器已存在旧 12 条路由时，脚本只追加
`qzoneBatch` 和 `qzoneDelivery`，仍会拒绝重复路由或 header 数量不一致的配置。

服务器 AstrBot 的 6199 端口只绑定 `127.0.0.1`。只有切换回服务器 AstrBot 拓扑时，才以 root
执行 `backend/deploy/production/promote-astrbot-onebot-route.sh`。脚本只新增
`location = /onebot/v11/ws`，验证无 Token 返回 401、正确 Token 完成 101 WebSocket
升级，并在失败时恢复 Nginx 备份。AstrBot 管理页 6185 不对公网开放。
