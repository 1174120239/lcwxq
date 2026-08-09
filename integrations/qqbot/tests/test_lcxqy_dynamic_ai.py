import asyncio
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace


PLUGIN_DIR = Path(__file__).resolve().parents[1] / "astrbot_plugin_lcxqy_dynamic_ai"
sys.path.insert(0, str(PLUGIN_DIR))

from main import LcxqyDynamicAiPlugin  # noqa: E402


class DummyEvent:
    def __init__(self, text):
        self.message_str = text
        self.message_obj = SimpleNamespace(message=[], group_id="", message_id="message-1")
        self.stopped = False

    def stop_event(self):
        self.stopped = True

    def plain_result(self, text):
        return text

    def get_sender_id(self):
        return "10001"


class LcxqyDynamicAiPluginTest(unittest.TestCase):
    def setUp(self):
        self.plugin = object.__new__(LcxqyDynamicAiPlugin)
        self.plugin.config = {
            "chat_enabled": True,
            "chat_in_groups": False,
        }
        self.plugin._pending = {}

    def test_command_text_is_not_treated_as_chat(self):
        calls = []

        async def api(path, payload):
            calls.append((path, payload))
            return {"content": "unexpected"}

        self.plugin._api = api
        event = DummyEvent("发动态")

        async def collect():
            return [item async for item in self.plugin._handle_message(event)]

        self.assertEqual([], asyncio.run(collect()))
        self.assertEqual([], calls)

    def test_private_chat_stops_default_llm_pipeline(self):
        async def api(path, payload):
            self.assertEqual("/SFreeBot/chat", path)
            self.assertEqual("你好", payload["message"])
            return {"content": "论坛助手回复"}

        self.plugin._api = api
        event = DummyEvent("你好")

        async def collect():
            return [item async for item in self.plugin._handle_message(event)]

        self.assertEqual(["论坛助手回复"], asyncio.run(collect()))
        self.assertTrue(event.stopped)

    def test_command_handler_stops_default_llm_pipeline(self):
        event = DummyEvent("发动态")

        async def collect():
            return [item async for item in self.plugin.prepare_space(event)]

        result = asyncio.run(collect())
        self.assertEqual(1, len(result))
        self.assertIn("请在命令后写动态内容", result[0])
        self.assertTrue(event.stopped)


if __name__ == "__main__":
    unittest.main()
