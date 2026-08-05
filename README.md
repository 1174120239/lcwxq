# 聊一论坛

聊一论坛是一个基于 uni-app 的校园论坛项目。本仓库包含前端应用、PHP 管理后台、旧闭源 API 的可部署 JAR、重新实现的 Spring Boot 后端、数据库迁移、生产部署脚本和维护文档。

## 统一工作流

Windows 下从仓库根目录使用 `workflow.cmd`，无需手工记忆 Maven、PHP lint 或发布脚本参数：

```powershell
.\workflow.cmd doctor
.\workflow.cmd start feature-name
.\workflow.cmd check all
.\workflow.cmd deploy
```

`deploy` 默认只是本地演练，不连接服务器。代码经 Pull Request 合并到 `main` 后，单独开启发布会话并显式加 `-ConfirmProduction` 才会部署生产。完整步骤见 [Codex 工作流](markdown_docs/CODEX_WORKFLOW.md)。

## 目录结构

| 目录 | 内容 |
|---|---|
| `pages/`、`components/`、`utils/` | uni-app 前端页面、组件与 API 封装 |
| `static/` | 前端图片、图标和品牌资源 |
| `admin/starfree-admin/` | 从生产服务器核对的 PHP 管理后台、脱敏配置模板和部署脚本 |
| `backend/legacy-api/` | 旧闭源 Java API 的可部署 JAR、配置模板和 systemd 服务 |
| `backend/starfree-replacement/` | Java 8 / Spring Boot 2.7 后端源码与测试 |
| `backend/database/migrations/` | 数据库迁移脚本 |
| `backend/deploy/production/` | 生产部署、路由切换和验收脚本 |
| `workflow.cmd`、`workflow.ps1` | 开发诊断、分支创建、检查、发布和验收的统一入口 |
| `deploy/` | 底层发布实现、服务器固定部署入口和回滚脚本 |
| `backend/reference/` | 旧 Java API 的 Mapper 等逆向参考资料，不作为独立源码发布 |
| `integrations/` | 外部集成代码或设计入口 |
| `markdown_docs/` | 项目手册、接口文档与集成设计 |

## 前端开发

使用 HBuilderX 打开仓库根目录即可运行或发行 uni-app 项目。`unpackage/` 是本地编译产物，不纳入版本控制。

## 后端开发

后端要求 Java 8 或更高版本、Maven 3.9+、MySQL 和可选 Redis。数据库密码等敏感配置必须通过环境变量或本地密钥文件提供，不应写入仓库。

```powershell
cd backend/starfree-replacement
mvn test
mvn spring-boot:run
```

主要环境变量包括 `DB_HOST`、`DB_PORT`、`DB_NAME`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT` 和 `REDIS_PASSWORD`。

旧 API 没有源码，仓库只保存从生产服务器核验的可部署 JAR。其安装方式和 PHP admin 的完整部署步骤见[生产部署与回滚手册](markdown_docs/DEPLOYMENT_GUIDE.md)。

## 文档

- [完整项目技术手册](markdown_docs/AI_PROJECT_BRIEF.md)
- [接口使用手册](markdown_docs/API_USAGE_GUIDE.md)
- [生产部署与回滚手册](markdown_docs/DEPLOYMENT_GUIDE.md)
- [Codex 会话与一键发布工作流](markdown_docs/CODEX_WORKFLOW.md)
- [AstrBot QQ 发帖同步设计](markdown_docs/QQBOT_INTEGRATION_GUIDE.md)

## 安全说明

仓库不保存服务器密码、数据库密码、签名证书、生产密钥、APK、构建缓存或本地开发工具。服务器连接信息应存放在被 `.gitignore` 排除的 `markdown_docs/private/` 目录中。

## 许可与责任

项目许可见 [LICENSE.txt](LICENSE.txt)。部署者应自行承担内容审核、用户数据保护、密钥管理和合规运营责任。
