import asyncio
import json
import os
import re
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional

from astrbot.api import AstrBotConfig, logger
from astrbot.api.event import AstrMessageEvent, MessageChain, MessageEventResult, filter
from astrbot.api.star import Context, Star, register

try:
    from astrbot.api.star import StarTools
except ImportError:  # pragma: no cover - older AstrBot versions
    StarTools = None

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
    "0.2.0",
)
class LcxqyDynamicAiPlugin(Star):
    _STATE_VERSION = 1
    _SESSION_TTL_SECONDS = 7 * 24 * 60 * 60
    _HISTORY_LIMIT = 12
    _CONFIRM_WORDS = {
        "确认", "确认发布", "确认修改", "发吧", "发布吧", "提交吧", "继续", "可以", "好的", "好", "行",
    }
    _CANCEL_WORDS = {
        "取消", "算了", "取消发布", "取消修改", "不发了", "不改了", "不用了", "别发了", "别改了",
    }
    _BIND_DONE_WORDS = {"好了", "绑定好了", "已经绑定", "已绑定", "我绑定好了", "登录好了"}
    _PROFILE_FIELDS = {
        "昵称": "screenName",
        "名字": "screenName",
        "简介": "introduce",
        "头像": "avatar",
        "校区": "campusId",
        "入学年份": "gradeId",
        "年级": "gradeId",
        "screenName": "screenName",
        "introduce": "introduce",
        "avatar": "avatar",
        "campusId": "campusId",
        "gradeId": "gradeId",
    }
    _COMMAND_NAMES = (
        "动态助手", "绑定论坛", "绑定", "bind", "我的状态", "积分", "状态", "签到状态", "签到", "sign",
        "signin", "发动态", "动态", "修改资料", "绑定本群同步",
    )

    def __init__(self, context: Context, config: AstrBotConfig = None):
        super().__init__(context)
        self.config = config or {}
        self._sessions: Dict[str, Dict[str, Any]] = {}
        self._state_path = self._resolve_state_path()
        self._sync_task: Optional[asyncio.Task] = None
        self._load_state()

    async def initialize(self):
        self._load_state()
        if self._cfg_bool("sync_enabled", True):
            self._sync_task = asyncio.create_task(self._sync_loop())

    async def terminate(self):
        self._save_state()
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
            "我是聊一下动态助手。你可以直接对我说：\n"
            "帮我发个动态\n"
            "签到顺便看看积分\n"
            "把昵称改成小明\n"
            "绑定论坛账号\n"
            "也兼容 /发动态、/签到、/我的状态 等命令。发布动态和修改资料前，我会先让你确认。"
        )

    @filter.command("绑定论坛", alias={"绑定", "bind"})
    async def bind_forum(self, event: AstrMessageEvent):
        event.stop_event()
        session = self._session(event)
        session["stage"] = "awaiting_binding"
        session["resume_stage"] = "idle"
        self._touch_session(session)
        yield event.plain_result(await self._bind_hint(event))

    @filter.command("我的状态", alias={"积分", "状态", "签到状态"})
    async def my_status(self, event: AstrMessageEvent):
        event.stop_event()
        yield event.plain_result(await self._status_text(event, preserve_action=True))

    @filter.command("签到", alias={"sign", "signin"})
    async def signin(self, event: AstrMessageEvent):
        event.stop_event()
        yield event.plain_result(await self._signin_text(event, preserve_action=True))

    @filter.command("发动态", alias={"动态"})
    async def prepare_space(self, event: AstrMessageEvent):
        event.stop_event()
        text = self._command_tail(event, ("发动态", "动态")).strip()
        yield event.plain_result(self._prepare_space(event, text, self._images_from_event(event)))

    @filter.command("修改资料")
    async def prepare_profile(self, event: AstrMessageEvent):
        event.stop_event()
        tail = self._command_tail(event, ("修改资料",)).strip()
        field, value = self._parse_profile_update(tail)
        yield event.plain_result(self._prepare_profile(event, field, value))

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
            })
            yield event.plain_result("本群已加入动态同步。后台只需维护群号、群名、开关和摘要设置。")
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
        images = self._images_from_event(event)
        if not text and not images:
            return
        session = self._session(event)
        normalized = self._normalize_phrase(text)

        if normalized in self._CANCEL_WORDS:
            event.stop_event()
            had_action = bool(session.get("pending") or session.get("stage") != "idle")
            self._clear_action(session)
            yield event.plain_result("已取消。" if had_action else "目前没有需要取消的操作。")
            return

        if session.get("stage") == "awaiting_binding" and (
                normalized in self._BIND_DONE_WORDS or normalized in self._CONFIRM_WORDS):
            event.stop_event()
            yield event.plain_result(await self._resume_after_binding(event, session))
            return

        if normalized in self._CONFIRM_WORDS and session.get("pending"):
            event.stop_event()
            async for item in self._confirm(event):
                yield item
            return

        if session.get("stage") == "awaiting_space_content":
            event.stop_event()
            yield event.plain_result(self._prepare_space(event, text, images))
            return

        if session.get("stage") == "awaiting_profile_value":
            event.stop_event()
            field = str(session.get("profile_field") or "")
            if not field:
                selected = self._PROFILE_FIELDS.get(text)
                if selected:
                    session["profile_field"] = selected
                    self._touch_session(session)
                    yield event.plain_result(f"请告诉我新的{self._profile_label(selected)}。")
                    return
            yield event.plain_result(self._prepare_profile(event, field, text))
            return

        if self._is_command_text(text):
            return
        if text.startswith(("/", "／", "!", "！")):
            return

        deterministic = await self._deterministic_intent(event, text, images)
        if deterministic is not None:
            event.stop_event()
            yield event.plain_result(deterministic)
            return

        if not self._cfg_bool("chat_enabled", True):
            return
        if self._group_id(event) and not self._cfg_bool("chat_in_groups", False):
            return

        event.stop_event()
        try:
            plan = await self._plan(event, session, text)
            reply = await self._execute_plan(event, session, plan, images)
            if reply:
                yield event.plain_result(reply)
        except BackendError as error:
            logger.warning("lcxqy_dynamic_ai planner failed: %s", error.message)

    async def _deterministic_intent(self, event: AstrMessageEvent, text: str,
                                    images: List[str]) -> Optional[str]:
        compact = re.sub(r"\s+", "", text)
        has_signin = "签到" in compact
        has_status = any(word in compact for word in ("积分", "签到状态", "我的状态", "多少经验", "多少余额"))
        if has_signin and has_status:
            first = await self._signin_text(event, preserve_action=True)
            if "绑定" in first:
                return first
            second = await self._status_text(event, preserve_action=False)
            return first + "\n" + second
        if has_signin and len(compact) <= 16:
            return await self._signin_text(event, preserve_action=True)
        if has_status and len(compact) <= 20:
            return await self._status_text(event, preserve_action=True)
        if any(word in compact for word in ("绑定论坛", "绑定账号", "登录论坛")):
            session = self._session(event)
            session["stage"] = "awaiting_binding"
            session["resume_stage"] = "idle"
            self._touch_session(session)
            return await self._bind_hint(event)

        field, value = self._natural_profile_update(text)
        if field:
            return self._prepare_profile(event, field, value)
        if self._looks_like_space_request(text):
            content = self._natural_space_content(text)
            return self._prepare_space(event, content, images)
        return None

    async def _plan(self, event: AstrMessageEvent, session: Dict[str, Any], text: str) -> Dict[str, str]:
        system = (
            "你是聊一下校园论坛的 QQ 助手意图规划器。动态是唯一核心内容，严禁建议帖子、文章或发帖。"
            "只输出一个 JSON 对象，不要 Markdown："
            '{"intent":"chat|add_space|signin|status|bind|update_profile|confirm|cancel",'
            '"text":"动态内容","field":"昵称|简介|头像|校区|入学年份","value":"字段值","reply":"聊天回复"}。'
            "论坛操作只识别意图，不得声称已经执行。普通聊天放在 reply。用户说发帖也按发动态处理。"
            "缺少动态内容时 add_space 的 text 为空；缺少资料值时 value 为空。"
        )
        messages: List[Dict[str, str]] = [{"role": "system", "content": system}]
        for item in session.get("history") or []:
            if item.get("role") in ("user", "assistant") and item.get("content"):
                messages.append({"role": item["role"], "content": str(item["content"])[:1000]})
        messages.append({"role": "user", "content": text})
        data = await self._api("/SFreeBot/chat", {
            "qqUserId": self._sender_id(event),
            "messages": json.dumps(messages, ensure_ascii=False),
        })
        raw = str(data.get("content") or "").strip()
        plan = self._parse_plan(raw)
        self._remember(session, "user", text)
        if plan.get("intent") == "chat" and plan.get("reply"):
            self._remember(session, "assistant", plan["reply"])
        return plan

    async def _execute_plan(self, event: AstrMessageEvent, session: Dict[str, Any],
                            plan: Dict[str, str], images: List[str]) -> str:
        intent = plan.get("intent", "chat")
        if intent == "add_space":
            return self._prepare_space(event, plan.get("text", ""), images)
        if intent == "update_profile":
            field = self._PROFILE_FIELDS.get(plan.get("field", ""), "")
            return self._prepare_profile(event, field, plan.get("value", ""))
        if intent == "signin":
            return await self._signin_text(event, preserve_action=True)
        if intent == "status":
            return await self._status_text(event, preserve_action=True)
        if intent == "bind":
            session["stage"] = "awaiting_binding"
            session["resume_stage"] = "idle"
            self._touch_session(session)
            return await self._bind_hint(event)
        if intent == "confirm":
            if session.get("pending"):
                return await self._confirm_text(event)
            return "目前没有待确认操作。"
        if intent == "cancel":
            self._clear_action(session)
            return "已取消。"
        return plan.get("reply") or "我在。你可以和我聊天，也可以让我发动态、签到、查积分或修改资料。"

    def _prepare_space(self, event: AstrMessageEvent, text: str, images: List[str]) -> str:
        session = self._session(event)
        text = (text or "").strip()
        if not text and not images:
            session["stage"] = "awaiting_space_content"
            session["pending"] = None
            self._touch_session(session)
            return "想发什么内容？可以直接发文字，也可以带图片。"
        if len(text) > 1500:
            session["stage"] = "awaiting_space_content"
            session["pending"] = None
            self._touch_session(session)
            return f"动态内容最多 1500 字，当前有 {len(text)} 字。请精简后重新发送。"
        session["pending"] = {
            "type": "addSpace",
            "payload": {
                "qqUserId": self._sender_id(event),
                "requestId": self._request_id(event, "space"),
                "text": text,
                "pic": ",".join(images),
                "onlyMe": "0",
            },
        }
        session["stage"] = "confirm_add_space"
        preview = text if len(text) <= 180 else text[:180] + "..."
        image_note = f"\n图片：{len(images)} 张" if images else ""
        self._touch_session(session)
        return f"动态预览：\n{preview or '（仅图片）'}{image_note}\n现在发布吗？回复“发吧”或“取消”。"

    def _prepare_profile(self, event: AstrMessageEvent, field: Optional[str], value: Optional[str]) -> str:
        session = self._session(event)
        normalized_field = self._PROFILE_FIELDS.get(str(field or ""), str(field or ""))
        if normalized_field not in set(self._PROFILE_FIELDS.values()):
            session["stage"] = "awaiting_profile_value"
            session["profile_field"] = ""
            session["pending"] = None
            self._touch_session(session)
            return "想修改哪项资料？目前支持昵称、简介、头像、校区和入学年份。"
        value = (value or "").strip()
        if not value:
            session["stage"] = "awaiting_profile_value"
            session["profile_field"] = normalized_field
            session["pending"] = None
            self._touch_session(session)
            return f"请告诉我新的{self._profile_label(normalized_field)}。"
        if normalized_field == "screenName" and len(value) > 32:
            session["stage"] = "awaiting_profile_value"
            session["profile_field"] = normalized_field
            self._touch_session(session)
            return "昵称最多 32 个字符，请换一个更短的昵称。"
        if normalized_field == "introduce" and len(value) > 255:
            session["stage"] = "awaiting_profile_value"
            session["profile_field"] = normalized_field
            self._touch_session(session)
            return "简介最多 255 个字符，请精简后重新发送。"
        if normalized_field in ("campusId", "gradeId") and not value.isdigit():
            session["stage"] = "awaiting_profile_value"
            session["profile_field"] = normalized_field
            self._touch_session(session)
            return f"{self._profile_label(normalized_field)}需要填写后台对应的数字 ID。"
        session["pending"] = {
            "type": "updateProfile",
            "payload": {
                "qqUserId": self._sender_id(event),
                "requestId": self._request_id(event, "profile"),
                normalized_field: value,
            },
        }
        session["stage"] = "confirm_update_profile"
        session.pop("profile_field", None)
        self._touch_session(session)
        return f"资料修改预览：{self._profile_label(normalized_field)} = {value}\n确认修改吗？回复“确认”或“取消”。"

    async def _confirm(self, event: AstrMessageEvent) -> Iterable[MessageEventResult]:
        yield event.plain_result(await self._confirm_text(event))

    async def _confirm_text(self, event: AstrMessageEvent) -> str:
        session = self._session(event)
        pending = session.get("pending") or {}
        action_type = pending.get("type")
        if action_type not in ("addSpace", "updateProfile", "signin", "status"):
            return "目前没有待确认操作。"
        try:
            if action_type == "addSpace":
                data = await self._api("/SFreeBot/addSpace", pending["payload"])
                msg = data.get("msg") or ("动态已提交审核" if data.get("pending") else "动态已发布")
                url = data.get("h5Url")
                self._clear_action(session)
                return f"{msg}\n{url}" if url else msg
            elif action_type == "updateProfile":
                await self._api("/SFreeBot/updateProfile", pending["payload"])
                self._clear_action(session)
                return "资料已修改。"
            elif action_type == "signin":
                self._clear_action(session)
                return await self._signin_text(event, preserve_action=True)
            else:
                self._clear_action(session)
                return await self._status_text(event, preserve_action=True)
        except BackendError as error:
            if self._is_unbound_error(error):
                session["stage"] = "awaiting_binding"
                session["resume_stage"] = self._stage_for_action(action_type)
                self._touch_session(session)
                return await self._bind_hint(event, "刚才的操作已经保留。")
            self._touch_session(session)
            return error.message

    async def _resume_after_binding(self, event: AstrMessageEvent, session: Dict[str, Any]) -> str:
        try:
            data = await self._api("/SFreeBot/meStatus", {"qqUserId": self._sender_id(event)})
        except BackendError as error:
            return error.message
        if not data.get("bound"):
            return await self._bind_hint(event, "还没有检测到绑定成功，请完成登录后再回复“好了”。")
        pending = session.get("pending") or {}
        action_type = pending.get("type")
        if action_type:
            session["stage"] = self._stage_for_action(action_type)
            session["resume_stage"] = "idle"
            self._touch_session(session)
            questions = {
                "addSpace": "绑定成功，继续发布刚才的动态吗？",
                "updateProfile": "绑定成功，继续提交刚才的资料修改吗？",
                "signin": "绑定成功，继续签到吗？",
                "status": "绑定成功，继续查询状态吗？",
            }
            question = questions.get(action_type, "绑定成功，继续刚才的操作吗？")
            return question + "回复“继续”或“取消”。"
        self._clear_action(session)
        user = data.get("user") or {}
        return f"绑定成功，当前论坛账号：{user.get('screenName') or user.get('name') or '未命名'}。"

    async def _signin_text(self, event: AstrMessageEvent, preserve_action: bool) -> str:
        try:
            data = await self._api("/SFreeBot/signin", {
                "qqUserId": self._sender_id(event),
                "requestId": self._request_id(event, "signin"),
            })
            return "签到成功：连续 {continuous} 天，获得余额 {assets}，经验 {experience}。".format(
                continuous=data.get("continuous", 0),
                assets=data.get("assets", 0),
                experience=data.get("experience", 0),
            )
        except BackendError as error:
            if preserve_action and self._is_unbound_error(error):
                session = self._session(event)
                session["pending"] = {"type": "signin", "payload": {}}
                session["stage"] = "awaiting_binding"
                session["resume_stage"] = "confirm_signin"
                self._touch_session(session)
                return await self._bind_hint(event, "绑定后可以继续签到。")
            return error.message

    async def _status_text(self, event: AstrMessageEvent, preserve_action: bool) -> str:
        try:
            data = await self._api("/SFreeBot/meStatus", {"qqUserId": self._sender_id(event)})
            if not data.get("bound"):
                if preserve_action:
                    session = self._session(event)
                    session["pending"] = {"type": "status", "payload": {}}
                    session["stage"] = "awaiting_binding"
                    session["resume_stage"] = "confirm_status"
                    self._touch_session(session)
                return await self._bind_hint(event, "绑定后可以继续查询积分和签到状态。")
            user = data.get("user") or {}
            signin = data.get("signin") or {}
            return "当前论坛账号：{name}\n积分：{points}\n经验：{experience}\n余额：{assets}\n连续签到：{streak} 天".format(
                name=user.get("screenName") or user.get("name") or "未命名",
                points=user.get("points", 0),
                experience=user.get("experience", 0),
                assets=user.get("assets", 0),
                streak=signin.get("leiji", 0),
            )
        except BackendError as error:
            return error.message

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
        if not group_id:
            return
        if not origin:
            origin = "lcxqy_onebot:GroupMessage:" + group_id
        after_id = int(group.get("cursorSpaceId") or 0)
        data = await self._api("/SFreeBot/latestSpaces", {
            "groupId": group_id,
            "afterId": str(after_id),
            "limit": str(self._cfg_int("sync_limit", 5)),
        })
        if not data.get("groupEnabled", True):
            return
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
        response_data = parsed.get("data")
        return response_data if isinstance(response_data, dict) else {}

    def _urlopen_text(self, request: urllib.request.Request) -> str:
        try:
            with urllib.request.urlopen(request, timeout=35) as response:
                return response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            raise BackendError(body or str(error)) from error
        except urllib.error.URLError as error:
            raise BackendError(str(error)) from error

    async def _bind_hint(self, event: AstrMessageEvent, prefix: str = "") -> str:
        try:
            data = await self._api("/SFreeBot/bindChallenge", {"qqUserId": self._sender_id(event)})
            lead = (prefix.strip() + "\n") if prefix.strip() else ""
            return (
                lead + "请打开下面链接登录论坛账号完成绑定：\n"
                + str(data.get("bindUrl") or "")
                + "\n这个登录不会挤掉其他设备。没有账号请先去论坛注册，完成后回复“好了”。"
            )
        except BackendError as error:
            return "QQ 尚未绑定论坛账号，且绑定链接生成失败：" + error.message

    def _parse_plan(self, raw: str) -> Dict[str, str]:
        candidate = raw.strip()
        if candidate.startswith("```"):
            candidate = re.sub(r"^```(?:json)?\s*", "", candidate, flags=re.IGNORECASE)
            candidate = re.sub(r"\s*```$", "", candidate)
        start = candidate.find("{")
        end = candidate.rfind("}")
        try:
            parsed = json.loads(candidate[start:end + 1]) if start >= 0 and end > start else None
        except (TypeError, ValueError, json.JSONDecodeError):
            parsed = None
        if not isinstance(parsed, dict):
            return {"intent": "chat", "reply": raw[:2000]}
        allowed = {"chat", "add_space", "signin", "status", "bind", "update_profile", "confirm", "cancel"}
        intent = str(parsed.get("intent") or "chat")
        if intent not in allowed:
            intent = "chat"
        result = {"intent": intent}
        for key in ("text", "field", "value", "reply"):
            result[key] = str(parsed.get(key) or "")[:2000]
        return result

    def _natural_profile_update(self, text: str):
        patterns = (
            r"(?:把|将)?\s*(昵称|名字|简介|头像|校区|入学年份|年级)\s*(?:改成|改为|修改为|设为|设置成)\s*(.+)",
            r"修改\s*(昵称|名字|简介|头像|校区|入学年份|年级)\s+(.+)",
        )
        for pattern in patterns:
            match = re.search(pattern, text, re.DOTALL)
            if match:
                return self._PROFILE_FIELDS.get(match.group(1)), match.group(2).strip(" ：:")
        for label in ("昵称", "名字", "简介", "头像", "校区", "入学年份", "年级"):
            if label in text and any(word in text for word in ("修改", "改", "设置")):
                return self._PROFILE_FIELDS[label], ""
        return None, None

    def _looks_like_space_request(self, text: str) -> bool:
        compact = re.sub(r"\s+", "", text)
        return any(word in compact for word in ("发动态", "发个动态", "发一条动态", "发布动态", "发帖子", "发帖"))

    def _natural_space_content(self, text: str) -> str:
        patterns = (
            r"^(?:请|麻烦)?(?:帮我)?(?:发布|发)(?:一条|一则|个)?(?:动态|帖子|帖)\s*[：:，,]?\s*(.*)$",
            r"^(?:我要|我想)(?:发布|发)(?:一条|一个|个)?(?:动态|帖子|帖)\s*[：:，,]?\s*(.*)$",
        )
        for pattern in patterns:
            match = re.match(pattern, text.strip(), re.DOTALL)
            if match:
                return match.group(1).strip()
        return ""

    def _parse_profile_update(self, tail: str):
        parts = tail.split(maxsplit=1)
        if not parts:
            return None, None
        field = self._PROFILE_FIELDS.get(parts[0])
        value = parts[1].strip() if len(parts) == 2 else ""
        return field, value

    def _profile_label(self, field: str) -> str:
        labels = {
            "screenName": "昵称", "introduce": "简介", "avatar": "头像", "campusId": "校区ID", "gradeId": "入学年份ID",
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
        return any(normalized == name or normalized.startswith(name + " ") for name in self._COMMAND_NAMES)

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

    def _pending_key(self, event: AstrMessageEvent) -> str:
        return f"{self._group_id(event)}:{self._sender_id(event)}"

    def _request_id(self, event: AstrMessageEvent, action: str) -> str:
        message_id = str(getattr(event.message_obj, "message_id", "") or int(time.time() * 1000))
        return f"qqbot-{action}-{self._sender_id(event)}-{message_id}"

    def _session(self, event: AstrMessageEvent) -> Dict[str, Any]:
        if not hasattr(self, "_sessions"):
            self._sessions = {}
        key = self._pending_key(event)
        session = self._sessions.get(key)
        if not isinstance(session, dict):
            session = {"stage": "idle", "pending": None, "history": [], "updated": time.time()}
            self._sessions[key] = session
        return session

    def _touch_session(self, session: Dict[str, Any]) -> None:
        session["updated"] = time.time()
        self._save_state()

    def _clear_action(self, session: Dict[str, Any]) -> None:
        session["stage"] = "idle"
        session["pending"] = None
        session.pop("resume_stage", None)
        session.pop("profile_field", None)
        self._touch_session(session)

    def _remember(self, session: Dict[str, Any], role: str, content: str) -> None:
        history = session.setdefault("history", [])
        history.append({"role": role, "content": content[:1000]})
        session["history"] = history[-self._HISTORY_LIMIT:]
        self._touch_session(session)

    def _stage_for_action(self, action_type: str) -> str:
        return {
            "addSpace": "confirm_add_space",
            "updateProfile": "confirm_update_profile",
            "signin": "confirm_signin",
            "status": "confirm_status",
        }.get(action_type, "idle")

    def _is_unbound_error(self, error: BackendError) -> bool:
        return "绑定" in error.message or error.data.get("bound") is False

    def _normalize_phrase(self, text: str) -> str:
        return re.sub(r"[\s，。！？!?、,.]+", "", text or "")

    def _resolve_state_path(self) -> Optional[Path]:
        try:
            if StarTools is not None:
                data_dir = Path(StarTools.get_data_dir("lcxqy_dynamic_ai"))
            else:
                fallback = os.environ.get("ASTRBOT_DATA_DIR", "")
                if not fallback:
                    return None
                data_dir = Path(fallback) / "lcxqy_dynamic_ai"
            data_dir.mkdir(parents=True, exist_ok=True)
            return data_dir / "state.json"
        except Exception:
            logger.warning("lcxqy_dynamic_ai cannot initialize persistent state:\n%s", traceback.format_exc())
            return None

    def _load_state(self) -> None:
        path = getattr(self, "_state_path", None)
        if path is None or not path.exists():
            return
        try:
            parsed = json.loads(path.read_text(encoding="utf-8"))
            sessions = parsed.get("sessions") if isinstance(parsed, dict) else None
            if isinstance(sessions, dict):
                cutoff = time.time() - self._SESSION_TTL_SECONDS
                self._sessions = {
                    str(key): value for key, value in sessions.items()
                    if isinstance(value, dict) and float(value.get("updated", 0)) >= cutoff
                }
        except Exception:
            logger.warning("lcxqy_dynamic_ai state load failed:\n%s", traceback.format_exc())

    def _save_state(self) -> None:
        path = getattr(self, "_state_path", None)
        if path is None:
            return
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            temp_path = path.with_suffix(".tmp")
            temp_path.write_text(json.dumps({
                "version": self._STATE_VERSION,
                "sessions": getattr(self, "_sessions", {}),
            }, ensure_ascii=False, indent=2), encoding="utf-8")
            os.replace(str(temp_path), str(path))
        except Exception:
            logger.warning("lcxqy_dynamic_ai state save failed:\n%s", traceback.format_exc())

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
