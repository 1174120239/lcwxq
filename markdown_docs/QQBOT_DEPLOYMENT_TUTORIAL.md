# 云云 QQ 动态助手部署教程

这份教程用于把论坛 QQ 助手部署到一台个人电脑上。接入方式是：**个人 QQ 账号 + NapCat + AstrBot + OneBot v11**，不是 QQ 官方机器人，也不需要 AppID、AppSecret 或官方 QQ Bot 平台。

## 一、需要准备的东西

### 必需软件

1. Windows 电脑，建议保持 QQ、NapCat 和 AstrBot 长时间运行。
2. QQNT 桌面版，并使用个人 QQ 账号登录。
3. NapCatQQ：负责让个人 QQ 提供 OneBot v11 接口。
4. AstrBot：负责接收 QQ 消息、运行插件和调用模型。
5. 本项目插件压缩包：
   `integrations/qqbot/astrabot_plugin_lcxqy_dynamic_ai.zip`

### 可选软件

- DeepSeek API Key：只在需要 AI 聊天和自然语言识别时需要。
- Git：只在从源码安装或更新插件时需要，普通部署直接使用 ZIP 即可。

NapCat 官方项目：<https://github.com/NapNeko/NapCatQQ>

AstrBot 文档：<https://docs.astrbot.app/>

## 二、先确认使用哪一个后端

### 使用现成论坛后端

普通使用者只需要把插件配置为：

```text
https://api.lcxqy.cn
```

论坛后台管理员需要提前设置 QQ Bot 配置和 Bot Secret。部署者不需要安装 Java、MySQL 或 Redis。

### 自建论坛后端

如果对方不是使用本论坛现成服务，还需要单独部署论坛后端、数据库、Redis、图片上传服务和 `/SFreeBot/*` 接口。这不是本教程的范围，不能只安装 AstrBot 插件就使用。

## 三、安装并登录 NapCat

1. 安装 QQNT，确认个人 QQ 可以正常登录。
2. 按 NapCat 官方文档安装与当前 QQNT 版本匹配的 NapCat。
3. 启动 NapCat 后，在 NapCat 的 OneBot v11 网络配置中新增一个**反向 WebSocket**连接。
4. 本机 AstrBot 使用以下连接参数：

```text
连接类型：反向 WebSocket
地址：ws://127.0.0.1:8083/ws
Token：与 AstrBot 适配器设置一致；未启用 Token 时保持两端都为空
心跳：使用默认值即可
```

5. 保存配置并重启 NapCat/QQ。

不要把 NapCat 的 WebUI Token 当成 Bot Secret。NapCat WebUI Token 只用于打开 NapCat 管理页；Bot Secret 用于插件访问论坛后端。

## 四、安装 AstrBot

1. 安装 AstrBot 桌面版或按官方文档安装 AstrBot。
2. 启动 AstrBot，打开管理页面。
3. 添加 OneBot v11 适配器，选择反向 WebSocket 服务端，监听：

```text
监听地址：127.0.0.1
监听端口：8083
路径：/ws
```

4. 如果设置了访问 Token，NapCat 和 AstrBot 必须填写同一个值。
5. 先启动 AstrBot，再启动 NapCat，查看 AstrBot 日志中是否显示 OneBot 连接成功。

同一个 NapCat 账号只能连接一个负责回复的 AstrBot 实例。不要同时连接本机 AstrBot 和服务器 AstrBot，否则一条 QQ 消息会出现重复回复。

## 五、安装论坛插件

### ZIP 安装

在 AstrBot 管理页面的插件管理中选择“本地安装”，上传：

```text
astrbot_plugin_lcxqy_dynamic_ai.zip
```

安装后重启 AstrBot，确认插件列表出现：

```text
lcxqy_dynamic_ai
```

### 手动安装

如果管理页面不能上传 ZIP，可以解压整个目录到 AstrBot 的插件目录。目录结构必须保持为：

```text
astrbot_plugin_lcxqy_dynamic_ai/
├─ main.py
├─ metadata.yaml
├─ _conf_schema.json
└─ requirements.txt
```

插件依赖会由 AstrBot 根据 `requirements.txt` 安装，核心依赖是：

```text
Pillow>=10.0.0
```

不要只复制 `main.py`，否则插件元数据和配置界面不会出现。

## 六、填写插件配置

在 AstrBot 的插件配置中填写：

```json
{
  "backend_base_url": "https://api.lcxqy.cn",
  "bot_secret": "与论坛后台完全一致的密码",
  "platform": "qq",
  "chat_enabled": true,
  "sync_enabled": true,
  "sync_limit": 5
}
```

字段说明：

