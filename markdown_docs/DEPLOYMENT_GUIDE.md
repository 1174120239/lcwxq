# 生产部署与回滚手册

> 更新日期：2026-08-04
>
> 适用服务：starfree-legacy.service、starfree-replacement.service 和 PHP admin

本文只记录当前可执行的部署、验证和回滚流程。接口参数见 [API_USAGE_GUIDE.md](API_USAGE_GUIDE.md)，系统边界见 [AI_PROJECT_BRIEF.md](AI_PROJECT_BRIEF.md)。

## 1. 部署拓扑

生产环境采用新旧后端并行：

| 组件 | 地址/路径 | 作用 |
|---|---|---|
| Nginx | api.lcxqy.cn | 按精确 location 将请求分流到新旧后端 |
| 新后端 | 127.0.0.1:18082 | Spring Boot 重建服务 |
| 旧后端 | 127.0.0.1:8081 | 保留支付、短信验证码、上传、聊天等未迁移能力 |
| PHP admin | admin.lcxqy.cn | 原管理后台，不重建 |
| MySQL | lcxqy | 新旧后端共享业务数据 |
| Redis | 旧配置指定 | 共享登录态、限额和缓存 |

新后端只监听 loopback，不应把 18082 直接暴露到公网。

## 2. 受控一键发布

日常生产发布优先使用仓库根目录的 `deploy/publish-to-server.ps1`。它只发布当前干净工作区对应的已提交 commit，先执行测试或校验，再生成发布包 SHA-256，通过 SSH 上传，最后调用服务器上的固定入口 `/usr/local/sbin/lcxqy-deploy`。详细的会话划分和可复制提示词见 [CODEX_WORKFLOW.md](CODEX_WORKFLOW.md)。

服务器入口会在替换前验证压缩包内的 JAR，并在重启后等待最多 60 秒完成服务和 HTTP 健康检查。失败回滚时会先停止对应服务，再恢复备份 JAR，避免运行中的 Java 进程读取到正在被覆盖的文件。

~~~powershell
# 只构建和校验，不连接生产服务器
.\deploy\publish-to-server.ps1 -Component replacement-backend -DryRun

# 明确确认后发布一个组件
.\deploy\publish-to-server.ps1 -Component replacement-backend -ConfirmProduction
~~~

组件可选 `replacement-backend`、`legacy-api`、`admin` 和 `all`。`all` 按新后端、旧 API、PHP admin 顺序处理；后续组件失败时，服务器入口会恢复本次已经更新的前置组件。

服务器目录固定为：

| 路径 | 用途 |
|---|---|
| `/srv/lcxqy/releases/<commit>/<component>/` | 发布包、manifest 和 SHA-256 |
| `/srv/lcxqy/backups/<time>-<commit>-<component>/` | 发布前组件备份 |
| `/srv/lcxqy/current/<component>` | 当前组件对应发布记录的符号链接 |
| `/usr/local/sbin/lcxqy-deploy` | 唯一通用部署入口 |
| `/usr/local/sbin/lcxqy-rollback` | 受控组件回滚入口 |

首次初始化可由有 sudo 权限的部署账号执行：

~~~powershell
.\deploy\publish-to-server.ps1 -Component replacement-backend -DryRun
.\deploy\publish-to-server.ps1 -Component replacement-backend -ConfirmProduction -BootstrapServer
~~~

`-BootstrapServer` 只安装仓库内两个固定入口，不修改 SSH、防火墙、Nginx 或数据库。正式自动化应使用专用 `deploy` 用户、Ed25519 私钥和严格的 known_hosts；sudo 只放行上述两个入口。不要在脚本、GitHub Secrets 或仓库中保存 root 密码。

专用用户初始化脚本为 `deploy/server/bootstrap-deploy-user.sh`，它只接受 Ed25519 公钥文件，并在 `/etc/sudoers.d/lcxqy-deploy` 中写入受限规则。必须先在第二个终端验证新账号可登录，再考虑关闭旧的 root 密码登录；不要在同一个操作里同时改账号、防火墙和服务部署。

通用发布入口不执行数据库迁移，也不修改 Nginx。传入 `-RunMigrations` 会在任何生产变更前中止；迁移继续按本手册的对应章节单独审查、备份和执行。Nginx 精确路由切换继续使用 `backend/deploy/production/` 下的专用脚本。

