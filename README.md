# 聊一论坛

聊一论坛是一个基于 uni-app 的校园论坛项目。本仓库包含前端应用、重新实现的 Spring Boot 后端、数据库迁移、生产部署脚本和维护文档。

## 目录结构

| 目录 | 内容 |
|---|---|
| `pages/`、`components/`、`utils/` | uni-app 前端页面、组件与 API 封装 |
| `static/` | 前端图片、图标和品牌资源 |
| `backend/starfree-replacement/` | Java 8 / Spring Boot 2.7 后端源码与测试 |
| `backend/database/migrations/` | 数据库迁移脚本 |
| `backend/deploy/production/` | 生产部署、路由切换和验收脚本 |
| `backend/reference/` | 旧系统的必要接口参考资料，不包含旧后端可执行 JAR |
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

## 文档

- [完整项目技术手册](markdown_docs/AI_PROJECT_BRIEF.md)
- [接口使用手册](markdown_docs/API_USAGE_GUIDE.md)
- [生产部署与回滚手册](markdown_docs/DEPLOYMENT_GUIDE.md)
- [AstrBot QQ 发帖同步设计](markdown_docs/QQBOT_INTEGRATION_GUIDE.md)

## 安全说明

仓库不保存服务器密码、数据库密码、签名证书、生产密钥、APK、构建缓存或本地开发工具。服务器连接信息应存放在被 `.gitignore` 排除的 `markdown_docs/private/` 目录中。

## 许可与责任

项目许可见 [LICENSE.txt](LICENSE.txt)。部署者应自行承担内容审核、用户数据保护、密钥管理和合规运营责任。
