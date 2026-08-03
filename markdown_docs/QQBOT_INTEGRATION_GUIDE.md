# QQBot / AstrBot 论坛同步设计

> 文档性质：实现规划，不代表插件已经开发或上线。
>
> 更新日期：2026-08-04

本文面向接手插件开发的 AI 或开发者，固定论坛侧的数据、接口和审核边界。AstrBot 的插件 API 以当前安装版本的官方文档为准。

## 1. 目标与边界

目标：

1. QQ 用户首次使用时绑定论坛账号。
2. Bot 读取论坛中已经审核通过的动态/帖子并同步到指定群。
3. 用户在私信中使用 /发帖 提交文本和图片。
4. 帖子先进入论坛，审核状态由论坛决定；只有审核通过后才同步到群。
5. 记录幂等和投递状态，重启或重试不重复发帖。

明确不做：

- 不绕过论坛审核。
- 不把用户密码交给 Bot 或长期保存。
- 不直接修改论坛核心表来代替接口业务。
- 不把支付、验证码、上传、聊天和插件旧功能复制到 Bot。
- QQ 空间只作为可插拔的二期发布器，失败不能影响论坛和群同步。

## 2. 推荐架构

~~~text
QQ 私信/群消息
        │
        ▼
AstrBot 插件
  ├─ 绑定、命令解析、图片下载
  ├─ ForumClient：调用论坛 API
  ├─ SyncWorker：读取审核状态
  └─ DeliveryStore：幂等和投递记录
        │
        ├─ 公网 API：api.lcxqy.cn
        ├─ 新后端：127.0.0.1:18082
        └─ 旧后端：127.0.0.1:8081
                         │
                         ├─ MySQL lcxqy
                         └─ Redis 登录态
~~~

推荐将插件和新后端部署在同一台服务器。插件可以访问本机 MySQL、Redis 和 18082，但数据库端口不应开放公网。

## 3. 论坛侧现状

### 3.1 可使用的接口

发帖和读取接口的完整参数见 [API_USAGE_GUIDE.md](API_USAGE_GUIDE.md)。

插件最少需要：

| 用途 | 接口/数据 |
|---|---|
| 登录态确认 | 论坛登录接口或后端内部绑定桥接 |
| 发帖 | SFreeContents/contentsAdd |
| 读取新帖 | SFreeContents/contentsList 或直接读取审核后的数据库 |
| 读取详情 | SFreeContents/contentsInfo |
| 图片上传 | upload/full，当前仍是旧端 |
| 绑定用户 | 新增内部绑定接口或一次性 challenge |

contentsAdd 的兼容表单使用 application/x-www-form-urlencoded。params 是 JSON 字符串，正文换行使用论坛现有的换行约定。HTTP 200 仍需检查业务 code。

### 3.2 不能假设的事情

- 成功响应不一定直接返回 cid，必要时需要按 requestId、作者和时间回查。
- 带 token 的部分列表接口仍可能由旧端处理。
- token 可能只存在 Redis，不能只查用户表 authCode。
- 分类和话题 ID 必须从接口或数据库读取，不能猜数字。
- 付费、草稿、商品关联和未知类型内容可能被新后端委托旧端。

## 4. 账号绑定

### 4.1 首选方案：论坛登录态确认

推荐流程：

1. QQ 用户私信 /绑定。
2. 插件生成随机 challenge，设置短过期时间。
3. 用户在论坛登录后打开一次性绑定链接，或调用后端绑定确认接口。
4. 后端从当前论坛 token 得到 uid，将 uid 与 QQ 平台用户 ID 绑定。
5. challenge 标记为已使用，插件只保存绑定关系，不保存密码。

绑定成功必须同时记录 QQ 平台、QQ 用户 ID、论坛 uid、创建时间、状态和最近使用时间。

### 4.2 可接受的 MVP

管理员在本地配置文件或管理命令中预绑定测试 QQ 与论坛 uid。该方案只用于内测，不能作为公开用户绑定方案。

### 4.3 禁止方案