## 3. 从 GitHub 全新部署

本节用于新服务器或需要从仓库重建的环境。生产服务器已有可用服务时，先做备份和维护窗口，不要直接覆盖正在运行的 JAR。

### 3.1 仓库内的可部署资产

| 资产 | 仓库路径 | 服务器目标 |
|---|---|---|
| 旧 API JAR | `backend/legacy-api/dist/StarFreeApi.jar` | `/opt/StarFreeApi.jar` |
| 旧 API 配置模板 | `backend/legacy-api/config/application.example.properties` | `/opt/application.properties`，填写后使用 |
| 旧 API systemd | `backend/legacy-api/deploy/starfree-legacy.service` | `/etc/systemd/system/` |
| PHP admin 源码 | `admin/starfree-admin/source/` | `/www/wwwroot/admin.lcxqy.cn/` |
| admin 配置模板 | `admin/starfree-admin/source/Config_DB.example.php` | `/www/wwwroot/admin.lcxqy.cn/Config_DB.php`，填写后使用 |
| admin 安装脚本 | `admin/starfree-admin/deploy/install.sh` | 由仓库直接执行 |

旧 API 没有可用源码；JAR 是从生产服务器导出的闭源发布物。它可以运行和部署，但不能用 Maven 编译，也不能通过 GitHub 的源码审查推断内部实现。

### 3.2 获取仓库和检查 JAR

~~~bash
git clone https://github.com/1174120239/lcwxq.git
cd lcwxq
sha256sum -c backend/legacy-api/SHA256SUMS
php -v
java -version
~~

校验必须显示 `OK`，且 JAR 哈希为：

~~~text
c2daa75c2c6a2968bea2d72783fc4a6844c666306daeacdf936e31dc9cb89c26
~~~

### 3.3 配置旧 API

~~~bash
install -d -m 0755 /opt /var/log/lcxqy
install -m 0600 backend/legacy-api/config/application.example.properties /opt/application.properties
vi /opt/application.properties
~~~

至少填写 `spring.datasource.username`、`spring.datasource.password`、`spring.redis.password`（无密码时保持为空）、邮件账号密码和 `webinfo.key`。数据库名默认 `lcxqy`，旧 API 和新后端的表前缀、Redis 前缀必须与现有生产配置一致；支付上线前还要确认 `gateway_url` 是当前环境实际使用的网关，不能直接照抄模板。

不要把填写后的文件复制回 Git 工作区，也不要把真实值写进 shell 历史。仓库只跟踪 `application.example.properties`，服务器上的 `/opt/application.properties` 是独立运行时文件，不属于仓库。

### 3.4 安装并启动旧 API

先确认是否有旧的手工启动进程：

~~~bash
pgrep -af 'java .*StarFreeApi\.jar' || true
ss -lntp | grep ':8081' || true
~~~

如果已有手工启动进程，先记录 PID、工作目录和当前 JAR 哈希，在维护窗口内停止这个明确的旧 API 进程，不能按模糊关键字杀 Java 进程。确认 `8081` 释放后执行：

~~~bash
sudo bash backend/legacy-api/deploy/install.sh
systemctl status starfree-legacy.service --no-pager
curl -fsS http://127.0.0.1:8081/
~~~

安装脚本会再次校验 JAR、备份旧 JAR、安装 systemd unit、拒绝 `CHANGE_ME` 配置，并确保旧 API 只监听 `127.0.0.1:8081`。首次运行若配置文件不存在，会只创建模板并退出，填完配置后再次运行即可。

### 3.5 部署 PHP admin

先填写模板：

~~~bash
install -m 0600 admin/starfree-admin/source/Config_DB.example.php /tmp/Config_DB.php
vi /tmp/Config_DB.php
install -d -m 0755 /www/wwwroot/admin.lcxqy.cn
install -m 0600 /tmp/Config_DB.php /www/wwwroot/admin.lcxqy.cn/Config_DB.php
sudo TARGET_DIR=/www/wwwroot/admin.lcxqy.cn bash admin/starfree-admin/deploy/install.sh
~~~

