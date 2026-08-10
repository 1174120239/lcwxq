import asyncio
import json
import sys
import tempfile
import types
import unittest
from pathlib import Path
from types import SimpleNamespace


def install_astrbot_stubs():
    try:
        __import__("astrbot")
        return
    except ImportError:
        pass

    astrbot = types.ModuleType("astrbot")
    api = types.ModuleType("astrbot.api")
    event_api = types.ModuleType("astrbot.api.event")
    star_api = types.ModuleType("astrbot.api.star")

    class DummyLogger:
        def warning(self, *_args, **_kwargs):
            pass

    class DummyFilter:
        EventMessageType = SimpleNamespace(PRIVATE_MESSAGE="private", GROUP_MESSAGE="group")

        @staticmethod
        def command(*_args, **_kwargs):
            return lambda function: function

        @staticmethod
        def event_message_type(*_args, **_kwargs):
            return lambda function: function

    class DummyMessageChain:
        def __init__(self, items=None):
            self.items = items or []

        def message(self, value):
            self.items.append(value)
            return self

    class DummyStar:
        def __init__(self, context):
            self.context = context

    api.AstrBotConfig = dict
    api.logger = DummyLogger()
    event_api.AstrMessageEvent = object
    event_api.MessageChain = DummyMessageChain
    event_api.MessageEventResult = object
    event_api.filter = DummyFilter()
    star_api.Context = object
    star_api.Star = DummyStar
    star_api.register = lambda *_args, **_kwargs: (lambda cls: cls)

    sys.modules.update({
        "astrbot": astrbot,
        "astrbot.api": api,
        "astrbot.api.event": event_api,
        "astrbot.api.star": star_api,
    })


install_astrbot_stubs()

PLUGIN_DIR = Path(__file__).resolve().parents[1] / "astrbot_plugin_lcxqy_dynamic_ai"
sys.path.insert(0, str(PLUGIN_DIR))

from main import BackendError, LcxqyDynamicAiPlugin  # noqa: E402


class DummyEvent:
    def __init__(self, text, message_id="message-1", group_id="", images=None):
        self.message_str = text
        segments = [SimpleNamespace(data={"url": value}) for value in (images or [])]
        self.message_obj = SimpleNamespace(
            message=segments,
            group_id=group_id,
            group_name="测试群" if group_id else "",
            message_id=message_id,
        )
        self.stopped = False

    def stop_event(self):
        self.stopped = True

    def plain_result(self, text):
        return text

    def get_sender_id(self):
        return "10001"


class LcxqyDynamicAiPluginTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.plugin = self.make_plugin(Path(self.temp_dir.name) / "state.json")

    def make_plugin(self, state_path):
        plugin = object.__new__(LcxqyDynamicAiPlugin)
        plugin.config = {
            "chat_enabled": True,
            "chat_in_groups": False,
        }
        platform_meta = SimpleNamespace(id="001", name="aiocqhttp")
        platform = SimpleNamespace(meta=lambda: platform_meta)
        plugin.context = SimpleNamespace(
            platform_manager=SimpleNamespace(platform_insts=[platform]),
        )
        plugin._sessions = {}
        plugin._state_path = state_path
        plugin._sync_task = None
        return plugin

    def collect(self, event):
        async def run():
            return [item async for item in self.plugin._handle_message(event)]

        return asyncio.run(run())

    def test_natural_space_request_asks_for_content_then_previews(self):
        first = self.collect(DummyEvent("帮我发个动态"))
        second = self.collect(DummyEvent("今天晚霞很好看", message_id="message-2"))

        self.assertIn("想发什么内容", first[0])
        self.assertIn("今天晚霞很好看", second[0])
        session = self.plugin._sessions[":10001"]
        self.assertEqual("confirm_add_space", session["stage"])
        self.assertEqual("今天晚霞很好看", session["pending"]["payload"]["text"])

    def test_forum_operation_works_in_group_when_group_chat_is_disabled(self):
        result = self.collect(DummyEvent("帮我发个动态", group_id="638978650"))

        self.assertIn("想发什么内容", result[0])
        self.assertTrue(self.plugin._sessions["638978650:10001"])

    def test_command_handler_stops_default_llm_pipeline(self):
        event = DummyEvent("发动态")

        result = asyncio.run(self._collect_command(self.plugin.prepare_space, event))

        self.assertIn("想发什么内容", result[0])
        self.assertTrue(event.stopped)

    def test_unbound_draft_survives_binding_and_resumes(self):
        bound = False
        calls = []

        async def api(path, payload):
            nonlocal bound
            calls.append(path)
            if path == "/SFreeBot/addSpace":
                if not bound:
                    raise BackendError("QQ 尚未绑定论坛账号", {"bound": False})
                return {"msg": "动态已发布", "h5Url": "https://example.test/space/9"}
            if path == "/SFreeBot/bindChallenge":
                return {"bindUrl": "https://example.test/bind"}
            if path == "/SFreeBot/meStatus":
                return {"bound": bound, "user": {"screenName": "Alice"}}
            raise AssertionError(path)

        self.plugin._api = api
        self.collect(DummyEvent("帮我发个动态"))
        self.collect(DummyEvent("今天晚霞很好看", message_id="message-2"))
        unbound_reply = self.collect(DummyEvent("发吧", message_id="message-3"))[0]

        session = self.plugin._sessions[":10001"]
        self.assertIn("刚才的操作已经保留", unbound_reply)
        self.assertEqual("awaiting_binding", session["stage"])
        self.assertEqual("今天晚霞很好看", session["pending"]["payload"]["text"])

        bound = True
        resumed = self.collect(DummyEvent("好了", message_id="message-4"))[0]
        published = self.collect(DummyEvent("继续", message_id="message-5"))[0]

        self.assertIn("继续发布刚才的动态", resumed)
        self.assertIn("动态已发布", published)
        self.assertIsNone(session["pending"])
        self.assertEqual("idle", session["stage"])
        self.assertEqual(2, calls.count("/SFreeBot/addSpace"))

    def test_flexible_cancel_clears_draft(self):
        self.collect(DummyEvent("帮我发个动态"))
        self.collect(DummyEvent("准备取消的内容", message_id="message-2"))

        result = self.collect(DummyEvent("算了", message_id="message-3"))

        self.assertEqual(["已取消。"], result)
        self.assertIsNone(self.plugin._sessions[":10001"]["pending"])

    def test_profile_follow_up_accepts_field_then_value(self):
        first = asyncio.run(self._collect_command(self.plugin.prepare_profile, DummyEvent("修改资料")))
        second = self.collect(DummyEvent("昵称", message_id="message-2"))
        third = self.collect(DummyEvent("小明", message_id="message-3"))

        self.assertIn("想修改哪项资料", first[0])
        self.assertIn("新的昵称", second[0])
        self.assertIn("昵称 = 小明", third[0])

    def test_profile_and_space_values_are_validated_before_confirmation(self):
        long_space = self.plugin._prepare_space(DummyEvent(""), "a" * 1501, [])
        invalid_campus = self.plugin._prepare_profile(DummyEvent(""), "campusId", "主校区")

        self.assertIn("最多 1500 字", long_space)
        self.assertIn("数字 ID", invalid_campus)
        self.assertEqual("awaiting_profile_value", self.plugin._sessions[":10001"]["stage"])

    def test_compound_signin_and_status(self):
        async def api(path, _payload):
            if path == "/SFreeBot/signin":
                return {"continuous": 3, "assets": 1, "experience": 2}
            if path == "/SFreeBot/meStatus":
                return {
                    "bound": True,
                    "user": {"screenName": "Alice", "points": 8, "experience": 9, "assets": 10},
                    "signin": {"leiji": 3},
                }
            raise AssertionError(path)

        self.plugin._api = api
        result = self.collect(DummyEvent("签到顺便看看积分"))[0]

        self.assertIn("签到成功", result)
        self.assertIn("积分：8", result)

    def test_state_persists_and_reloads(self):
        state_path = Path(self.temp_dir.name) / "persistent-state.json"
        plugin = self.make_plugin(state_path)
        plugin._prepare_space(DummyEvent(""), "重启后也要保留", [])

        reloaded = self.make_plugin(state_path)
        reloaded._load_state()

        session = reloaded._sessions[":10001"]
        self.assertEqual("confirm_add_space", session["stage"])
        self.assertEqual("重启后也要保留", session["pending"]["payload"]["text"])

    def test_planner_history_is_sent_on_next_turn(self):
        payloads = []

        async def api(path, payload):
            self.assertEqual("/SFreeBot/chat", path)
            payloads.append(json.loads(payload["messages"]))
            return {"content": json.dumps({
                "intent": "chat",
                "text": "",
                "field": "",
                "value": "",
                "reply": "记得",
            }, ensure_ascii=False)}

        self.plugin._api = api
        self.collect(DummyEvent("今天天气怎么样"))
        self.collect(DummyEvent("你还记得我刚才问的吗", message_id="message-2"))

        second_messages = payloads[1]
        self.assertTrue(any(item["role"] == "assistant" and item["content"] == "记得"
                            for item in second_messages))

    def test_malformed_planner_json_falls_back_to_chat(self):
        plan = self.plugin._parse_plan("这是普通回复，不是 JSON")

        self.assertEqual("chat", plan["intent"])
        self.assertEqual("这是普通回复，不是 JSON", plan["reply"])

    def test_chat_prompt_uses_safe_xiaoying_forum_persona(self):
        prompt = self.plugin._CHAT_SYSTEM_PROMPT

        self.assertIn("你是小樱", prompt)
        self.assertIn("一到三句", prompt)
        self.assertIn("动态是论坛唯一核心内容", prompt)
        self.assertIn("不输出露骨", prompt)
        self.assertIn("先直接回答用户的问题", prompt)
        self.assertIn("当用户问你是谁时", prompt)

    def test_chat_reply_is_limited_to_three_short_sections(self):
        plan = self.plugin._parse_plan(json.dumps({
            "intent": "chat",
            "reply": "第一句。第二句！第三句？第四句不会保留。",
        }, ensure_ascii=False))

        self.assertEqual("第一句。\n第二句！\n第三句？", plan["reply"])

    def test_chat_reply_has_hard_length_limit(self):
        plan = self.plugin._parse_plan(json.dumps({
            "intent": "chat",
            "reply": "很长" * 120,
        }, ensure_ascii=False))

        self.assertLessEqual(len(plan["reply"]), 181)
        self.assertTrue(plan["reply"].endswith("…"))

    def test_identity_question_uses_direct_short_forum_reply(self):
        async def unexpected_api(*_args, **_kwargs):
            raise AssertionError("identity reply should not call DeepSeek")

        self.plugin._api = unexpected_api
        result = self.collect(DummyEvent("你好，你是谁？简单说。"))

        self.assertEqual(["我是小樱，聊一下论坛的动态助手。\n聊天、发动态、签到这些都可以找我喵。"], result)

    def test_simple_greeting_uses_direct_short_reply(self):
        result = self.collect(DummyEvent("你好"))

        self.assertEqual(["在呢。\n我是小樱，有事直接说喵。"], result)

    def test_initial_sync_delivers_only_latest_space_with_active_platform_origin(self):
        captured = []

        async def api(path, payload):
            self.assertEqual("/SFreeBot/latestSpaces", path)
            self.assertEqual("0", payload["afterId"])
            return {"groupEnabled": False, "spaces": [{"id": 1}, {"id": 2}]}

        async def deliver(origin, group_id, space):
            captured.append((origin, group_id, space))

        self.plugin._api = api
        self.plugin._deliver_space = deliver
        asyncio.run(self.plugin._sync_group({
            "groupId": "638978650",
            "unifiedMsgOrigin": "",
            "cursorSpaceId": 0,
        }))

        self.assertEqual([("001:GroupMessage:638978650", "638978650", 2)], [
            (origin, group_id, space["id"]) for origin, group_id, space in captured
        ])

    def test_legacy_sync_origin_is_replaced_with_active_platform(self):
        origin = self.plugin._group_origin(
            "lcxqy_onebot:GroupMessage:638978650", "638978650")

        self.assertEqual("001:GroupMessage:638978650", origin)

    def test_sync_origin_with_wrong_group_is_rebuilt(self):
        origin = self.plugin._group_origin(
            "001:GroupMessage:123456", "638978650")

        self.assertEqual("001:GroupMessage:638978650", origin)

    def test_delivery_failure_does_not_advance_cursor(self):
        calls = []

        async def send_message(_origin, _chain):
            return False

        async def api(path, payload):
            calls.append((path, payload))
            return {"recorded": True}

        self.plugin.context.send_message = send_message
        self.plugin._api = api
        asyncio.run(self.plugin._deliver_space(
            "missing:GroupMessage:638978650", "638978650", {"id": 9, "text": "新动态"}))

        self.assertEqual("/SFreeBot/delivery", calls[0][0])
        self.assertEqual("error", calls[0][1]["status"])

    async def _collect_command(self, handler, event):
        return [item async for item in handler(event)]


if __name__ == "__main__":
    unittest.main()
