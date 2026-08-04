# StarFree Admin 部署包

本目录是从生产服务器 `admin.lcxqy.cn` 核对的 PHP 管理后台程序。它不是重新实现的后台，运行时仍通过 `Config_DB.php` 调用旧 StarFree API、MySQL 和 Redis。

## 目录

| 路径 | 说明 |
|---|---|
| `source/` | PHP 页面、API、静态资源和第三方前端资源 |
| `source/Config_DB.example.php` | 脱敏配置模板，部署时复制为 `Config_DB.php` |
| `deploy/install.sh` | PHP 语法检查、备份和发布脚本 |
| `deploy/nginx-admin.conf` | Nginx 示例配置，证书和 PHP-FPM 版本需按服务器调整 |

服务器导出的程序文件不包含真实 `Config_DB.php`、`.user.ini`、日志、缓存或用户上传内容。后台程序原有版权和许可证文件位于 `source/README.md` 与 `source/LICENSE.md`，二次开发必须遵守其中条款。

## 运行要求

- PHP 7.2 及 `mysqli`、`pdo_mysql`、`curl`、`mbstring`、`openssl`、`json`、`redis` 扩展。
- Nginx 或兼容 Web 服务器。
- 可访问旧 API、MySQL 和 Redis。
- Web 用户对站点目录具有读取权限，PHP 运行时可读取 `Config_DB.php`。

## 配置重点

1. 复制 `source/Config_DB.example.php` 为 `source/Config_DB.php`。
2. 设置 `$api_site`、`$api_key`、数据库和 Redis 参数。
3. `$api_key` 必须与旧 API 配置中的 `webinfo.key` 完全一致。
4. `$ADMIN_PATH` 必须与访问后台的 URL 前缀一致，默认是 `/admin`。
5. 不要将 `Config_DB.php`、`.user.ini` 或备份文件提交到 Git。

完整流程见 [`../../markdown_docs/DEPLOYMENT_GUIDE.md`](../../markdown_docs/DEPLOYMENT_GUIDE.md)。