如果目标站点已经有 `Config_DB.php`，安装脚本会先备份它；脚本不会把仓库中的配置模板当作生产配置使用。必须确认：

- `$api_site` 指向旧 API 的实际内部地址并以 `/` 结尾。
- `$api_key` 与旧 API 的 `webinfo.key` 完全相同。
- `$db_prefix`、`$redis_prefix` 与现有数据库和 Redis 数据一致。
- `$ADMIN_PATH` 与后台访问路径一致。

生产站点的 `.user.ini` 可能带 immutable 属性。admin 安装脚本会保留目标目录中已有的
`.user.ini`，在复制文件前用 `lsattr` 检查属性；写入 PHP 会话安全配置时会临时执行
`chattr -i`，并通过退出清理逻辑在成功或失败时恢复 `chattr +i`。属性无法检查、解锁或
恢复时发布会明确失败，必须先人工确认文件属性，不得跳过检查。若失败发布导致 `Config_DB.php` 缺失，脚本会从
`/srv/lcxqy/backups/*-admin/admin.tar.gz` 中最近一个包含该配置的受控备份恢复运行时文件，
再覆盖安装仓库源码。

### 3.6 配置 Nginx 和 PHP-FPM

参考 `admin/starfree-admin/deploy/nginx-admin.conf` 创建站点配置，替换域名、证书路径和 PHP-FPM 版本。aaPanel 当前环境可以继续使用 `include enable-php-72.conf;`，不要把生产证书提交到仓库。

~~~bash
nginx -t
nginx -s reload
curl -skI https://admin.example.com/
~~~

返回 `301` 或后台登录页都说明 Web 层已经接通；登录页显示后再检查 PHP-FPM、`Config_DB.php` 和旧 API，不要只依据 HTTP 200 判断后台可用。

### 3.7 部署新后端和 API 入口

