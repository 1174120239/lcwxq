# Codex 工作流

本文是本项目的操作入口。根目录 `AGENTS.md` 是 Codex 的强制规则；本文负责告诉维护者如何选择会话、如何发布和如何让新会话快速进入上下文。

## 会话选择

建议把工作拆成三类独立任务：

| 任务 | 会话权限 | 入口 | 结果 |
|---|---|---|---|
| 开发/修 bug | 本地文件 | `AGENTS.md` + 相关手册 | 分支、代码、测试 |
| 代码审查 | 只读 | `AGENTS.md` + `API_USAGE_GUIDE.md` | 按严重程度排列的发现 |
| 发布/回滚 | 服务器 + GitHub | 本文发布章节 | 指定 commit 的部署和验收 |

不要在一个会话里同时做功能开发和生产发布。开发完成后提交 commit，再新建发布会话执行发布命令。

## 最短日常流程

仓库根目录的 `workflow.cmd` 是 Windows 和 Codex 会话的统一入口。常规改动按以下顺序进行：

```powershell
# 1. 首次或环境变化后检查工具和本机部署配置
.\workflow.cmd doctor

# 2. 在干净、已同步的 main 上创建功能分支
.\workflow.cmd start topic-feature

# 3. 开发中按范围快速检查
.\workflow.cmd check backend
.\workflow.cmd check admin
.\workflow.cmd check scripts
.\workflow.cmd check docs

# 4. 提交、推送或合并前执行完整验收
.\workflow.cmd check all
.\workflow.cmd status
```

然后提交并推送 `codex/<name>`，在 GitHub 创建 Pull Request，等待 CI 通过后合并到 `main`。生产发布只接受当前 `origin/main` 的精确提交，因此功能分支即使已推送也只能做发布演练，不能上线。

命令职责如下：

| 命令 | 是否连接生产 | 用途 |
|---|---:|---|
| `doctor` | 否 | 检查 Git、Java/Maven、PHP、Bash、SSH 和持久化部署配置 |
| `start <name>` | 否 | 从同步的 `main` 创建 `codex/<name>` |
| `check <scope>` | 否 | 执行 `backend`、`admin`、`scripts`、`docs` 或 `all` 检查 |
| `status` | 否 | 查看当前分支、工作区、HEAD 和 `origin/main` |
| `status -Remote` | 是，只读 | 核对三个生产组件的当前健康状态 |
| `publish <component>` | 否 | 构建发布包并输出 commit、组件和 SHA-256 |
| `publish <component> -ConfirmProduction` | 是，写入 | 仅从干净且同步的 `main` 发布指定组件 |
| `deploy` | 否 | `replacement-backend` 的默认发布演练快捷方式 |
| `deploy -ConfirmProduction` | 是，写入 | 发布当前 `origin/main` 的 `replacement-backend` |
| `verify <component>` | 是，只读 | 调用服务器固定入口执行发布后验收 |

`check all` 会运行 Spring Boot 测试、PHP lint、PowerShell/Bash 语法检查、Markdown 链接检查、`git diff --check` 和敏感文件名检查。前端依赖 HBuilderX，本仓库没有可在命令行完整复现的 uni-app 构建，因此改动前端后还必须在 HBuilderX 中手工运行对应页面。

## 新开发会话提示词

把下面内容作为新会话第一条消息，并替换尖括号内容：

```text
你在 lcwxq 项目中工作。先阅读根目录 AGENTS.md、markdown_docs/README.md，以及与本任务相关的 API_USAGE_GUIDE.md、AI_PROJECT_BRIEF.md。

任务：<清楚描述一个功能或 bug>
范围：<前端 / replacement 后端 / admin / 文档>
约束：保持旧 API 协议兼容；不要连接生产服务器；不要修改无关文件。
完成标准：实现代码、补充针对性测试、更新相关 Markdown；检查通过后提交并推送功能分支，但不要合并 `main`、连接生产或执行数据库迁移，并汇报测试命令和未验证风险。
开始前先检查 git 状态，不要覆盖工作区已有修改。
```

## 新审查会话提示词

```text
这是一次只读代码审查。先阅读 AGENTS.md 和相关项目文档，不要修改文件、不要连接生产服务器。
请检查：权限和所有权、审核状态、重复请求、积分/支付幂等性、Redis 登录态、缓存失效、SQL 注入、敏感信息和缺失测试。
先按 P0/P1/P2/P3 报告具体问题，附绝对文件路径和行号；没有问题时说明剩余测试缺口和风险。
```

## 新发布会话提示词

```text
这是一次生产发布会话。先阅读 AGENTS.md、CODEX_WORKFLOW.md 和 DEPLOYMENT_GUIDE.md。
默认发布已经合并到 `main` 的 replacement-backend；先运行 `workflow.cmd deploy` dry-run。
只有我明确确认后，才运行 `workflow.cmd deploy -ConfirmProduction`。
不执行数据库迁移，不修改 Nginx 路由；admin、legacy-api、all 只有我明确指定组件时才处理。
发布前做只读检查和备份，发布后报告 commit、组件、SHA-256、服务状态、HTTP 健康状态、备份路径和回滚命令。
```

## 本地一键发布

先完成一次服务器初始化：创建专用 `deploy` 用户、安装 Ed25519 公钥、配置 `sudo` 仅允许执行 `/usr/local/sbin/lcxqy-deploy` 和 `/usr/local/sbin/lcxqy-rollback`、设置 SSH known_hosts。不要把 root 密码放进脚本或 GitHub Secrets。

