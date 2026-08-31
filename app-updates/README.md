# Android WGT 发布目录

这个目录对应服务器上的：

`/opt/starfree/files/static/app-updates/`

域名映射后，客户端访问地址为：

`https://frp.lcxqy.cn/app-updates/update.json`

后台“功能设置 → 版本管理”的新增版本页面可以直接上传 `.wgt`，服务器会自动校验包内 AppID/版本号、计算 SHA-256，并原子更新 `update.json`。手工发布时，也可以把生成的 `.wgt` 文件和更新清单放到服务器这个目录。清单使用 `update.json.example` 的字段，`versionCode` 必须大于 App 当前版本号，`wgtUrl` 必须是 HTTPS 直链。

WGT 只用于页面、脚本、样式和静态资源更新。修改原生模块、权限、推送配置、App 图标或 Manifest 时，仍需重新云打包 APK。

发布前先在 HBuilderX 5.24 中生成 App 资源升级包（WGT），使用同一个 AppID `__UNI__850911F`。先用测试 APK 验证下载、安装和重启，再替换线上清单。
