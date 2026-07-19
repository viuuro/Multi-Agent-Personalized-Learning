"""Contract tests for the native token SSE endpoint."""
import asyncio
import json
from types import SimpleNamespace
import unittest

import main


class _StreamingModel:
    async def astream(self, _messages):
        yield SimpleNamespace(content="第一段")
        yield SimpleNamespace(content="第二段")


class _FailingStreamingModel:
    async def astream(self, _messages):
        if False:
            yield None
        raise RuntimeError("provider unavailable")


class ChatStreamTest(unittest.TestCase):
    @staticmethod
    def _prepared_turn(req):
        return (
            {"knowledgeBase": 5, "cognitiveStyle": "verbal", "weaknessPoints": [],
             "learningPace": 5, "interestAreas": ["Python"], "shortTermGoal": "学习基础"},
            {"intent": "STUDY_QA", "dialogue_state": "ANSWERING", "plan_action": "none",
             "revision_scope": {}, "temporary_state": {}, "profile_evidence": [],
             "memory_summary": ""},
            f"用户：{req.message}",
            [],
        )

    def test_stream_endpoint_emits_metadata_tokens_and_done(self):
        original_prepare = main.prepare_chat_turn
        original_llm = main.llm
        try:
            main.prepare_chat_turn = self._prepared_turn
            main.llm = _StreamingModel()

            async def collect():
                response = await main.chat_stream(main.ChatRequest(message="解释变量"))
                frames = []
                async for item in response.body_iterator:
                    frames.append(item.decode() if isinstance(item, bytes) else item)
                return response, frames

            response, frames = asyncio.run(collect())
            events = [json.loads(frame.removeprefix("data: ").strip()) for frame in frames]

            self.assertEqual(response.media_type, "text/event-stream")
            self.assertEqual([event["type"] for event in events],
                             ["metadata", "content", "content", "done"])
            self.assertEqual("".join(event["content"] for event in events[1:3]), "第一段第二段")
        finally:
            main.prepare_chat_turn = original_prepare
            main.llm = original_llm

    def test_stream_endpoint_emits_error_without_done_when_provider_fails(self):
        original_prepare = main.prepare_chat_turn
        original_llm = main.llm
        try:
            main.prepare_chat_turn = self._prepared_turn
            main.llm = _FailingStreamingModel()

            async def collect():
                response = await main.chat_stream(main.ChatRequest(message="解释变量"))
                return [
                    item.decode() if isinstance(item, bytes) else item
                    async for item in response.body_iterator
                ]

            frames = asyncio.run(collect())
            events = [json.loads(frame.removeprefix("data: ").strip()) for frame in frames]

            self.assertEqual([event["type"] for event in events], ["metadata", "error"])
            self.assertIn("稍后重试", events[-1]["content"])
        finally:
            main.prepare_chat_turn = original_prepare
            main.llm = original_llm


if __name__ == "__main__":
    unittest.main()
