import asyncio
import json
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Dict, Iterable, List, Optional

from astrbot.api import AstrBotConfig, logger
from astrbot.api.event import AstrMessageEvent, MessageChain, MessageEventResult, filter
from astrbot.api.star import Context, Star, register

try:
    import astrbot.api.message_components as Comp
except Exception:  # pragma: no cover - depends on AstrBot runtime version
    Comp = None


class BackendError(Exception):
    def __init__(self, message: str, data: Optional[Dict[str, Any]] = None):
        super().__init__(message)
        self.message = message
        self.data = data or {}


@register(
    "lcxqy_dynamic_ai",
    "lcxqy",
    "聊一下论坛动态 QQ 助手：NapCat 个人 QQ 账号接入、DeepSeek 聊天、账号绑定、动态工具和群同步。",
    "0.1.1",
)
class LcxqyDynamicAiPlugin(Star):
    _COMMAND_NAMES = (
        "动态助手",
        "绑定论坛",
        "绑定",
        "bind",
        "我的状态",
        "积分",
        "状态",
        "签到状态",
        "签到",
        "sign",
        "signin",
        "发动态",
        "动态",
        "修改资料",
        "绑定本群同步",
    )

    def __init__(self, context: Context, config: AstrBotConfig = None):
        super().__init__(context)
        self.config = config or {}
        self._pending: Dict[str, Dict[str, Any]] = {}
        self._sync_task: Optional[asyncio.Task] = None

    async def initialize(self):
        if self._cfg_bool("sync_enabled", True):
            self._sync_task = asyncio.create_task(self._sync_loop())

    async def terminate(self):
        if self._sync_task:
            self._sync_task.cancel()
            try:
                await self._sync_task
            except asyncio.CancelledError:
                pass

    @filter.command("动态助手")
    async def help(self, event: AstrMessageEvent):
        event.stop_event()
        yield event.plain_result(
            "我是聊一下动态助手。可用命令：\n"
            "/绑定论坛\n"
            "/我的状态\n"
            "/签到\n"
            "/发动态 内容\n"
            "/修改资料 昵称 新昵称\n"
            "/修改资料 简介 新简介\n"
            "/绑定本群同步\n"
            "发动态和修改资料需要回复“确认发布”或“确认修改”。"
        )

    @filter.command("绑定论坛", alias={"绑定", "bind"})
    async def bind_forum(self, event: AstrMessageEvent):
        event.stop_event()
        try:
            data = await self._api("/SFreeBot/bindChallenge", {
                "qqUserId": self._sender_id(event),
                "groupId": self._group_id(event),
            })
            yield event.plain_result(
                "请打开下面链接登录论坛账号完成绑定：\n"
                f"{data.get('bindUrl')}\n"
                "这个登录只用于绑定 QQ，不会挤掉论坛里的其他登录设备。"
            )
        except BackendError as error:
            yield event.plain_result(f"绑定链接生成失败：{error.message}")

    @filter.command("我的状态", alias={"积分", "状态", "签到状态"})
    async def my_status(self, event: AstrMessageEvent):
        event.stop_event()
        try:
            data = await self._api("/SFreeBot/meStatus", {"qqUserId": self._sender_id(event)})
            if not data.get("bound"):
                yield event.plain_result(await self._bind_hint(event))
                return
            user = data.get("user") or {}
            signin = data.get("signin") or {}
            yield event.plain_result(
                "当前论坛账号：{name}\n积分：{points}\n经验：{experience}\n余额：{assets}\n连续签到：{streak} 天".format(
                    name=user.get("screenName") or user.get("name") or "未命名",
                    points=user.get("points", 0),
                    experience=user.get("experience", 0),
                    assets=user.get("assets", 0),
                    streak=signin.get("leiji", 0),
                )
            )
        except BackendError as error:
            yield event.plain_result(await self._error_or_bind(event, error))

    @filter.command("签到", alias={"sign", "signin"})
    async def signin(self, event: AstrMessageEvent):
        event.stop_event()
        try:
            data = await self._api("/SFreeBot/signin", {
                "qqUserId": self._sender_id(event),
                "requestId": self._request_id(event, "signin"),
            })
            yield event.plain_result(
                "签到成功：连续 {continuous} 天，获得余额 {assets}，经验 {experience}。".format(
                    continuous=data.get("continuous", 0),
                    assets=data.get("assets", 0),
                    experience=data.get("experience", 0),
                )
            )
        except BackendError as error:
            yield event.plain_result(await self._error_or_bind(event, error))

    @filter.command("发动态", alias={"动态"})
    async def prepare_space(self, event: AstrMessageEvent):
        event.stop_event()
        text = self._command_tail(event, ("发动态", "动态")).strip()
        images = self._images_from_event(event)
        if not text and not images:
            yield event.plain_result("请在命令后写动态内容，或附带图片。例：/发动态 今天操场晚霞很好看")
            return
        key = self._pending_key(event)
        self._pending[key] = {
            "type": "addSpace",
            "created": time.time(),
            "payload": {
                "qqUserId": self._sender_id(event),
                "requestId": self._request_id(event, "space"),
                "text": text,
                "pic": ",".join(images),
                "onlyMe": "0",
            },
        }
        preview = text if len(text) <= 180 else text[:180] + "..."
        yield event.plain_result(f"待发布动态：\n{preview}\n回复“确认发布”发布，回复“取消”取消。")

    @filter.command("修改资料")
    async def prepare_profile(self, event: AstrMessageEvent):
        event.stop_event()
        tail = self._command_tail(event, ("修改资料",)).strip()
        field, value = self._parse_profile_update(tail)
        if not field:
            yield event.plain_result("请这样写：/修改资料 昵称 新昵称，或 /修改资料 简介 新简介")
            return
        key = self._pending_key(event)
        self._pending[key] = {
            "type": "updateProfile",
            "created": time.time(),
            "payload": {
                "qqUserId": self._sender_id(event),
                "requestId": self._request_id(event, "profile"),
                field: value,
            },
        }
        yield event.plain_result(f"待修改资料：{self._profile_label(field)} = {value}\n回复“确认修改”提交，回复“取消”取消。")

    @filter.command("绑定本群同步")
    async def register_group(self, event: AstrMessageEvent):
        event.stop_event()
        group_id = self._group_id(event)
        if not group_id:
            yield event.plain_result("这个命令需要在 QQ 群里使用。")
            return
        try:
            await self._api("/SFreeBot/registerGroup", {
                "qqUserId": self._sender_id(event),
                "groupId": group_id,
                "groupName": self._group_name(event),
                "unifiedMsgOrigin": self._unified_origin(event),
            })
            yield event.plain_result("本群已加入动态同步，后续可在后台 QQ Bot设置 中调整开关和摘要。")
        except BackendError as error:
            yield event.plain_result(f"群同步绑定失败：{error.message}")

    @filter.event_message_type(filter.EventMessageType.PRIVATE_MESSAGE)
    async def on_private_message(self, event: AstrMessageEvent):
        async for item in self._handle_message(event):
            yield item

    @filter.event_message_type(filter.EventMessageType.GROUP_MESSAGE)
    async def on_group_message(self, event: AstrMessageEvent):
        async for item in self._handle_message(event):
            yield item

    async def _handle_message(self, event: AstrMessageEvent):
        text = (event.message_str or "").strip()
        if not text:
            return
        if text in ("取消", "算了", "取消发布", "取消修改"):
            event.stop_event()
            if self._pending.pop(self._pending_key(event), None):
                yield event.plain_result("已取消。")
            return
        if text in ("确认发布", "确认修改"):
            event.stop_event()
            async for item in self._confirm(event, text):
                yield item
            return
        if self._is_command_text(text):
            return
        if text.startswith(("/", "／", "!", "！")):
            return
        if not self._cfg_bool("chat_enabled", True):
            return
        if self._group_id(event) and not self._cfg_bool("chat_in_groups", False):
            return
        event.stop_event()
        try:
            data = await self._api("/SFreeBot/chat", {
                "qqUserId": self._sender_id(event),
                "message": text,
            })
            content = (data.get("content") or "").strip()
            if content:
                yield event.plain_result(content)
        except BackendError as error:
            logger.warning("lcxqy_dynamic_ai chat failed: %s", error.message)

    async def _confirm(self, event: AstrMessageEvent, text: str) -> Iterable[MessageEventResult]:
        key = self._pending_key(event)
        pending = self._pending.get(key)
        if not pending or time.time() - pending.get("created", 0) > 300:
            self._pending.pop(key, None)
            yield event.plain_result("没有待确认操作，或操作已过期。")
            return
        if text == "确认发布" and pending.get("type") != "addSpace":
            yield event.plain_result("当前没有待发布动态。")
            return
        if text == "确认修改" and pending.get("type") != "updateProfile":
            yield event.plain_result("当前没有待修改资料。")
            return
        self._pending.pop(key, None)
        try:
            path = "/SFreeBot/addSpace" if pending["type"] == "addSpace" else "/SFreeBot/updateProfile"
            data = await self._api(path, pending["payload"])
            if pending["type"] == "addSpace":
                msg = data.get("msg") or ("动态已提交审核" if data.get("pending") else "动态已发布")
                url = data.get("h5Url")
                yield event.plain_result(f"{msg}\n{url}" if url else msg)
            else:
                yield event.plain_result("资料已修改。")
        except BackendError as error:
            yield event.plain_result(await self._error_or_bind(event, error))

    async def _sync_loop(self):
        await asyncio.sleep(5)
        while True:
            try:
                config = await self._api("/SFreeBot/config", {})
                interval = int(config.get("syncIntervalSeconds") or 45)
                if config.get("enabled"):
                    for group in config.get("groups") or []:
                        await self._sync_group(group)
                await asyncio.sleep(max(10, interval))
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.warning("lcxqy_dynamic_ai sync loop failed:\n%s", traceback.format_exc())
                await asyncio.sleep(30)

    async def _sync_group(self, group: Dict[str, Any]):
        group_id = str(group.get("groupId") or "")
        origin = str(group.get("unifiedMsgOrigin") or "")
        if not group_id or not origin:
            return
        after_id = int(group.get("cursorSpaceId") or 0)
        data = await self._api("/SFreeBot/latestSpaces", {
            "groupId": group_id,
            "afterId": str(after_id),
            "limit": str(self._cfg_int("sync_limit", 5)),
        })
        for space in data.get("spaces") or []:
            await self._deliver_space(origin, group_id, space)

    async def _deliver_space(self, origin: str, group_id: str, space: Dict[str, Any]):
        space_id = str(space.get("id") or "")
        try:
            chain = self._space_chain(space)
            result = await self.context.send_message(origin, chain)
            message_id = getattr(result, "message_id", "") if result is not None else ""
            await self._api("/SFreeBot/delivery", {
                "groupId": group_id,
                "spaceId": space_id,
                "status": "success",
                "messageId": str(message_id),
            })
        except Exception as error:
            await self._api("/SFreeBot/delivery", {
                "groupId": group_id,
                "spaceId": space_id,
                "status": "error",
                "error": str(error),
            })

    def _space_chain(self, space: Dict[str, Any]) -> Any:
        text = self._space_text(space)
        images = [str(item) for item in (space.get("images") or []) if item]
        if Comp is None:
            chain = MessageChain().message(text)
            for image_url in images:
                chain.message("\n" + image_url)
            return chain
        components: List[Any] = [Comp.Plain(text)]
        for image_url in images:
            image_factory = getattr(Comp.Image, "fromURL", None) or getattr(Comp.Image, "from_url", None)
            if image_factory:
                components.append(image_factory(image_url))
            else:
                components.append(Comp.Plain("\n" + image_url))
        return MessageChain(components)

    def _space_text(self, space: Dict[str, Any]) -> str:
        author = space.get("author") or {}
        topics = space.get("topics") or []
        topic_text = ""
        if topics:
            names = [("#" + str(item.get("name"))) for item in topics if item.get("name")]
            topic_text = "\n" + " ".join(names)
        return (
            "论坛有新动态\n"
            f"{author.get('name') or '用户'}：{space.get('summary') or space.get('text') or ''}"
            f"{topic_text}\n"
            f"{space.get('h5Url') or ''}"
        )

    async def _api(self, path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        base = str(self._cfg("backend_base_url", "http://127.0.0.1:18082")).rstrip("/")
        secret = str(self._cfg("bot_secret", ""))
        if not secret:
            raise BackendError("插件未配置 bot_secret")
        data = dict(payload)
        data.setdefault("botSecret", secret)
        data.setdefault("platform", self._cfg("platform", "qq"))
        body = urllib.parse.urlencode({k: "" if v is None else str(v) for k, v in data.items()}).encode("utf-8")
        request = urllib.request.Request(
            base + path,
            data=body,
            headers={"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"},
            method="POST",
        )
        loop = asyncio.get_running_loop()
        raw = await loop.run_in_executor(None, self._urlopen_text, request)
        parsed = json.loads(raw)
        if int(parsed.get("code", 0)) != 1:
            raise BackendError(str(parsed.get("msg") or "请求失败"), parsed.get("data") or {})
        data = parsed.get("data")
        return data if isinstance(data, dict) else {}

    def _urlopen_text(self, request: urllib.request.Request) -> str:
        try:
            with urllib.request.urlopen(request, timeout=35) as response:
                return response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            raise BackendError(body or str(error)) from error
        except urllib.error.URLError as error:
            raise BackendError(str(error)) from error

    async def _error_or_bind(self, event: AstrMessageEvent, error: BackendError) -> str:
        if "绑定" in error.message:
            return await self._bind_hint(event)
        return error.message

    async def _bind_hint(self, event: AstrMessageEvent) -> str:
        try:
            data = await self._api("/SFreeBot/bindChallenge", {"qqUserId": self._sender_id(event)})
            return "请先绑定论坛账号：\n{}\n没有账号请先去论坛注册。".format(data.get("bindUrl"))
        except BackendError as error:
            return "QQ 尚未绑定论坛账号，且绑定链接生成失败：" + error.message

    def _parse_profile_update(self, tail: str):
        parts = tail.split(maxsplit=1)
        if len(parts) != 2:
            return None, None
        aliases = {
            "昵称": "screenName",
            "名字": "screenName",
            "简介": "introduce",
            "头像": "avatar",
            "校区": "campusId",
            "入学年份": "gradeId",
            "年级": "gradeId",
        }
        return aliases.get(parts[0]), parts[1].strip()

    def _profile_label(self, field: str) -> str:
        labels = {
            "screenName": "昵称",
            "introduce": "简介",
            "avatar": "头像",
            "campusId": "校区ID",
            "gradeId": "入学年份ID",
        }
        return labels.get(field, field)

    def _command_tail(self, event: AstrMessageEvent, names: Iterable[str]) -> str:
        text = event.message_str or ""
        normalized = text.lstrip("/／!！").strip()
        for name in names:
            if normalized.startswith(name):
                return normalized[len(name):].strip()
        return ""

    def _is_command_text(self, text: str) -> bool:
        normalized = text.lstrip("/／!！").strip()
        return any(
            normalized == name or normalized.startswith(name + " ")
            for name in self._COMMAND_NAMES
        )

    def _images_from_event(self, event: AstrMessageEvent) -> List[str]:
        message = getattr(event.message_obj, "message", None) or []
        result = []
        for segment in message:
            data = getattr(segment, "data", None)
            if isinstance(data, dict):
                url = data.get("url") or data.get("file") or data.get("path")
                if url:
                    result.append(str(url))
            else:
                for attr in ("url", "file", "path"):
                    value = getattr(segment, attr, None)
                    if value:
                        result.append(str(value))
                        break
        return result

    def _sender_id(self, event: AstrMessageEvent) -> str:
        try:
            return str(event.get_sender_id())
        except Exception:
            sender = getattr(event.message_obj, "sender", None)
            return str(getattr(sender, "user_id", "") or getattr(event.message_obj, "user_id", ""))

    def _group_id(self, event: AstrMessageEvent) -> str:
        return str(getattr(event.message_obj, "group_id", "") or "")

    def _group_name(self, event: AstrMessageEvent) -> str:
        return str(getattr(event.message_obj, "group_name", "") or "")

    def _unified_origin(self, event: AstrMessageEvent) -> str:
        getter = getattr(event, "unified_msg_origin", None)
        if callable(getter):
            return str(getter())
        return str(getattr(event, "unified_msg_origin", "") or getattr(event.message_obj, "unified_msg_origin", ""))

    def _pending_key(self, event: AstrMessageEvent) -> str:
        return f"{self._group_id(event)}:{self._sender_id(event)}"

    def _request_id(self, event: AstrMessageEvent, action: str) -> str:
        message_id = str(getattr(event.message_obj, "message_id", "") or int(time.time() * 1000))
        return f"qqbot-{action}-{self._sender_id(event)}-{message_id}"

    def _cfg(self, key: str, default: Any = None) -> Any:
        getter = getattr(self.config, "get", None)
        return getter(key, default) if callable(getter) else default

    def _cfg_bool(self, key: str, default: bool) -> bool:
        value = self._cfg(key, default)
        if isinstance(value, bool):
            return value
        return str(value).lower() in ("1", "true", "yes", "on")

    def _cfg_int(self, key: str, default: int) -> int:
        try:
            return int(self._cfg(key, default))
        except (TypeError, ValueError):
            return default
