import asyncio
import base64
import io
import json
import sys
import tempfile
import time
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
        segments = [SimpleNamespace(data=value if isinstance(value, dict) else {"url": value})
                    for value in (images or [])]
        self.message_obj = SimpleNamespace(
            message=segments,
            group_id=group_id,
            group_name="测试群" if group_id else "",
            message_id=message_id,
            self_id="987654321",
        )
        self.stopped = False

    def stop_event(self):
        self.stopped = True

    def plain_result(self, text):
        return text

    def get_sender_id(self):
        return "10001"

    def get_self_id(self):
        return self.message_obj.self_id

    def get_messages(self):
        return self.message_obj.message


def quoted_group_event(text, quoted_text, sender_id="987654321", message_id="message-1"):
    event = DummyEvent(text, message_id=message_id, group_id="638978650")
    event.message_obj.message.insert(0, SimpleNamespace(
        sender_id=sender_id,
        message_str=quoted_text,
        text=quoted_text,
        chain=[SimpleNamespace(text=quoted_text)],
    ))
    return event


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
        plugin._comment_space_supported = True
        plugin._group_chat_enabled = True
        plugin._remote_config_refreshed_at = float("inf")
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

    def test_space_draft_accepts_text_then_image_in_separate_messages(self):
        self.collect(DummyEvent("帮我发个动态"))
        text_preview = self.collect(DummyEvent("今天晚霞很好看", message_id="message-2"))[0]
        session = self.plugin._sessions[":10001"]
        request_id = session["pending"]["payload"]["requestId"]

        image_preview = self.collect(DummyEvent(
            "", message_id="message-3", images=[{"file": "napcat-image-1"}]))[0]

        payload = session["pending"]["payload"]
        self.assertIn("继续发送文字或图片", text_preview)
        self.assertIn("图片：1 张", image_preview)
        self.assertEqual("今天晚霞很好看", payload["text"])
        self.assertEqual([{"file": "napcat-image-1"}], payload["_imageSources"])
        self.assertEqual(request_id, payload["requestId"])

    def test_space_draft_accepts_image_then_text_in_separate_messages(self):
        self.collect(DummyEvent("帮我发个动态"))
        self.collect(DummyEvent(
            "", message_id="message-2", images=[{"file": "napcat-image-1"}]))

        preview = self.collect(DummyEvent("配上这句文字", message_id="message-3"))[0]
        payload = self.plugin._sessions[":10001"]["pending"]["payload"]

        self.assertIn("配上这句文字", preview)
        self.assertIn("图片：1 张", preview)
        self.assertEqual("配上这句文字", payload["text"])
        self.assertEqual(1, len(payload["_imageSources"]))

    def test_space_draft_appends_multiple_text_messages(self):
        self.collect(DummyEvent("帮我发个动态"))
        self.collect(DummyEvent("第一段", message_id="message-2"))

        self.collect(DummyEvent("第二段", message_id="message-3"))

        payload = self.plugin._sessions[":10001"]["pending"]["payload"]
        self.assertEqual("第一段\n第二段", payload["text"])

    def test_space_draft_rejects_tenth_image_without_losing_existing_draft(self):
        first_nine = [{"file": f"image-{index}"} for index in range(9)]
        self.plugin._prepare_space(DummyEvent(""), "九张图片", first_nine)

        result = self.collect(DummyEvent(
            "", message_id="message-2", images=[{"file": "image-10"}]))[0]
        payload = self.plugin._sessions[":10001"]["pending"]["payload"]

        self.assertIn("最多 9 张", result)
        self.assertEqual("九张图片", payload["text"])
        self.assertEqual(9, len(payload["_imageSources"]))

    def test_image_segment_keeps_url_file_and_path_for_napcat_fallback(self):
        event = DummyEvent("发动态", images=[{
            "url": "https://multimedia.nt.qq.com.cn/temp.jpg",
            "file": "napcat-file-id",
            "path": "C:/temp/napcat.jpg",
        }])

        sources = self.plugin._images_from_event(event)

        self.assertEqual([{
            "url": "https://multimedia.nt.qq.com.cn/temp.jpg",
            "file": "napcat-file-id",
            "path": "C:/temp/napcat.jpg",
        }], sources)

    def test_confirm_image_space_uses_multipart_images(self):
        image_path = Path(self.temp_dir.name) / "qq.png"
        image_path.write_bytes(b"\x89PNG\r\n\x1a\nimage-data")
        calls = []

        async def multipart(path, payload, files):
            calls.append((path, payload, files))
            return {"msg": "动态已发布", "h5Url": "https://example.test/space/8"}

        self.plugin._api_multipart = multipart
        self.plugin._prepare_space(
            DummyEvent(""), "带图动态", [{"path": str(image_path), "file": "napcat-id"}])

        result = self.collect(DummyEvent("发吧", message_id="message-2"))

        self.assertIn("动态已发布", result[0])
        self.assertEqual("/SFreeBot/addSpace", calls[0][0])
        self.assertEqual(1, len(calls[0][2]))
        self.assertEqual("image/png", calls[0][2][0]["content_type"])
        self.assertTrue(calls[0][2][0]["content"].startswith(b"\x89PNG"))

    def test_image_read_failure_keeps_pending_space(self):
        called = False

        async def multipart(*_args):
            nonlocal called
            called = True
            return {}

        self.plugin._api_multipart = multipart
        self.plugin._prepare_space(
            DummyEvent(""), "图片稍后重试", [{"file": "missing-napcat-file"}])

        result = self.collect(DummyEvent("发吧", message_id="message-2"))
        session = self.plugin._sessions[":10001"]

        self.assertIn("图片上传失败", result[0])
        self.assertFalse(called)
        self.assertEqual("confirm_add_space", session["stage"])
        self.assertEqual("图片稍后重试", session["pending"]["payload"]["text"])

    def test_space_draft_persists_temporary_image_until_confirm(self):
        temporary_path = Path(self.temp_dir.name) / "astrbot-temp.jpg"
        temporary_path.write_bytes(b"\xff\xd8\xfftemporary-image")
        calls = []

        async def multipart(path, payload, files):
            calls.append((path, payload, files))
            return {"msg": "动态已发布"}

        self.plugin._api_multipart = multipart
        self.collect(DummyEvent("帮我发个动态"))
        preview = self.collect(DummyEvent(
            "", message_id="message-2", images=[{"path": str(temporary_path)}]))[0]
        payload = self.plugin._sessions[":10001"]["pending"]["payload"]
        persisted_path = Path(payload["_imageSources"][0]["path"])

        self.assertIn("图片：1 张", preview)
        self.assertNotEqual(temporary_path, persisted_path)
        self.assertTrue(persisted_path.is_file())

        temporary_path.unlink()
        result = self.collect(DummyEvent("发吧", message_id="message-3"))

        self.assertIn("动态已发布", result[0])
        self.assertEqual(1, len(calls[0][2]))
        self.assertFalse(persisted_path.exists())

    def test_resending_image_discards_expired_temporary_source(self):
        expired_path = Path(self.temp_dir.name) / "expired.jpg"
        self.plugin._prepare_space(
            DummyEvent(""), "旧草稿", [{"path": str(expired_path), "file": str(expired_path)}])
        replacement_path = Path(self.temp_dir.name) / "replacement.png"
        replacement_path.write_bytes(b"\x89PNG\r\n\x1a\nreplacement")

        preview = self.collect(DummyEvent(
            "", message_id="message-2", images=[{"path": str(replacement_path)}]))[0]
        sources = self.plugin._sessions[":10001"]["pending"]["payload"]["_imageSources"]

        self.assertIn("图片：1 张", preview)
        self.assertEqual(1, len(sources))
        self.assertTrue(Path(sources[0]["path"]).is_file())

    def test_napcat_get_image_falls_back_to_local_file(self):
        image_path = Path(self.temp_dir.name) / "napcat.jpg"
        image_path.write_bytes(b"\xff\xd8\xffimage-data")
        actions = []

        async def call_action(action, **payload):
            actions.append((action, payload))
            return {"data": {"file": str(image_path)}}

        platform_meta = SimpleNamespace(id="001", name="aiocqhttp")
        platform = SimpleNamespace(
            meta=lambda: platform_meta,
            bot=SimpleNamespace(call_action=call_action),
        )
        self.plugin.context.platform_manager.platform_insts = [platform]

        part = asyncio.run(self.plugin._read_image_for_upload(
            {"file": "napcat-file-id"}, 1))

        self.assertEqual("get_image", actions[0][0])
        self.assertEqual("napcat-file-id", actions[0][1]["file"])
        self.assertEqual("image/jpeg", part["content_type"])

    def test_napcat_get_image_prefers_the_current_event_bot(self):
        image_path = Path(self.temp_dir.name) / "event-napcat.png"
        image_path.write_bytes(b"\x89PNG\r\n\x1a\nimage-data")
        actions = []

        async def call_action(action, **payload):
            actions.append((action, payload))
            return {"file": str(image_path)}

        event = DummyEvent("发吧")
        event.bot = SimpleNamespace(call_action=call_action)
        part = asyncio.run(self.plugin._read_image_for_upload(
            {"file": "napcat-event-file-id"}, 1, event))

        self.assertEqual("get_image", actions[0][0])
        self.assertEqual("napcat-event-file-id", actions[0][1]["file"])
        self.assertEqual("image/png", part["content_type"])

    def test_image_upload_accepts_base64_source(self):
        raw = b"\xff\xd8\xffimage-data"
        encoded = base64.b64encode(raw).decode("ascii")

        part = asyncio.run(self.plugin._read_image_for_upload(
            {"file": "base64://" + encoded}, 1))

        self.assertEqual("image/jpeg", part["content_type"])
        self.assertEqual(raw, part["content"])

    def test_plain_text_space_still_uses_form_api(self):
        calls = []

        async def api(path, payload):
            calls.append((path, payload))
            return {"msg": "动态已发布"}

        self.plugin._api = api
        self.plugin._prepare_space(DummyEvent(""), "纯文字动态", [])

        result = self.collect(DummyEvent("发吧", message_id="message-2"))

        self.assertIn("动态已发布", result[0])
        self.assertEqual(["/SFreeBot/addSpace"], [item[0] for item in calls])

    def test_multipart_request_repeats_images_and_hides_private_sources(self):
        captured = {}
        self.plugin.config.update({
            "backend_base_url": "https://api.example.test",
            "bot_secret": "test-secret",
        })

        def urlopen(request):
            captured["body"] = request.data
            captured["content_type"] = request.headers.get("Content-type")
            return '{"code":1,"data":{"msg":"动态已发布"}}'

        self.plugin._urlopen_text = urlopen
        data = asyncio.run(self.plugin._api_multipart(
            "/SFreeBot/addSpace",
            {"qqUserId": "10001", "_imageSources": [{"file": "private-id"}]},
            [
                {"filename": "one.png", "content_type": "image/png", "content": b"one"},
                {"filename": "two.jpg", "content_type": "image/jpeg", "content": b"two"},
            ],
        ))

        body = captured["body"]
        self.assertEqual(2, body.count(b'name="images"'))
        self.assertIn(b'name="botSecret"', body)
        self.assertNotIn(b"_imageSources", body)
        self.assertNotIn(b"private-id", body)
        self.assertTrue(captured["content_type"].startswith("multipart/form-data; boundary="))
        self.assertEqual("动态已发布", data["msg"])

    def test_forum_operation_works_in_group_when_group_chat_is_disabled(self):
        self.plugin._group_chat_enabled = False
        result = self.collect(DummyEvent("云云，帮我发个动态", group_id="638978650"))

        self.assertIn("想发什么内容", result[0])
        self.assertTrue(self.plugin._sessions["638978650:10001"])

    def test_group_chat_is_ignored_when_disabled_by_backend(self):
        self.plugin._group_chat_enabled = False

        result = self.collect(DummyEvent("云云，你是谁", group_id="638978650"))

        self.assertEqual([], result)

    def test_unaddressed_group_chat_is_ignored(self):
        result = self.collect(DummyEvent("今天天气真不错", group_id="638978650"))

        self.assertEqual([], result)

    def test_group_chat_responds_to_direct_name(self):
        result = self.collect(DummyEvent("云云，你是谁", group_id="638978650"))

        self.assertEqual(["我是云云，聊一下论坛的动态助手。\n聊天、发动态、签到这些都可以找我喵。"], result)

    def test_group_chat_responds_to_self_mention(self):
        event = DummyEvent("你是谁", group_id="638978650")
        event.message_obj.message.insert(0, SimpleNamespace(qq="987654321"))

        result = self.collect(event)

        self.assertEqual(["我是云云，聊一下论坛的动态助手。\n聊天、发动态、签到这些都可以找我喵。"], result)

    def test_group_chat_responds_to_reply_to_yunyun(self):
        event = DummyEvent("你是谁", group_id="638978650")
        event.message_obj.message.insert(0, SimpleNamespace(sender_id="987654321"))

        result = self.collect(event)

        self.assertEqual(["我是云云，聊一下论坛的动态助手。\n聊天、发动态、签到这些都可以找我喵。"], result)

    def test_group_follow_up_continues_pending_operation_without_mention(self):
        self.plugin._group_chat_enabled = False
        first = self.collect(DummyEvent("云云，帮我发个动态", group_id="638978650"))
        second = self.collect(DummyEvent("今天晚霞很好看", message_id="message-2", group_id="638978650"))

        self.assertIn("想发什么内容", first[0])
        self.assertIn("今天晚霞很好看", second[0])

    def test_reply_to_synced_dynamic_posts_comment_with_bound_forum_account(self):
        calls = []
        self.plugin._group_chat_enabled = False

        async def api(path, payload):
            calls.append((path, payload))
            return {"msg": "评论已发布", "spaceId": 91}

        self.plugin._api = api
        event = quoted_group_event(
            "晚霞确实很好看",
            "论坛有新动态\nAlice：操场晚霞\nhttps://prev.lcxqy.cn/#/pages/space/info?id=88",
        )

        result = self.collect(event)

        self.assertEqual(["评论已发布"], result)
        self.assertTrue(event.stopped)
        self.assertEqual("/SFreeBot/addSpace", calls[0][0])
        self.assertEqual("3", calls[0][1]["type"])
        self.assertEqual("88", calls[0][1]["toid"])
        self.assertEqual("晚霞确实很好看", calls[0][1]["text"])
        self.assertEqual("10001", calls[0][1]["qqUserId"])

    def test_reply_to_other_group_member_is_not_treated_as_forum_comment(self):
        async def unexpected_api(*_args, **_kwargs):
            raise AssertionError("other member reply must not call backend")

        self.plugin._api = unexpected_api
        event = quoted_group_event(
            "我来评论",
            "https://prev.lcxqy.cn/#/pages/space/info?id=88",
            sender_id="123456",
        )

        self.assertEqual([], self.collect(event))

    def test_reply_to_yunyun_chat_without_dynamic_link_stays_chat(self):
        calls = []

        async def api(path, _payload):
            calls.append(path)
            raise AssertionError("short greeting should not call backend")

        self.plugin._api = api
        event = quoted_group_event("你好", "我是云云，有事直接说。")

        result = self.collect(event)

        self.assertEqual(["在呢。\n我是云云，有事直接说喵。"], result)
        self.assertEqual([], calls)

    def test_empty_reply_to_synced_dynamic_asks_for_comment_and_accepts_follow_up(self):
        calls = []

        async def api(path, payload):
            calls.append((path, payload))
            return {"msg": "评论已发布"}

        self.plugin._api = api
        first = self.collect(quoted_group_event(
            "", "https://prev.lcxqy.cn/#/pages/space/info?id=88"))
        second = self.collect(DummyEvent(
            "补上的评论", message_id="message-2", group_id="638978650"))

        self.assertEqual(["想评论什么？"], first)
        self.assertEqual(["评论已发布"], second)
        self.assertEqual("88", calls[0][1]["toid"])
        self.assertEqual("补上的评论", calls[0][1]["text"])

    def test_unbound_quoted_comment_survives_binding_with_same_request_id(self):
        bound = False
        comment_payloads = []

        async def api(path, payload):
            nonlocal bound
            if path == "/SFreeBot/addSpace":
                comment_payloads.append(dict(payload))
                if not bound:
                    raise BackendError("QQ 尚未绑定论坛账号", {"bound": False})
                return {"msg": "评论已发布"}
            if path == "/SFreeBot/bindChallenge":
                return {"bindUrl": "https://example.test/bind"}
            if path == "/SFreeBot/meStatus":
                return {"bound": bound, "user": {"screenName": "Alice"}}
            raise AssertionError(path)

        self.plugin._api = api
        first = self.collect(quoted_group_event(
            "同意这个观点",
            "https://prev.lcxqy.cn/#/pages/space/info?id=88",
            message_id="message-comment",
        ))[0]
        session = self.plugin._sessions["638978650:10001"]

        self.assertIn("刚才的评论已经保留", first)
        self.assertIn("https://example.test/bind", first)
        self.assertEqual("awaiting_binding", session["stage"])
        self.assertEqual("commentSpace", session["pending"]["type"])

        bound = True
        resumed = self.collect(DummyEvent(
            "好了", message_id="message-2", group_id="638978650"))[0]
        posted = self.collect(DummyEvent(
            "继续", message_id="message-3", group_id="638978650"))[0]

        self.assertIn("继续发送刚才的评论", resumed)
        self.assertEqual("评论已发布", posted)
        self.assertEqual(2, len(comment_payloads))
        self.assertEqual(comment_payloads[0]["requestId"], comment_payloads[1]["requestId"])
        self.assertEqual("3", comment_payloads[1]["type"])
        self.assertEqual("88", comment_payloads[1]["toid"])

    def test_quoted_comment_over_limit_is_rejected_before_backend_call(self):
        async def unexpected_api(*_args, **_kwargs):
            raise AssertionError("oversized comment must not call backend")

        self.plugin._api = unexpected_api
        result = self.collect(quoted_group_event(
            "a" * 1501,
            "https://prev.lcxqy.cn/#/pages/space/info?id=88",
        ))

        self.assertIn("评论最多 1500 字", result[0])
        session = self.plugin._sessions["638978650:10001"]
        self.assertEqual("awaiting_comment_content", session["stage"])
        self.assertEqual("", session["pending"]["payload"]["text"])

    def test_old_backend_without_comment_capability_cannot_mispost_as_dynamic(self):
        calls = []

        async def api(path, _payload):
            calls.append(path)
            if path == "/SFreeBot/config":
                return {"dynamicOnly": True}
            raise AssertionError("old backend must not receive comment addSpace")

        self.plugin._comment_space_supported = False
        self.plugin._api = api
        result = self.collect(quoted_group_event(
            "这是一条评论",
            "https://prev.lcxqy.cn/#/pages/space/info?id=88",
        ))

        self.assertIn("后端尚未升级", result[0])
        self.assertEqual(["/SFreeBot/config"], calls)

    def test_group_pure_mention_gets_short_ack(self):
        event = DummyEvent("", group_id="638978650")
        event.message_obj.message.insert(0, SimpleNamespace(qq="987654321"))

        result = self.collect(event)

        self.assertEqual(["我在呢。想聊什么？"], result)

    def test_group_pure_mention_is_ignored_when_disabled_by_backend(self):
        self.plugin._group_chat_enabled = False
        event = DummyEvent("", group_id="638978650")
        event.message_obj.message.insert(0, SimpleNamespace(qq="987654321"))

        result = self.collect(event)

        self.assertEqual([], result)

    def test_group_chat_refreshes_remote_switch(self):
        calls = []

        async def api(path, _payload):
            calls.append(path)
            return {"chatInGroups": False, "commentSpace": True}

        self.plugin._remote_config_refreshed_at = 0.0
        self.plugin._api = api

        result = self.collect(DummyEvent("云云，你是谁", group_id="638978650"))

        self.assertEqual([], result)
        self.assertFalse(self.plugin._group_chat_enabled)
        self.assertEqual(["/SFreeBot/config"], calls)

    def test_old_backend_config_defaults_group_chat_to_enabled(self):
        self.plugin._group_chat_enabled = False
        self.plugin._apply_remote_config({"commentSpace": True})

        self.assertTrue(self.plugin._group_chat_enabled)

    def test_command_handler_stops_default_llm_pipeline(self):
        event = DummyEvent("发动态")

        result = asyncio.run(self._collect_command(self.plugin.prepare_space, event))

        self.assertIn("想发什么内容", result[0])
        self.assertTrue(event.stopped)

    def test_help_uses_yunyun_name(self):
        result = asyncio.run(self._collect_command(self.plugin.help, DummyEvent("动态助手")))

        self.assertIn("我是云云", result[0])

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

    def test_chat_prompt_uses_safe_yunyun_forum_persona(self):
        prompt = self.plugin._CHAT_SYSTEM_PROMPT

        self.assertIn("你是云云", prompt)
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

        self.assertEqual(["我是云云，聊一下论坛的动态助手。\n聊天、发动态、签到这些都可以找我喵。"], result)

    def test_simple_greeting_uses_direct_short_reply(self):
        result = self.collect(DummyEvent("你好"))

        self.assertEqual(["在呢。\n我是云云，有事直接说喵。"], result)

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

    def test_qzone_renderer_returns_one_numbered_png_per_dynamic(self):
        pngs = self.plugin._render_qzone_images({
            "title": "聊一今日动态",
            "subtitle": "校园里今天发生了什么",
            "footer": "更多动态，来聊一看看",
            "includeSourceImages": False,
            "showCampus": True,
            "showTopics": True,
        }, [{
            "id": 9,
            "summary": "今天操场的晚霞很好看，和大家分享一下。",
            "author": {"name": "Alice", "campus": "主校区", "grade": "2025"},
            "topics": [{"name": "校园生活"}],
            "images": [],
        }, {
            "id": 10,
            "summary": "图书馆新到了一批书。",
            "author": {"name": "Bob"},
            "topics": [],
            "images": [],
        }])

        from PIL import Image
        self.assertEqual(2, len(pngs))
        rendered = Image.open(io.BytesIO(pngs[0]))
        self.assertEqual("PNG", rendered.format)
        self.assertEqual(1080, rendered.width)
        self.assertEqual(1350, rendered.height)

    def test_qzone_sync_posts_one_batch_and_reports_success(self):
        calls = []

        async def api(path, payload):
            calls.append((path, dict(payload)))
            if path == "/SFreeBot/qzoneBatch":
                return {
                    "enabled": True,
                    "alreadyPublishedToday": False,
                    "publishNowPending": True,
                    "publishNowToken": "manual-1",
                    "postText": "今日动态",
                    "ugcRight": 1,
                    "includeSourceImages": False,
                    "spaces": [{"id": 31, "summary": "第一条", "author": {"name": "A"}},
                               {"id": 32, "summary": "第二条", "author": {"name": "B"}}],
                }
            if path == "/SFreeBot/qzoneDelivery":
                return {"recorded": True}
            raise AssertionError(path)

        async def send(_settings, pngs, spaces):
            self.assertEqual(2, len(pngs))
            self.assertEqual(2, len(spaces))
            self.assertTrue(all(png.startswith(b"\x89PNG\r\n\x1a\n") for png in pngs))
            return {"status": "ok", "retcode": 0, "data": {"tid": "tid-32"}}

        self.plugin._api = api
        self.plugin._send_qzone_message = send
        self.plugin._qzone_next_check_at = 0
        asyncio.run(self.plugin._sync_qzone({
            "enabled": True, "due": True, "alreadyPublishedToday": False,
        }))

        self.assertEqual("/SFreeBot/qzoneBatch", calls[0][0])
        self.assertEqual("/SFreeBot/qzoneDelivery", calls[1][0])
        self.assertEqual("success", calls[1][1]["status"])
        self.assertEqual("32", calls[1][1]["maxSpaceId"])
        self.assertEqual("tid-32", calls[1][1]["tid"])
        self.assertEqual("manual-1", calls[1][1]["publishNowToken"])

    def test_qzone_sync_skips_empty_batch(self):
        calls = []

        async def api(path, payload):
            calls.append((path, payload))
            return {"enabled": True, "alreadyPublishedToday": False, "spaces": []}

        self.plugin._api = api
        self.plugin._qzone_next_check_at = 0
        asyncio.run(self.plugin._sync_qzone({
            "enabled": True, "due": True, "alreadyPublishedToday": False,
        }))

        self.assertEqual(["/SFreeBot/qzoneBatch"], [item[0] for item in calls])

    def test_qzone_manual_publish_bypasses_local_daily_backoff(self):
        calls = []

        async def api(path, payload):
            calls.append((path, dict(payload)))
            return {
                "enabled": True,
                "alreadyPublishedToday": False,
                "publishNowPending": True,
                "publishNowToken": "manual-1",
                "spaces": [],
            }

        self.plugin._api = api
        self.plugin._qzone_next_check_at = time.time() + 24 * 60 * 60
        asyncio.run(self.plugin._sync_qzone({
            "enabled": True,
            "due": True,
            "alreadyPublishedToday": False,
            "publishNowPending": True,
        }))

        self.assertEqual(["/SFreeBot/qzoneBatch"], [item[0] for item in calls])

    def test_qzone_send_uses_napcat_credentials_and_qzone_http(self):
        calls = []

        async def call_action(action, **payload):
            calls.append((action, payload))
            if action == "get_cookies":
                return {"cookies": "uin=o1174120239; skey=test-skey; p_skey=test-p-skey"}
            raise AssertionError(f"unexpected NapCat action: {action}")

        async def post_form(url, params, data, context, timeout):
            calls.append(("http", {"url": url, "params": params, "data": data}))
            if "cgi_upload_image" in url:
                return {"ret": 0, "data": {
                    "url": "https://example.test/photo.jpg&bo=pic-bo",
                    "albumid": "0", "lloc": "lloc", "sloc": "sloc",
                    "type": "1", "height": "1350", "width": "1080",
                }}
            return {"code": 0, "tid": "tid-1"}

        platform_meta = SimpleNamespace(id="001", name="aiocqhttp")
        platform = SimpleNamespace(meta=lambda: platform_meta,
                                   bot=SimpleNamespace(call_action=call_action))
        self.plugin.context.platform_manager.platform_insts = [platform]
        self.plugin._qzone_post_form = post_form

        result = asyncio.run(self.plugin._send_qzone_message(
            {"postText": "今天的动态", "ugcRight": 4},
            [b"png-1", b"png-2"],
            [{"summary": "第一条", "author": {"name": "A"}},
             {"summary": "第二条", "author": {"name": "B"}}]))

        self.assertEqual("tid-1", result["data"]["tid"])
        self.assertEqual("get_cookies", calls[0][0])
        http_calls = [item for item in calls if item[0] == "http"]
        self.assertEqual(3, len(http_calls))
        self.assertEqual("今天的动态", http_calls[-1][1]["data"]["con"])
        self.assertEqual("4", http_calls[-1][1]["data"]["ugc_right"])
        self.assertEqual("pic-bo,pic-bo", http_calls[-1][1]["data"]["pic_bo"])
        self.assertNotIn("send_qzone_msg", [item[0] for item in calls])

    def test_qzone_image_loader_rejects_private_network_urls(self):
        self.assertFalse(self.plugin._remote_url_allowed("http://127.0.0.1/private.png"))
        self.assertFalse(self.plugin._remote_url_allowed("http://192.168.1.10/private.png"))

    async def _collect_command(self, handler, event):
        return [item async for item in handler(event)]


if __name__ == "__main__":
    unittest.main()