- 让用户把论坛密码发到群里。
- 将密码写入日志、数据库、payload_json 或模型上下文。
- 用一个共享论坛 token 代表所有 QQ 用户。
- 允许任意群成员通过 uid 猜测完成绑定。

## 5. Bot 自有数据表

插件表与论坛原有表分开。建议使用同一个 MySQL 数据库，但表名必须有独立前缀。

### 5.1 绑定表

~~~sql
CREATE TABLE lcxqy_qqbot_bindings (
  id BIGINT NOT NULL AUTO_INCREMENT,
  platform VARCHAR(32) NOT NULL,
  qq_user_id VARCHAR(64) NOT NULL,
  forum_uid BIGINT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  token_ciphertext TEXT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  last_used_at DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_qqbot_binding (platform, qq_user_id),
  KEY idx_qqbot_forum_uid (forum_uid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
~~~

token_ciphertext 只有在确实需要调用用户级旧接口时才保存，并使用独立密钥加密。更好的长期方案是后端内部桥接接口，不保存用户 token。

### 5.2 Challenge 表

字段至少包括 challenge、platform、qq_user_id、forum_uid、expires_at、used_at 和 created_at。challenge 必须随机、短期、一次性，并建立唯一索引。

### 5.3 同步配置表

保存群号、是否启用、允许同步的分类/话题、是否包含视频、是否允许机器人自己的帖子和摘要长度。群配置变更只允许管理员操作。

### 5.4 发布任务表

建议字段：

- request_id：Bot 生成的唯一请求号。
- platform、qq_user_id、group_id、forum_uid。
- title、body_hash、media_json。
- forum_cid、forum_status。
- state：created、submitted、pending_review、published、failed、cancelled。
- error_message、retry_count、created_at、updated_at。

request_id 必须建立唯一索引。

### 5.5 投递表

建议字段：

- group_id、forum_cid、message_id、delivered_at。
- content_hash、status、error_message。

建立 group_id + forum_cid 唯一索引，保证相同帖子在同一群最多成功投递一次。

## 6. 命令设计

私信命令：

~~~text
/绑定
/绑定 <一次性验证码>
/绑定状态
/解绑
/发帖 <标题>
正文内容
图片：直接附加图片
/发帖
正文内容
~~~

群管理员命令：

~~~text
/同步开启
/同步关闭
/同步状态
/同步话题 <mid,...>
/重同步 <cid>
~~~

第一版只开放私信发帖，群内发帖默认关闭。标题、正文、图片数量、单图大小和用户频率都要校验。

## 7. 发帖流程

1. 校验消息来源是私信，读取绑定关系。
2. 生成 request_id，拒绝同一 request_id 的重复任务。
3. 下载 QQ 图片到临时目录，检查 MIME、大小和数量。
4. 调用旧 upload/full 上传图片，失败则停止，不创建帖子。
5. 将图片 URL 和正文转换为论坛要求的 Markdown。
6. 调用 SFreeContents/contentsAdd。
7. 记录论坛返回的 code、msg、可能的 cid 和状态。
8. 若没有 cid，按 request_id、论坛 uid、标题和时间窗口回查。
9. 读取最终审核状态，任务进入 pending_review。
10. 只在状态为 publish 时进入群投递队列。

插件不能根据“发帖接口返回成功”直接群发。接口成功可能只表示已创建待审核记录。

### 7.1 推荐的后端桥接接口

长期建议新后端提供内部接口：

~~~http
POST /SFreeBot/contentsAdd
~~~

请求字段：

~~~json
{
  "botSecret": "from-environment",
  "platform": "qq",
  "qqUserId": "123456",
  "groupId": "987654",
  "requestId": "qqbot-unique-id",
  "title": "标题",
  "text": "Markdown 正文",
  "topicIds": [],
  "type": "post",
  "imageUrls": []
}
~~~

后端负责校验 Bot 密钥、绑定用户、封禁状态、发帖权限、审核配置、幂等和审计，并返回明确的 cid 与 status。密钥只能来自服务器环境变量或被忽略的本地配置。

## 8. 审核后同步

同步 worker 按 created_at 或 cid 增量读取论坛数据：

1. 只查询公开且 status=publish 的记录。
2. 排除 private、draft、pending、rejected、deleted、paid 和插件类型内容。
3. 根据群配置筛选分类/话题。
4. 读取详情和图片地址，生成群消息。
5. 发送成功后写入投递表。
6. 失败进入有限次数的重试队列。
7. 重启后从游标和投递表恢复，不依赖内存状态。

建议保存 cursor_cid 和 last_scan_at。轮询必须有时间窗口重叠，避免按时间边界丢帖；重复记录由投递唯一键消除。

群消息至少包含标题、作者显示名、摘要、论坛链接和第一张图片。不能把隐藏内容、会员内容、付费内容或未审核内容展开到群。

## 9. 图片和正文

- 单张图片先下载到临时目录，成功上传后立即清理。
- 拒绝未知 MIME、超大图片和明显非图片内容。
- 不信任 QQ 文件名和远端 Content-Type，必要时读取文件头。
- 正文限制长度，过滤危险 HTML 和不允许的远程协议。
- 保留论坛原有 Markdown 换行和图片链接格式。
- 图片上传仍属于旧端能力，插件必须把它作为可失败步骤处理。

## 10. 配置模板

实际配置不要提交。插件配置可以提供以下字段：

~~~yaml
api_base: https://api.lcxqy.cn
web_base: https://prev.lcxqy.cn
sync_interval_seconds: 45
max_images_per_post: 9
max_image_mb: 10
allow_group_publish: false
allowed_group_ids: []
admin_qq_ids: []
enable_qzone: false
bot_secret_env: LCXQY_QQBOT_SECRET
~~~

数据库连接通过环境变量提供：

~~~text
LCXQY_MYSQL_HOST
LCXQY_MYSQL_PORT
LCXQY_MYSQL_DATABASE
LCXQY_MYSQL_USER
LCXQY_MYSQL_PASSWORD
LCXQY_QQBOT_SECRET
~~~

不要把 QQ 空间 Cookie、论坛 token、MySQL 密码、Redis 密码或 Bot secret 写入仓库。

## 11. QQ 空间（二期）

QQ 空间发布不是 MVP。原因是授权、Cookie、风控和接口稳定性都不适合放进主链路。

实现时抽象为 QzonePublisher：

~~~text
publish(forumPost) -> success | retryable_failure | permanent_failure
~~~

触发条件必须是论坛状态已经为 publish。发布失败只写日志和任务状态，不回滚论坛帖子，也不阻塞群同步。不要逆向或绕过官方安全机制。

## 12. 部署和测试

推荐目录：

~~~text
/opt/lcxqy-qqbot/
├─ plugin/
├─ .env
└─ logs/
~~~

systemd 运行时使用独立用户、EnvironmentFile、自动重启和日志轮转。不要让插件直接监听公网端口。

最小测试：

| 场景 | 期望 |
|---|---|
| 未绑定发帖 | 提示先绑定 |
| challenge 过期/重复 | 拒绝并要求重新绑定 |
| 上传失败 | 不创建帖子 |
| 接口返回 code=0 | 不写 submitted/published |
| 待审核帖子 | 不发群 |
| 审核通过 | 每群只发一次 |
| 重启后重扫 | 不重复发 |
| 论坛删除 | 后续不再发 |
| token 失效 | 标记绑定失效并提示重新绑定 |
| QQ 空间失败 | 不影响论坛和群同步 |

## 13. 开发顺序

1. 确认当前 AstrBot 版本和适配器。
2. 先实现只读同步和投递去重。
3. 实现管理员预绑定，再实现一次性 challenge。
4. 实现私信纯文本发帖。
5. 增加图片上传和审核轮询。
6. 增加群配置、限流和管理命令。
7. 最后评估 QQ 空间发布。

官方参考：

- AstrBot 插件开发：https://docs-v3.astrbot.app/dev/star/plugin.html
- AstrBot 插件页面：https://docs.astrbot.app/en/dev/star/guides/plugin-pages.html
- QQ 空间产品说明：https://www.tencent.com/zh-cn/products/qzone/
