# 旧 StarFree API

本目录保存生产环境仍依赖的闭源 `StarFreeApi.jar` 及其可复现部署文件。服务器没有该版本源码，因此这里不能执行 Maven 构建，也不能对 JAR 内部实现提供源码级保证。

## 文件

| 路径 | 用途 |
|---|---|
| `dist/StarFreeApi.jar` | 从生产服务器导出的原始可执行 JAR |
| `SHA256SUMS` | JAR 完整性校验 |
| `config/application.example.properties` | 不含生产凭据的完整配置模板 |
| `deploy/start.sh` | systemd 启动入口，只监听 `127.0.0.1` |
| `deploy/starfree-legacy.service` | systemd unit |
| `deploy/install.sh` | 安装、备份、校验和启动脚本 |

原始 JAR 信息：

- 导出日期：2026-08-04
- 生产路径：`/opt/StarFreeApi.jar`
- 文件大小：84,018,614 字节
- SHA-256：`c2daa75c2c6a2968bea2d72783fc4a6844c666306daeacdf936e31dc9cb89c26`

真实配置不得提交。部署前把模板安装为 `/opt/application.properties`，填写数据库、Redis、邮件和 `webinfo.key`。`webinfo.key` 必须与 admin 的 `$api_key` 相同。

完整步骤见 [`../../markdown_docs/DEPLOYMENT_GUIDE.md`](../../markdown_docs/DEPLOYMENT_GUIDE.md)。
