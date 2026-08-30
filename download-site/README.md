# 聊城一中论坛下载介绍站

这是一个无需构建工具的独立静态站点，可直接上传到任意静态空间。

## 文件

- `index.html`：页面结构与文案
- `styles.css`：响应式样式，支持手机端
- `app.js`：公开接口数据同步与下载链接配置
- `assets/school-emblem.png`：校徽素材

## 数据来源

- 用户数：`https://admin.lcxqy.cn/Api/api.php?act=usercount`
- 动态数：`https://api.lcxqy.cn/SFreeSpace/spaceList?page=1&limit=1` 的 `total`
- 当前版本：`https://admin.lcxqy.cn/Api/api.php?update=1`
- 历史版本：`https://admin.lcxqy.cn/Api/api.php?act=versionList`，由后台版本管理表 `*_admin_update` 提供
- Android 下载：`https://api.lcxqy.cn/SFreeInvitation/config` 的 `androidDownloadUrl`，未配置时回退到当前版本 `versionUrl`
- 网页版：`https://prev.lcxqy.cn/`

接口若未配置跨域响应头，浏览器会阻止前端读取数据；部署新域名后，需要在后台 `Api/set.php` 的 CORS 白名单加入该站点来源，或在站点服务器做同源代理。不要把 StarFree 的 `StarFreeSystem/apiNewVersion` 当作本论坛版本源。
