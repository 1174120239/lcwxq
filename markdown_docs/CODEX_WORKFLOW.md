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

## 新开发会话提示词

把下面内容作为新会话第一条消息，并替换尖括号内容：

```text
你在 lcwxq 项目中工作。先阅读根目录 AGENTS.md、markdown_docs/README.md，以及与本任务相关的 API_USAGE_GUIDE.md、AI_PROJECT_BRIEF.md。

任务：<清楚描述一个功能或 bug>
范围：<前端 / replacement 后端 / admin / 文档>
约束：保持旧 API 协议兼容；不要连接生产服务器；不要修改无关文件。
完成标准：实现代码、补充针对性测试、更新相关 Markdown，并汇报测试命令和未验证风险。
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
只发布已提交的指定 commit：<commit SHA 或 HEAD>；组件：<replacement-backend / legacy-api / admin / all>。
默认 dry-run，不执行数据库迁移，不修改 Nginx 路由。只有我明确确认后，才使用 -ConfirmProduction。
发布前做只读检查和备份，发布后报告 commit、组件、SHA-256、服务状态、HTTP 健康状态、备份路径和回滚命令。
```

## 本地一键发布

先完成一次服务器初始化：创建专用 `deploy` 用户、安装 Ed25519 公钥、配置 `sudo` 仅允许执行 `/usr/local/sbin/lcxqy-deploy` 和 `/usr/local/sbin/lcxqy-rollback`、设置 SSH known_hosts。不要把 root 密码放进脚本或 GitHub Secrets。

本机 PowerShell 执行策略受限时，直接使用 `.cmd` 包装入口：

```powershell
.\deploy\publish-to-server.cmd -Component replacement-backend -DryRun
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
.\deploy\publish-to-server.ps1 -Component replacement-backend -DryRun
```

实际发布：

```powershell
.\deploy\publish-to-server.ps1 -Component replacement-backend -ConfirmProduction
.\deploy\publish-to-server.ps1 -Component legacy-api -ConfirmProduction
.\deploy\publish-to-server.ps1 -Component admin -ConfirmProduction
.\deploy\publish-to-server.cmd -Component all -ConfirmProduction
```

发布脚本只接受干净工作区和当前 commit；它会构建/校验组件、生成带 manifest 和 SHA-256 的压缩包、通过 SSH/SCP 上传到服务器临时目录，再调用服务器固定入口。`all` 按顺序处理三个组件，不会自动执行迁移或 Nginx 切流。

发布入口和 GitHub Actions 会先运行 `deploy/verify-feature-baseline.ps1`。该检查不编译前端、不连接服务器，只验证动态数据分析、用户详情、AI 审核、投票及相关接口文件仍然存在，避免分支合并时整块功能被删除后继续发布。日常合并前也可以单独运行：

```powershell
.\deploy\verify-feature-baseline.ps1
```

数据库迁移不属于普通一键发布。只有先按 `DEPLOYMENT_GUIDE.md` 审查迁移和备份数据库后，才可在独立维护任务中执行。当前 `-RunMigrations` 仅对白名单中的校园互助迁移 014 和 `replacement-backend` 生效；入口会固定校验 SQL SHA-256、备份已有互助表并验证结构。任何其他组件或迁移请求都会在生产变更前中止。

## GitHub Actions

仓库中的 `.github/workflows/production-deploy.yml` 只支持手动 `workflow_dispatch`。在 GitHub 仓库配置 `production` Environment，并将 SSH 私钥、known_hosts、服务器地址和用户名设置为 Secrets；生产 Environment 建议启用审批。工作流默认 `dry_run=true`，不会写服务器。

建议 Secrets：`LCXQY_SSH_HOST`、`LCXQY_SSH_USER`、`LCXQY_SSH_PRIVATE_KEY`、`LCXQY_SSH_KNOWN_HOSTS`。不要创建 `ROOT_PASSWORD`、数据库密码或支付密钥 Secret 给工作流使用。

## 回滚

服务器端每次发布会在 `/srv/lcxqy/backups/` 保存组件备份。发布输出会打印精确备份路径。也可以使用：

```bash
sudo /usr/local/sbin/lcxqy-rollback --component replacement-backend --backup /srv/lcxqy/backups/<backup>
```

回滚只允许作用于指定组件。回滚后必须重新检查 systemd 状态和对应健康地址；Nginx 路由切换产生的备份仍使用 `backend/deploy/production/` 中的原有回滚脚本处理。
Java 服务回滚入口会在重启后最多等待 60 秒，同时检查 systemd active 状态和 HTTP 健康接口，避免把正常启动过程误报为回滚失败。

## 每次任务的结束汇报

开发：改动文件、测试命令、测试结果、未验证风险。

发布：commit、组件、包 SHA-256、服务状态、健康 HTTP 状态、备份路径、是否迁移、回滚命令。

审查：问题列表优先，按严重程度排序，带文件和行号；没有问题时明确写“未发现可确认缺陷”。
