# NapCat 个人 QQ / AstrBot 动态助手实现说明

> 更新日期：2026-08-09
>
> 当前状态：后端、PHP 后台配置页、AstrBot/NapCat 插件已在本仓库实现；生产发布仍需按部署手册单独执行。

本文固定个人 QQ 账号助手的业务边界：**动态是唯一用户内容入口**。这里的 Bot 指 NapCat 登录的个人 QQ 号 + AstrBot 自动化助手，不是 QQ 官方机器人号。插件不提供帖子、文章、contents、content detail、文章评论、文章收藏等能力；用户表达“发帖”时，插件也必须解释为“发动态”。

## 1. 功能范围

已实现能力：

1. DeepSeek 聊天：个人 QQ 号收到消息后由 AstrBot 插件调用后端 `SFreeBot/chat`，DeepSeek Key 只保存在后端配置表。
2. QQ 绑定论坛账号：NapCat 账号助手生成绑定链接，用户在独立绑定页输入论坛账号密码；后端只验证密码并写 QQ 绑定关系，不创建普通登录 token，不改 `authCode`，不写 Redis session。
3. 动态工具：发动态、修改资料、查询积分/签到状态、签到。
4. 群动态同步：后台配置或群命令登记 QQ 群后，插件轮询最新公开已审核动态并发送动态 H5 链接、摘要和图片。
5. 幂等与投递：发动态、改资料、签到记录 requestId；群同步只有发送成功后才推进游标。
6. 对话状态：草稿、绑定续接和最近对话保存在 AstrBot 插件数据目录，插件重启后仍可恢复。

明确不做：

- 不绕过动态审核、发帖频率、经验门槛、违禁词和封禁校验。
- 不保存用户论坛密码。
- 不用共享论坛 token 代表所有 QQ 用户。
- 不把普通帖子/文章接口暴露给 Bot。
- 不在插件里直接写 MySQL。

## 2. 组件结构

~~~text
个人电脑上的 NapCat 登录个人 QQ 号
      │ OneBot v11 反向 WebSocket（WSS + 独立 Token）
      ▼
服务器 AstrBot 插件 integrations/qqbot/astrbot_plugin_lcxqy_dynamic_ai
      │ 表单协议 + Bot Secret
      ▼