旧 API 必须先可用，新后端再按 [第 6 节](#6-jar-部署) 部署。新后端使用 `18082`，旧 API 使用 `8081`，Nginx 只把已经验证的精确路径切到 `18082`，其余请求保留到 `8081`。

如果新后端的启动脚本仍从 `/opt/application.properties` 读取兼容配置，必须保留该文件，不要把它改成只存在于 Git 工作区的配置。部署完成后分别验证两个本地端口，再执行 Nginx 切流。

### 3.8 首次验收顺序

1. `systemctl is-active starfree-legacy.service`。
2. `curl http://127.0.0.1:8081/`。
3. admin 域名显示登录页。
4. 新后端 `/health` 和 `/health/live`。
5. API 域名匿名读取接口。
6. 一个登录态读取接口。
7. 后台登录、帖子读取和一个可回滚的设置读取。
8. 查看 systemd、PHP-FPM、Nginx 日志，没有持续错误后再放量。

## 4. 服务器文件

~~~text
/opt/starfree-replacement/
├─ starfree-replacement.jar
├─ starfree-replacement.jar.new
├─ start.sh
├─ deploy-jar.sh
├─ verify-*.sh
└─ *.rollback-YYYYMMDD-HHMMSS
~~~

旧 API 和 admin 的发布文件：

~~~text
/opt/
├─ StarFreeApi.jar
├─ application.properties
└─ starfree-legacy-start.sh

/etc/systemd/system/starfree-legacy.service
/www/wwwroot/admin.lcxqy.cn/
└─ Config_DB.php
~~~

关键外部文件：

| 路径 | 用途 |
|---|---|
| /opt/application.properties | 旧后端数据库、Redis 和兼容配置来源 |
| /opt/jdk1.8.0_311/bin/java | 生产 Java 运行时 |
| /etc/systemd/system/starfree-legacy.service | 旧闭源 API 的 systemd 服务 |
| /www/wwwroot/admin.lcxqy.cn | PHP admin 站点目录 |
| /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf | 新旧后端精确路由 |

真实服务器地址和 SSH 凭据只保存在本机 markdown_docs/private/SERVER_ACCESS.local.md，不得提交 Git。

## 5. 发布前检查

1. 确认本地 main 已提交，工作区没有遗漏修改。
2. 运行后端全量测试。
3. 核对本次是否包含数据库迁移。
4. 阅读将要使用的部署和切流脚本。
5. 在服务器确认旧、新服务和数据库均正常。
6. 备份当前 JAR、Nginx include 和涉及的数据库表。

本地检查：

~~~powershell
git status --short --branch
mvn -f backend/starfree-replacement/pom.xml clean test package
Get-FileHash backend/starfree-replacement/target/starfree-replacement-0.1.0-SNAPSHOT.jar -Algorithm SHA256
~~~

生产只接受已经通过测试并记录 SHA-256 的 JAR。

## 6. 数据库迁移

| 顺序 | 文件 | 作用 |
|---|---|---|
| 001 | 001_economy_operation_journal.sql | 经济操作幂等日志 |
| 002 | 002_space_views.sql | 动态浏览量字段 |
| 003 | 003_space_topics.sql | 动态话题、关注和关系表 |
| 004 | 004_campus_identity.sql | 校区/入学年份选项和用户引用列 |
| 005 | 005_ng_music_anonymous.sql | 匿名动态映射与配置表（ng_music 插件功能原生实现） |
| 006 | 006_qqbot_dynamic_ai.sql | QQBot 绑定、群同步、配置和幂等日志表 |
| 007 | 007_campus_qa.sql | 校园问答的问题、回答、点赞和评论表 |
| 008 | 008_simple_invitation.sql | 轻量邀请配置、邀请码和奖励记录表 |
| 009 | 009_space_reports.sql | 动态举报、审核状态和审核审计表 |
| 010 | 010_admin_password_hash.sql | 将 `starfree_admin_login.pw` 扩为 255 位，支持 PHP `password_hash()` |

规则：

- 不要编辑生产快照代替迁移。
- 迁移前备份受影响表并记录校验值。
- 先迁移数据库，再部署读取新结构的 JAR。
- 已执行的迁移先查 information_schema，不要凭感觉重复运行。
- MyISAM 主表不支持真正事务，跨表修改必须保留补偿和回滚方案。

004 会新增 `starfree_identity_options`，并修改 `starfree_users`。执行前必须定向备份
`starfree_users`，确认 001-003 已完成，再执行一次 004；执行后检查两个新增索引、四个种子选项
以及用户表新增列。读取用户资料和处理注册的新 JAR 依赖这些结构，因此 **不能在未执行 004 时部署该 JAR**。
停用选项不会清空已有用户引用，回滚 JAR 时也不要自动删除新表或新列。

005 只新增两张 InnoDB 表并写入一行默认配置（`fid=0` 表示匿名动态未开放），不修改现有表，
可随时在发布新 JAR 前执行；执行后应确认两张表存在且配置行 id=1。回滚 JAR 时不要删除这两张表。
上线后在管理端“匿名动态配置”里填写匿名账号 UID 与审核开关。

006 新增 QQBot 使用的六张 InnoDB 表，并以幂等方式写入默认配置，不修改论坛原有表。执行前先查询
`lcxqy_bot_%` 表是否存在；若生产已运行该功能，只备份这些表并保留现有配置值，不要用默认值覆盖密钥或开关。

007 只新增 `starfree_qa_questions`、`starfree_qa_answers`、`starfree_qa_answer_likes` 和
`starfree_qa_comments` 四张独立 InnoDB 表，不修改现有用户、帖子或动态表。执行前查询这四张表是否已存在；
若存在任意一张则先定向备份全部已存在的 Q&A 表，再执行幂等建表语句。执行后核对四张表、主键和索引，
再部署依赖这些表的 JAR。回滚 JAR 时保留 Q&A 表，避免丢失上线后产生的问答数据。

008 新增轻量邀请配置、邀请码和奖励记录三张 InnoDB 表。执行前查询 `lcxqy_invitation_%` 表是否已存在，
如已存在则先定向备份并保留当前下载地址和奖励配置。执行后核对配置主键、邀请码唯一键和受邀用户唯一键，
再部署邀请服务 JAR；回滚 JAR 时保留邀请记录和已发放奖励的审计数据。

009 只新增 `starfree_space_reports` 一张独立 InnoDB 表，不修改动态主表。执行前查询该表是否已存在；
如已存在则先定向备份并核对字段、唯一键 `uk_space_reporter` 和三个查询索引。执行后先验证表结构，
再部署举报服务 JAR，最后通过 `promote-space-report-routes.sh` 切换三个精确接口。回滚 JAR 或 Nginx
时保留举报表及其审核记录。

010 修改 PHP admin 登录表的密码列长度。它必须在独立、明确授权的生产迁移会话中执行，先备份
`starfree_admin_login` 并确认目标列当前定义；普通开发或组件发布不得顺带执行。未执行 010 时，
后台仍可验证原 MD5 密码，但会跳过自动升级，避免将 `password_hash()` 结果截断后锁死管理员。
只有用户明确要求迁移并通过带 `-RunMigrations` 的受控发布入口时才能执行。

001 可使用：

~~~bash
cd /opt/starfree-replacement
./apply-economy-migration.sh
~~~

脚本从 /opt/application.properties 读取凭据，并校验日志表和唯一键。其他迁移应先阅读 SQL，再通过受控的 MySQL 会话执行。

## 7. JAR 部署

上传候选文件：

~~~bash
scp backend/starfree-replacement/target/starfree-replacement-0.1.0-SNAPSHOT.jar root@<SERVER_IP>:/opt/starfree-replacement/starfree-replacement.jar.new
~~~

在服务器执行：

~~~bash
cd /opt/starfree-replacement
sha256sum starfree-replacement.jar.new
./deploy-jar.sh <EXPECTED_SHA256>
~~~

deploy-jar.sh 会：

1. 校验 starfree-replacement.jar.new 的 SHA-256。
2. 备份当前 JAR。
3. 替换 active JAR 并重启 systemd 服务。
4. 最多等待 40 秒检查 /health。
5. 健康检查失败时自动恢复旧 JAR。
6. 输出新 JAR 哈希、PID 和回滚文件路径。

不要绕过这个脚本直接覆盖正在运行的 JAR。

## 8. 服务检查

~~~bash
systemctl status starfree-replacement.service --no-pager
systemctl is-active starfree-replacement.service
curl -fsS http://127.0.0.1:18082/health
curl -fsS http://127.0.0.1:18082/health/live
journalctl -u starfree-replacement.service -n 100 --no-pager
~~~

健康接口成功只证明进程和基础依赖可用，不等于业务接口已验证。

## 9. Nginx 切流

### 9.1 原则

- 只添加精确 location。
- 不修改旧 API 的兜底 location。
- 每组接口独立切流、独立验证、独立回滚。
- 先验证本机 18082，再切公网。
- 所有修改先备份 include，执行 nginx -t 后才能 reload。

仓库中的 cutover-*.sh 和 promote-*.sh 已包含特定路由的备份、语法检查与验收逻辑。使用前必须确认脚本目标与本次范围一致。校区和入学年份的三个管理/注册接口统一使用 `promote-campus-identity-routes.sh`；用户资料读取的 `userStatus/userInfo` 使用 `promote-user-profile-routes.sh`；邮箱验证码的 `RegSendCode/SendCode` 使用 `promote-email-verification-routes.sh`；消息中心的 `inbox/unreadNum/setRead` 使用 `promote-inbox-routes.sh`（新端负责渲染动态评论 `spaceComment` 通知并携带原动态状态）；匿名动态的 `config/post/owner/admin/config` 使用 `promote-anonymous-routes.sh`；轻量邀请的 `SFreeInvitation/config` 和 `SFreeInvitation/me` 使用 `promote-invitation-routes.sh`；NapCat/AstrBot 动态助手的 14 个 `SFreeBot/*` 接口使用 `promote-qqbot-routes.sh`；校园问答的 14 个 `SFreeQa/*` 接口使用 `promote-qa-routes.sh`；动态举报的 `reportAdd/reportList/reportReview` 使用 `promote-space-report-routes.sh`。个人电脑上的 NapCat 连接服务器 AstrBot 时，使用 `promote-astrbot-onebot-route.sh` 单独开放带 Token 的 `/onebot/v11/ws` 精确 WSS 路由；6185 管理页和 6199 原始端口均不得直接暴露公网。脚本都先备份 include、执行 `nginx -t`，并在 reload 后验证对应响应。

### 9.2 安全版本切流

渗透测试修复不能只更新 JAR。必须在独立发布会话按以下顺序执行：

1. 构建、发布并验证 replacement JAR，确认 Redis 登录态已启用且 `/health` 正常。
2. 发布 PHP admin，验证登录页、严格会话 Cookie、登录后 session id 轮换及 `mp3.php` 输出转义。
3. 迁移 010 仅在用户另行明确授权时执行；它不是前两项发布的默认步骤。
4. 运行 `backend/deploy/production/promote-security-routes.sh`，由脚本备份 Nginx include、检查
   安全前置路由、增量添加精确 location、执行 `nginx -t`、reload 和公网验收；失败自动回滚。

安全切流会强制拒绝所有旧格式 token，所有用户需要重新登录。新 token 为 `sf2_` 加 60 位
小写十六进制随机串，总长度 64 字符以兼容共享用户表；Redis TTL 是生产会话有效期权威。客户端优先使用 Bearer Header，历史
`token` 参数只作为兼容通道。切流脚本还必须验证公开资料不含 IP、local、logged 或 clientId。

聊天、上传、社会化绑定、支付创建和卡密等业务实现仍留在 8081，但公网请求先进入 18082，
由新端验证 token，管理接口还会验证 staff/administrator 角色，再受控转发到旧端。支付供应商
回调保持原签名路径，不按用户 token 处理。不得为了保留旧功能而恢复基于 `$arg_token` 的 8081
直连回落。

### 9.3 当前边界

新后端已覆盖的主要范围：

- 公共内容、评论、分类和广告读取。
- 内容详情，以及普通帖子/视频的新增和更新。
- 动态读取、发布、编辑、审核、锁定、删除、点赞、关注、浏览量和话题。
- 站内通知读取、未读数与已读标记（inbox/unreadNum/setRead）。
- 匿名动态：公开配置、匿名发布、归属查询和管理端配置（SFreeAnonymous/*）。
- NapCat/AstrBot 动态助手：绑定、聊天、发动态、签到、资料修改和群同步（SFreeBot/*）。
- 用户注册、资料维护和部分管理操作。
- 轻量邀请：用户邀请码、注册奖励、分享配置和软件下载地址（SFreeInvitation/*）。
- 积分、签到、奖励、提现、商城、VIP、广告购买及广告奖励。

仍保留旧端：

- 短信验证码发送和第三方社交登录绑定；邮箱验证码由新端实现，需单独切流。
- upload/full 文件上传。
- 聊天和群聊。
- 官方充值、支付创建、卡密及支付回调的原始实现。
- 插件接口和未知插件内容（匿名动态已原生实现，不依赖 PHP 插件）。
- API 手册中标为“旧端”或“待抓包确认”的其他路径。

部分新接口会主动委托旧端处理不支持的付费、草稿、商品关联或未知类型请求。不能只看 URL 判断最终写入者。

邮箱验证码上线前必须确认新后端运行环境同时具备：`LEGACY_REDIS_ENABLED=true`、与旧端一致的 Redis 前缀、SMTP 配置，以及 `VERIFICATION_EMAIL_ENABLED=true`。生产 JAR 在没有显式 `SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` 时，会仅从 `/opt/application.properties` 兼容读取同名 `spring.mail.*` 五项，不导入端口、数据库、Redis 或其他旧端设置；生产 `start.sh` 也会执行相同的白名单读取，且都不会输出凭据。QQ 邮箱的 password 必须填写 SMTP 授权码，不是 QQ 登录密码。验证码 SMTP 默认限制为同时 2 个请求、真实尝试至少间隔 1000 毫秒；认证失败会全局退避 300 秒，避免 QQ 的 `535` 登录限频被连续重试放大，可分别通过 `VERIFICATION_EMAIL_MAX_CONCURRENT`、`VERIFICATION_EMAIL_MINIMUM_ATTEMPT_INTERVAL_MILLIS` 和 `VERIFICATION_EMAIL_AUTHENTICATION_BACKOFF_SECONDS` 调整。`NOTIFICATION_EMAIL_ENABLED` 默认保持 true，动态评论邮件提醒继续使用同一 SMTP，且失败不会影响评论落库。先在本机 `18082` 使用测试邮箱验证成功和失败清理，再运行 `backend/deploy/production/promote-email-verification-routes.sh`；脚本只切 `RegSendCode` 和 `SendCode`，不修改短信、登录或其他账号接口。

### 9.4 路由识别

~~~bash
curl -skI 'https://api.lcxqy.cn/SFreeSpace/spaceList?searchParams=%7B%7D&limit=1&page=1'
~~~

重点关注：

- X-Starfree-Backend：Nginx 选择的新旧后端。
- X-Starfree-Delegate：新后端是否又委托旧端。

HTTP 200 不代表业务成功，还要检查响应 JSON 的 code 和 msg。

## 10. 验收

每次发布至少验证：

1. /health 和 /health/live。
2. 一个匿名读取接口。
3. 一个带 token 的读取接口。
4. 本次修改的正常路径。
5. 本次修改的权限拒绝路径。
6. 本次修改的重复请求或幂等路径。
7. Nginx 响应头。
8. systemd 日志没有持续异常。

写接口优先使用可删除的测试数据。测试完成后只清理本次创建的记录，不操作真实用户数据。

| 类别 | 验证脚本 |
|---|---|
| Redis 登录态 | verify-redis-session.sh |
| 动态 | verify-space-*.sh |
| 内容 | verify-contents-*.sh |
| 经济 | verify-economy.sh |
| 广告奖励 | verify-ads-reward.sh |
| 注册与账号 | verify-user-registration.sh、verify-account-maintenance.sh |
| 匿名动态 | verify-anonymous.sh |

## 11. 回滚

### 11.1 JAR

优先使用 `/usr/local/sbin/lcxqy-rollback`。该入口恢复 JAR 并重启服务后会最多等待 60 秒，同时检查 systemd active 状态和 HTTP 健康接口；不要用一次立即执行的 `curl` 判断回滚失败。

~~~bash
cp -p /opt/starfree-replacement/starfree-replacement.jar.rollback-<TIMESTAMP> /opt/starfree-replacement/starfree-replacement.jar
systemctl restart starfree-replacement.service
curl -fsS http://127.0.0.1:18082/health
~~~

### 11.2 Nginx

~~~bash
cp -p <NGINX_ROLLBACK_FILE> /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf
nginx -t
nginx -s reload
~~~

### 11.3 数据库

数据库回滚必须单独评估。不要为了回滚 JAR 就自动覆盖整库或整表，否则会丢失发布后的真实数据。优先让旧代码兼容保留的新列/新表；只有迁移本身导致问题时，才使用发布前的定向备份恢复。

## 12. 禁止事项

- 不把数据库、Redis、SSH、支付或 Bot 密钥写进仓库。
- 不把 18082 或 MySQL 3306 暴露到公网。
- 不在未备份时修改 Nginx 或生产表。
- 不用生产账号做破坏性测试。
- 不把插件接口误认为已重建。
- 不同时修改 JAR、数据库和大范围路由后再一次性验证。
- 不删除旧 JAR、Nginx 回滚文件或数据库备份，直到新版本稳定。

## 13. 上线前安全加固

- 当前服务器 SSH 日志曾报告大量失败登录尝试。部署完成后立即更换 root 密码，创建受限运维用户，改用 SSH 公钥登录，并限制 SSH 来源 IP；不要继续长期使用仓库外泄过的密码。
- 关闭 root 密码登录前，先用第二个终端验证公钥用户可以登录，避免把自己锁在服务器外。
- 只对外开放 `80/443` 和确有需要的 SSH 端口；`8081`、`18082`、MySQL `3306` 和 Redis `6379` 只允许本机或受控内网访问。
- PHP 7.2 已停止上游支持。当前 admin 依赖它才能保持兼容，先在独立环境升级到受支持 PHP 版本并完成后台、上传、邮件和支付设置验证，再切生产。
- 不执行原服务器上的 `starfreeapi.sh` 自动更新流程；它会从第三方地址下载 JAR、删除文件并使用强制杀进程。仓库安装脚本只使用已校验的 JAR 和本地配置。
- GitHub 仓库应保持私有。JAR 虽未超过单文件限制，但包含闭源发布物，不应公开镜像或把生产配置、数据库快照和证书与它放在同一仓库。