本机 PowerShell 执行策略受限时，统一使用根目录 `.cmd` 包装入口：

```powershell
.\workflow.cmd publish replacement-backend
```

首次密钥初始化示例（`<server>` 只在本机替换，真实连接信息仍放在 `markdown_docs/private/`）：

```powershell
ssh-keygen -t ed25519 -a 64 -f "$env:USERPROFILE\.ssh\lcxqy_deploy" -C "lcxqy-deploy"
scp .\deploy\server\lcxqy-deploy.sh root@<server>:/tmp/
scp .\deploy\server\lcxqy-rollback.sh root@<server>:/tmp/
scp .\deploy\server\bootstrap-deploy-user.sh root@<server>:/tmp/
scp "$env:USERPROFILE\.ssh\lcxqy_deploy.pub" root@<server>:/tmp/lcxqy_deploy.pub
ssh root@<server>
```

进入服务器后只执行一次：

```bash
install -m 0755 /tmp/lcxqy-deploy.sh /usr/local/sbin/lcxqy-deploy
install -m 0755 /tmp/lcxqy-rollback.sh /usr/local/sbin/lcxqy-rollback
bash /tmp/bootstrap-deploy-user.sh /tmp/lcxqy_deploy.pub
```

打开第二个终端验证 `deploy` 密钥登录成功，再设置本机环境变量：

```powershell
setx LCXQY_SSH_HOST "<server>"
setx LCXQY_SSH_USER "deploy"
setx LCXQY_SSH_KEY "$env:USERPROFILE\.ssh\lcxqy_deploy"
```

关闭并重新打开终端后变量才会生效。known_hosts 必须通过已有可信连接核对服务器指纹后写入，不要用关闭 `StrictHostKeyChecking` 的方式绕过。

演练，不连接服务器：

```powershell
.\workflow.cmd deploy
```

实际发布前，先切回 `main`、拉取已合并代码并确认 `HEAD` 等于 `origin/main`。发布命令会再次强制检查这些条件：

```powershell
git switch main
git pull --ff-only origin main
.\workflow.cmd check all
.\workflow.cmd deploy -ConfirmProduction
.\workflow.cmd verify replacement-backend
```

发布脚本只接受干净工作区、当前 commit 和已推送的远端分支；生产额外限制为精确的 `origin/main`。它会先执行服务器只读预检，再构建/校验组件、生成带 manifest 和 SHA-256 的压缩包、通过 SSH/SCP 上传到服务器临时目录，最后调用服务器固定入口。`all` 按顺序处理三个组件，不会自动执行迁移或 Nginx 切流。

日常不需要记住组件参数：`deploy` 等价于 `publish replacement-backend`。只有发布 admin、旧 API 或组合组件时，才使用 `publish <component>`。

当前生产旧 API 仍由手工 Java 进程占用 `127.0.0.1:8081`，尚无 `starfree-legacy.service`。因此 `legacy-api` 和 `all` 的生产发布会在上传前主动失败；在单独维护窗口完成 systemd 迁移前，只发布 `replacement-backend` 或 `admin`。

数据库迁移不属于普通一键发布。只有先按 `DEPLOYMENT_GUIDE.md` 审查迁移和备份数据库后，才可在独立维护任务中执行；发布参数 `-RunMigrations` 会故意中止并提醒维护者，不会静默修改数据库。

## GitHub Actions

`.github/workflows/ci.yml` 会在 Pull Request 和 `main` 推送时自动运行后端测试、PHP lint、脚本检查和仓库卫生检查。CI 全部通过后才合并。

`.github/workflows/production-deploy.yml` 只支持手动 `workflow_dispatch`。构建任务不会申请生产权限，并保存 7 天可校验发布包；只有 `dry_run=false` 时部署任务才进入 GitHub `production` Environment。部署任务会确认构建 commit 与当前 `main` 完全相同、校验压缩包 SHA-256，并在上传前检查服务器固定入口和目标 systemd 服务。

在 GitHub 仓库配置 `production` Environment，并将 SSH 私钥、known_hosts、服务器地址和用户名设置为 Environment Secrets；生产 Environment 应启用人工审批。工作流默认 `dry_run=true`，不会写服务器。

建议 Secrets：`LCXQY_SSH_HOST`、`LCXQY_SSH_USER`、`LCXQY_SSH_PRIVATE_KEY`、`LCXQY_SSH_KNOWN_HOSTS`。不要创建 `ROOT_PASSWORD`、数据库密码或支付密钥 Secret 给工作流使用。

## 回滚

服务器端每次发布会在 `/srv/lcxqy/backups/` 保存组件备份。发布输出会打印精确备份路径。也可以使用：

```bash
sudo /usr/local/sbin/lcxqy-rollback --component replacement-backend --backup /srv/lcxqy/backups/<backup>
```

回滚只允许作用于指定组件。回滚后必须重新检查 systemd 状态和对应健康地址；Nginx 路由切换产生的备份仍使用 `backend/deploy/production/` 中的原有回滚脚本处理。

## 每次任务的结束汇报

开发：改动文件、测试命令、测试结果、未验证风险。

发布：commit、组件、包 SHA-256、服务状态、健康 HTTP 状态、备份路径、是否迁移、回滚命令。

审查：问题列表优先，按严重程度排序，带文件和行号；没有问题时明确写“未发现可确认缺陷”。