Spring Boot /SFreeBot/*
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

- `lcxqy_bot_config`：Bot 开关、Bot Secret 兜底值、DeepSeek Key/Base/Model、H5 地址、同步间隔、工具开关。
- `lcxqy_bot_bind_challenge`：一次性绑定 token，短期有效，只能使用一次。
- `lcxqy_bot_bindings`：QQ 用户与论坛 uid 的持久绑定。
- `lcxqy_bot_group_sync`：可同步群、`unified_msg_origin`、游标和摘要策略。
- `lcxqy_bot_operation_log`：requestId 幂等日志。
- `lcxqy_bot_delivery_log`：群投递结果；失败不推进游标。

生产推荐通过环境变量 `LCXQY_QQBOT_SECRET` 设置 Bot Secret。配置表中的 `bot_secret` 仅作为没有环境变量时的兜底。

生产后台只显示 Bot Secret 是否已配置，不回填原文，也不在普通“保存 QQ Bot 设置”操作中更新它。Secret 轮换必须作为独立运维操作，同步更新服务器 Secret、AstrBot 插件配置和后端配置，并在切换后立即验证 `/SFreeBot/config`。

## 4. 后端接口

所有插件调用都带 `botSecret` 和 `platform=qq`，使用 `application/x-www-form-urlencoded`，返回旧协议 `{code,msg,data}`。

| 接口 | 用途 | 关键参数 |
|---|---|---|
| `POST /SFreeBot/config` | 读取 Bot 开关、工具开关、同步群 | `botSecret,platform` |
| `POST /SFreeBot/chat` | DeepSeek 聊天代理 | `message` 或 `messages` JSON |
| `POST /SFreeBot/bindChallenge` | 生成 QQ 绑定链接 | `qqUserId` |
| `GET /SFreeBot/bindPage` | 独立绑定登录页 | `token` |
| `POST /SFreeBot/bindLogin` | 验证论坛账号密码并绑定 QQ | `token,account,password` |
| `POST /SFreeBot/meStatus` | 查询论坛账号、积分、经验、余额、签到连续天数 | `qqUserId` |
| `POST /SFreeBot/signin` | QQ 绑定用户签到 | `qqUserId,requestId` |
| `POST /SFreeBot/addSpace` | 发动态 | `qqUserId,requestId,text,pic,topicIds,onlyMe` |
| `POST /SFreeBot/updateProfile` | 修改资料白名单字段 | `qqUserId,requestId,screenName,introduce,avatar,campusId,gradeId` |
| `POST /SFreeBot/registerGroup` | 登记当前群同步 | `groupId,groupName,unifiedMsgOrigin` |
| `POST /SFreeBot/latestSpaces` | 拉取公开已审核动态 | `groupId,afterId,limit` |
| `POST /SFreeBot/delivery` | 回写群投递结果 | `groupId,spaceId,status,messageId,error` |

`addSpace` 只写 `starfree_space.type=0` 普通动态，`toid=0`，复用 `SpaceService` 既有校验。图片字段沿用动态接口的 `pic` 字符串格式；图片上传仍由现有前端/旧上传能力承担，Bot 只提交已可用的图片 URL。

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

每个 QQ 私聊或群内用户会话保存最近 12 条对话和当前操作阶段，状态文件位于 `StarTools.get_data_dir("lcxqy_dynamic_ai")/state.json`，采用临时文件替换方式写入，默认保留 7 天。

普通聊天默认可在私聊中启用；群聊普通聊天默认关闭，避免群内每句话触发 AI。群同步不依赖普通聊天开关。

插件处理私聊、确认操作或功能命令时会终止 AstrBot 默认 LLM 事件链，避免一条 QQ 消息同时获得论坛助手和默认模型的两次回复。无斜杠命令（如 `发动态`）也不再进入普通聊天分支。

## 7. 群同步规则

后台新增同步群时只需填写 QQ 群号，群名可选，勾选启用即可。平台固定为 `qq`，`unified_msg_origin` 自动生成：

~~~text
lcxqy_onebot:GroupMessage:<QQ群号>
~~~

游标、最近成功时间和最近错误由系统维护。“绑定本群同步”命令仍可用于自动登记当前群，但不再是必需步骤。

- 只同步 `starfree_space.status=1` 且 `onlyMe=0` 的公开动态。
- 默认排除动态回复 `type=3`。
- 消息包含作者显示名、摘要、话题、动态 H5 链接和最多 N 张图片。
- 发送成功后调用 `SFreeBot/delivery status=success`，后端才推进 `cursor_space_id`。
- 发送失败只记录错误，不推进游标，下一轮可继续重试。

动态 H5 链接格式：

~~~text
https://prev.lcxqy.cn/#/pages/space/info?id=<spaceId>
~~~

实际域名由后台 `QQ Bot设置 -> 动态 H5 地址` 控制。

## 8. 部署配置

AstrBot 插件配置：

~~~json
{
  "backend_base_url": "https://api.lcxqy.cn",
  "bot_secret": "与后端一致",
  "platform": "qq",
  "chat_enabled": true,
  "chat_in_groups": false,
  "sync_enabled": true,
  "sync_limit": 5
}
~~~

插件版本 `v0.2.0` 开始要求 AstrBot 提供 `StarTools.get_data_dir`；旧版本 AstrBot 无该能力时插件仍可运行，但不会持久化会话，应优先升级服务器 AstrBot。

本地针对性测试：

~~~powershell
python -m unittest integrations.qqbot.tests.test_lcxqy_dynamic_ai -v
mvn -f backend/starfree-replacement/pom.xml -Dtest=BotServiceTest test
php -l admin/starfree-admin/source/admin/qqBot.php
php -l admin/starfree-admin/source/admin/qqBotPost.php
~~~

生产环境的 NapCat 运行在个人电脑，保留个人 QQ 的设备登录态；服务器只运行 AstrBot，不再启动第二个 NapCat。NapCat 的 WebSockets 客户端连接 `wss://api.lcxqy.cn/onebot/v11/ws`，Token 使用服务器 `/srv/lcxqy/qqbot/secrets.env` 中的 `LCXQY_ONEBOT_TOKEN`。不要按 QQ 官方 Bot 号能力设计，也不要使用官方 Bot AppId/Secret 流程。不要把 QQ 密码、论坛用户密码、DeepSeek Key、Bot Secret 或 OneBot Token 写进仓库。

如果 NapCat 需要保留其他 WebSocket 客户端，可以继续保留，但 `lcxqy_dynamic_ai` 只能在服务器 AstrBot 启用。同时在本机 AstrBot 和服务器 AstrBot 安装该插件会导致命令重复回复。

生产部署在迁移 006 和 replacement JAR 完成后，以 root 执行
`backend/deploy/production/promote-qqbot-routes.sh`。脚本只新增 12 个
`location = /SFreeBot/...` 精确路由，备份原 Nginx include，并在 reload 后校验
`X-Starfree-Backend: replacement-qqbot`。

服务器 AstrBot 的 6199 端口只绑定 `127.0.0.1`。本机 NapCat 需要连接时，另以 root
执行 `backend/deploy/production/promote-astrbot-onebot-route.sh`。脚本只新增
`location = /onebot/v11/ws`，验证无 Token 返回 401、正确 Token 完成 101 WebSocket
升级，并在失败时恢复 Nginx 备份。AstrBot 管理页 6185 不对公网开放。