| 字段 | 填写内容 |
|---|---|
| `backend_base_url` | 论坛后端地址，现成服务填写 `https://api.lcxqy.cn` |
| `bot_secret` | 论坛后台 QQ Bot 设置中的 Bot Secret |
| `platform` | 固定填写 `qq` |
| `chat_enabled` | 是否启用 DeepSeek 对话和自然语言识别 |
| `sync_enabled` | 是否启用群动态同步和 QQ 空间轮询 |
| `sync_limit` | 每轮每个群最多同步多少条动态 |

Bot Secret 建议使用随机字符串，不能使用 QQ 密码、论坛密码或 DeepSeek Key。配置文件、截图和日志都不要公开这个值。

## 七、论坛后台配置

论坛管理员进入：

```text
后台 → QQ Bot设置
```

至少完成以下配置：

1. 启用 QQ Bot。
2. 设置 Bot Secret，并把同一个值填回 AstrBot 插件。
3. 设置 DeepSeek API Key、Base URL 和模型（需要 AI 聊天时）。
4. 设置动态 H5 地址，例如：

```text
https://prev.lcxqy.cn/#/pages/space/info?id=<动态ID>
```

5. 设置允许的工具：发动态、修改资料、积分/签到状态、签到、评论动态。
6. 如需群同步，新增群号并勾选启用；群名可选，`unified_msg_origin` 不需要手填。
7. 如需群聊 AI，开启“群聊普通对话”；关闭时群内闲聊不回复，但动态同步、引用评论和明确的论坛操作仍可用。
8. 如需 QQ 空间同步，在“QQ 空间每日同步”中配置发布模式、时间、批量上限、图片模板、可见范围和简短正文。

动态是本助手的核心内容入口，不提供普通帖子、文章或文章评论能力。

## 八、第一次测试顺序

建议按顺序测试，出现问题时容易定位：

1. 在 QQ 私聊机器人发送：`你好`。
2. 发送：`绑定论坛账号`，打开机器人返回的绑定页完成绑定。
3. 发送：`我的状态`，确认能返回论坛账号和积分/签到状态。
4. 发送：`签到`，确认只签到一次并返回结果。
5. 发送：`帮我发一个动态，内容是今天天气很好`，确认预览后回复：`发吧`。
6. 发送一张图片并说：`帮我发动态`，确认图片进入预览后再回复：`确认发布`。
7. 在后台配置同步群，等待最新动态推送。
8. 引用机器人发送的动态消息并输入评论，确认评论写入论坛。

## 九、常见问题

### 完全不回复

- 确认 AstrBot OneBot 适配器正在运行。
- 确认 NapCat 反向 WebSocket 地址是 `ws://127.0.0.1:8083/ws`。
- 确认没有另一台 AstrBot 同时连接同一个 QQ。
- 查看 AstrBot 日志是否出现插件加载异常。

### 提示 Bot Secret 错误

- 后台和插件必须使用同一个 Bot Secret。
- 修改后台密码后，必须同步修改插件配置并重启/刷新插件。
- NapCat WebUI Token、OneBot Token、Bot Secret 是三个不同的值。

### 提示需要绑定论坛账号

这是正常流程。用户必须通过机器人发送的绑定页登录自己的论坛账号；绑定成功后，后续动态、签到和资料操作会使用该 QQ 与论坛账号的持久绑定关系。

### 图片上传失败

- 单张图片不能超过 8 MB，格式应为 JPEG、PNG、GIF 或 WebP。
- 不要把 QQ 临时 CDN 链接手工填入论坛动态。
- 保留原动态草稿，重新发送图片后再回复“发吧”。
- 检查论坛后端的图片上传服务和旧 API 是否正常。

### 群里没有同步动态

- 后台确认群号已启用。
- 确认机器人已经加入该群并有发消息、发图片权限。
- 首次启用只发送当前最新一条，之后才按游标增量同步。
- 群同步与“群聊普通对话”是独立开关。

### 出现两次回复

同一个 QQ 同时连接了两个 AstrBot 实例。关闭其中一个 OneBot 连接，只保留一个实例处理消息。

## 十、安全要求

- 不要把 QQ 密码、论坛密码、Bot Secret、DeepSeek Key、OneBot Token 或 NapCat WebUI Token 发到群里。
- 不要关闭论坛原有的审核、频率限制和权限校验。
- 不要把个人 QQ 账号当作 QQ 官方机器人账号宣传或接入官方 Bot API。
- 卸载插件时只删除插件目录，不要删除 AstrBot 数据目录，除非确认不需要草稿和会话记录。
- 服务器部署不是本地部署的必要步骤；个人 QQ + NapCat + AstrBot 可以全部运行在同一台电脑上。

