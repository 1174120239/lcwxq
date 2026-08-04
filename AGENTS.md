# Codex 项目规则

本文件适用于仓库根目录及其所有子目录。每个新 Codex 会话开始时必须先阅读本文件，以及 `markdown_docs/README.md` 和与当前任务相关的项目手册。

## 项目范围

- 前端是 uni-app，入口主要在 `pages/`、`components/`、`utils/`。
- `backend/starfree-replacement/` 是可编译的 Spring Boot 重建后端。
- `backend/legacy-api/` 是没有源码的旧 Java API 发布包，只能按 JAR 处理。
- `admin/starfree-admin/` 是保留的 PHP 管理后台，不因前端需求重建。
- `backend/database/migrations/` 是共享 MySQL 的迁移脚本。
- `markdown_docs/` 是长期有效的项目文档；`markdown_docs/private/` 只存本机密钥资料，禁止提交。

## 会话类型

开始任务时先判断属于哪一种。

### 日常开发

1. 默认只在本地工作区操作，不连接生产服务器。
2. 先运行 `./workflow.cmd status`，再读取相关接口、数据表、部署和风险文档。
3. 新功能从同步且干净的 `main` 运行 `./workflow.cmd start <feature-name>`，创建 `codex/<feature-name>` 分支；不要直接推送 `main`。
4. 修改必须保持现有前端协议兼容，除非用户明确要求改变协议。
5. 后端写操作必须检查身份、所有权、状态、重复提交、审计/积分副作用和缓存失效。
6. 新增或修复共享行为时补充针对性测试；至少运行受影响模块测试。
7. 提交或推送前运行受影响范围的 `./workflow.cmd check <scope>`；准备合并前必须运行 `./workflow.cmd check all`。
8. 不要把调试输出、生产配置、数据库快照、Cookie、token、密码或密钥写入仓库。

### 代码审查

审查只读，不自动修改。先报告按严重程度排序的问题，重点检查权限绕过、数据删除、支付/积分重复记账、审核状态、Redis 登录态、缓存一致性、SQL 注入和缺失测试。报告必须带文件和行号；没有问题时明确说明剩余风险。

### 发布与生产维护

发布必须是独立会话，并且只发布用户指定的已提交 commit 和组件。优先使用：

```powershell
.\workflow.cmd publish replacement-backend
.\workflow.cmd publish replacement-backend -ConfirmProduction
```

第一条只做本地构建演练；第二条才连接生产。生产只能发布当前 `origin/main` 的精确提交，不能从功能分支上线。发布前备份，发布后健康检查；失败时回滚。通用发布命令永不执行数据库迁移，迁移必须是用户单独授权的维护任务。发布结果必须汇报 commit、组件、SHA-256、服务状态、HTTP 健康状态、备份路径和迁移是否执行。

生产操作包括以下高风险动作，不能由普通开发会话自行执行：

- 写入、删除或修改生产数据库。
- 执行数据库迁移。
- 修改或重载 Nginx。
- 重启 systemd 服务。
- 删除生产文件。
- 修改 SSH 用户、密码、防火墙或 sudo 权限。

用户明确授权某项生产动作后，仍然必须先做只读检查、创建备份、执行最小范围变更并验证；不要把“服务器随便碰”理解为可以跳过这些步骤。

## 发布边界

组件参数只有：`replacement-backend`、`legacy-api`、`admin`、`all`。一次发布只处理指定组件。

- `replacement-backend`：构建并部署新后端 JAR，目标服务为 `starfree-replacement.service`，监听 `127.0.0.1:18082`。
- `legacy-api`：校验并部署旧 API JAR，目标服务为 `starfree-legacy.service`，监听 `127.0.0.1:8081`。
- `admin`：运行 PHP lint 后部署原 PHP 后台；不修改后台业务代码以外的生产配置。
- `all`：按 replacement、legacy、admin 顺序分别发布；任何一步失败都停止后续组件。

支付、验证码、邮件、上传、聊天等仍由旧 API 提供的能力不得因发布新后端而被删除。Nginx 路由切换必须使用已有的精确 location 脚本，并单独备份和验收。

## 工具和编辑规则

- 搜索优先使用 `rg` 或 `rg --files`。
- 手工编辑使用 `apply_patch`；不要用 shell 重定向或脚本覆盖源码。
- 不恢复、覆盖或删除用户已有的无关修改。
- 不使用 `git reset --hard` 或 `git checkout --` 清理工作区。
- Windows 下命令使用 PowerShell；服务器脚本使用 Bash，并执行 `bash -n` 检查。
- 文档统一放在 `markdown_docs/`；新增接口必须同时更新 API 手册、技术手册和测试说明。

## 完成标准

结束前至少完成：

1. 运行 `./workflow.cmd check all`，其中包含测试、lint、脚本语法、`git diff --check`、文档链接和敏感文件检查。
2. 若全量检查因明确的环境限制不能运行，至少运行受影响范围的 `./workflow.cmd check <scope>` 并说明限制。
3. 检查 Git 工作区，确认没有把 `target/`、`unpackage/`、私密配置或临时文件加入版本控制。
4. 明确说明已验证内容、未能验证的内容、剩余风险和下一步命令。

不要只给设计方案：用户请求开发或修复时，应在当前会话完成实现和验证；只有用户明确要求规划、审查或暂停时才不改代码。
