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
| 旧后端 | 127.0.0.1:8081 | 保留支付、验证码、上传、聊天等未迁移能力 |
| PHP admin | admin.lcxqy.cn | 原管理后台，不重建 |
| MySQL | lcxqy | 新旧后端共享业务数据 |
| Redis | 旧配置指定 | 共享登录态、限额和缓存 |

新后端只监听 loopback，不应把 18082 直接暴露到公网。

## 2. 受控一键发布

日常生产发布优先使用仓库根目录的 `deploy/publish-to-server.ps1`。它只发布当前干净工作区对应的已提交 commit，先执行测试或校验，再生成发布包 SHA-256，通过 SSH 上传，最后调用服务器上的固定入口 `/usr/local/sbin/lcxqy-deploy`。详细的会话划分和可复制提示词见 [CODEX_WORKFLOW.md](CODEX_WORKFLOW.md)。

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

仓库中的 cutover-*.sh 和 promote-*.sh 已包含特定路由的备份、语法检查与验收逻辑。使用前必须确认脚本目标与本次范围一致。

### 9.2 当前边界

新后端已覆盖的主要范围：

- 公共内容、评论、分类和广告读取。
- 内容详情，以及普通帖子/视频的新增和更新。
- 动态读取、发布、编辑、审核、锁定、删除、点赞、关注、浏览量和话题。
- 用户注册、资料维护和部分管理操作。
- 积分、签到、奖励、提现、商城、VIP、广告购买及广告奖励。

仍保留旧端：

- 邮件/短信验证码发送和第三方社交登录绑定。
- upload/full 文件上传。
- 聊天和群聊。
- 官方充值、支付创建、卡密及支付回调的原始实现。
- 插件接口和未知插件内容。
- API 手册中标为“旧端”或“待抓包确认”的其他路径。

部分新接口会主动委托旧端处理不支持的付费、草稿、商品关联或未知类型请求。不能只看 URL 判断最终写入者。

### 9.3 路由识别

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

## 11. 回滚

### 11.1 JAR

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
