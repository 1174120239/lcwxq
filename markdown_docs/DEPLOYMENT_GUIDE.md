# 生产部署与回滚手册

> 更新日期：2026-08-04
>
> 适用服务：starfree-replacement.service

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

## 2. 服务器文件

~~~text
/opt/starfree-replacement/
├─ starfree-replacement.jar
├─ starfree-replacement.jar.new
├─ start.sh
├─ deploy-jar.sh
├─ verify-*.sh
└─ *.rollback-YYYYMMDD-HHMMSS
~~~

关键外部文件：

| 路径 | 用途 |
|---|---|
| /opt/application.properties | 旧后端数据库、Redis 和兼容配置来源 |
| /opt/jdk1.8.0_311/bin/java | 生产 Java 运行时 |
| /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf | 新旧后端精确路由 |

真实服务器地址和 SSH 凭据只保存在本机 markdown_docs/private/SERVER_ACCESS.local.md，不得提交 Git。

## 3. 发布前检查

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

## 4. 数据库迁移

| 顺序 | 文件 | 作用 |
|---|---|---|
| 001 | 001_economy_operation_journal.sql | 经济操作幂等日志 |
| 002 | 002_space_views.sql | 动态浏览量字段 |
| 003 | 003_space_topics.sql | 动态话题、关注和关系表 |

规则：

- 不要编辑生产快照代替迁移。
- 迁移前备份受影响表并记录校验值。
- 先迁移数据库，再部署读取新结构的 JAR。
- 已执行的迁移先查 information_schema，不要凭感觉重复运行。
- MyISAM 主表不支持真正事务，跨表修改必须保留补偿和回滚方案。

001 可使用：

~~~bash
cd /opt/starfree-replacement
./apply-economy-migration.sh
~~~

脚本从 /opt/application.properties 读取凭据，并校验日志表和唯一键。其他迁移应先阅读 SQL，再通过受控的 MySQL 会话执行。

## 5. JAR 部署

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

## 6. 服务检查

~~~bash
systemctl status starfree-replacement.service --no-pager
systemctl is-active starfree-replacement.service
curl -fsS http://127.0.0.1:18082/health
curl -fsS http://127.0.0.1:18082/health/live
journalctl -u starfree-replacement.service -n 100 --no-pager
~~~

健康接口成功只证明进程和基础依赖可用，不等于业务接口已验证。

## 7. Nginx 切流

### 7.1 原则

- 只添加精确 location。
- 不修改旧 API 的兜底 location。
- 每组接口独立切流、独立验证、独立回滚。
- 先验证本机 18082，再切公网。
- 所有修改先备份 include，执行 nginx -t 后才能 reload。

仓库中的 cutover-*.sh 和 promote-*.sh 已包含特定路由的备份、语法检查与验收逻辑。使用前必须确认脚本目标与本次范围一致。

### 7.2 当前边界

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

### 7.3 路由识别

~~~bash
curl -skI 'https://api.lcxqy.cn/SFreeSpace/spaceList?searchParams=%7B%7D&limit=1&page=1'
~~~

重点关注：

- X-Starfree-Backend：Nginx 选择的新旧后端。
- X-Starfree-Delegate：新后端是否又委托旧端。

HTTP 200 不代表业务成功，还要检查响应 JSON 的 code 和 msg。

## 8. 验收

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

## 9. 回滚

### 9.1 JAR

~~~bash
cp -p /opt/starfree-replacement/starfree-replacement.jar.rollback-<TIMESTAMP> /opt/starfree-replacement/starfree-replacement.jar
systemctl restart starfree-replacement.service
curl -fsS http://127.0.0.1:18082/health
~~~

### 9.2 Nginx

~~~bash
cp -p <NGINX_ROLLBACK_FILE> /www/server/panel/vhost/nginx/extension/api.lcxqy.cn/starfree-replacement-public.conf
nginx -t
nginx -s reload
~~~

### 9.3 数据库

数据库回滚必须单独评估。不要为了回滚 JAR 就自动覆盖整库或整表，否则会丢失发布后的真实数据。优先让旧代码兼容保留的新列/新表；只有迁移本身导致问题时，才使用发布前的定向备份恢复。

## 10. 禁止事项

- 不把数据库、Redis、SSH、支付或 Bot 密钥写进仓库。
- 不把 18082 或 MySQL 3306 暴露到公网。
- 不在未备份时修改 Nginx 或生产表。
- 不用生产账号做破坏性测试。
- 不把插件接口误认为已重建。
- 不同时修改 JAR、数据库和大范围路由后再一次性验证。
- 不删除旧 JAR、Nginx 回滚文件或数据库备份，直到新版本稳定。
