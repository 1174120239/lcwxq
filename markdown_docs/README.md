# 项目文档

本目录只保存聊一论坛项目自有的长期维护文档。第三方组件说明和逐日操作流水不在这里重复保存。

## 阅读顺序

| 文档 | 适合谁看 | 内容 |
|---|---|---|
| [AI_PROJECT_BRIEF.md](AI_PROJECT_BRIEF.md) | 维护者、后端/前端开发、接手 AI | 架构、数据模型、功能边界、前端行为、测试和风险 |
| [API_USAGE_GUIDE.md](API_USAGE_GUIDE.md) | 前端、脚本、Bot 和接口开发 | 136 个客户端 API 路径及回调/内部路径、参数、响应和新旧端状态 |
| [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) | 运维、后端发布人员 | 构建、迁移、JAR 部署、Nginx 切流、验收和回滚 |
| [CODEX_WORKFLOW.md](CODEX_WORKFLOW.md) | 使用 Codex 的开发和发布人员 | 会话边界、提示词、本地一键发布和 GitHub Actions |
| [QQBOT_INTEGRATION_GUIDE.md](QQBOT_INTEGRATION_GUIDE.md) | AstrBot 插件开发者 | 账号绑定、发帖、审核后同步、数据库表、部署和测试 |

## 文档维护规则

- 只写当前有效事实和可执行流程，不追加每日工作日志。
- 接口变化更新 API 手册；架构和业务边界变化更新技术手册。
- 部署脚本、路径、迁移或回滚变化更新部署手册。
- Codex 规则、会话提示词或发布入口变化更新工作流手册和根目录 `AGENTS.md`。
- QQBot 方案只记录设计事实；未实现功能必须明确标注。
- 不复制 uni_modules、js_sdk 等第三方 README/changelog。
- 不写入服务器 IP、密码、数据库快照、token、Cookie 或密钥。
- 真实服务器连接信息只保存在被 Git 忽略的 private/SERVER_ACCESS.local.md。
- 删除或重命名文档后同步检查根 README 和所有相对链接。

Git 提交历史承担变更记录，不再维护重复的“重建状态日志”或“Markdown 迁移清单”。
