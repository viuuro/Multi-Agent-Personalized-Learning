from __future__ import annotations

import base64
import os
import sys
import unittest
from types import ModuleType, SimpleNamespace
from unittest.mock import patch

try:
    import openai  # noqa: F401
except ModuleNotFoundError:
    openai_stub = ModuleType("openai")
    openai_stub.APIConnectionError = type("APIConnectionError", (Exception,), {})
    openai_stub.APIStatusError = type("APIStatusError", (Exception,), {})
    openai_stub.APITimeoutError = type("APITimeoutError", (Exception,), {})
    openai_stub.OpenAI = object
    sys.modules["openai"] = openai_stub

import tts_demo

VALID_WAV = b"RIFF\x04\x00\x00\x00WAVEdata"


class TtsDemoTest(unittest.TestCase):
    def setUp(self) -> None:
        tts_demo._get_tts_client.cache_clear()

    def test_non_streaming_request_uses_bingtang_preset_voice(self) -> None:
        completion = SimpleNamespace(
            choices=[
                SimpleNamespace(
                    message=SimpleNamespace(
                        audio=SimpleNamespace(data=base64.b64encode(VALID_WAV).decode())
                    )
                )
            ]
        )
        fake_client = SimpleNamespace(
            chat=SimpleNamespace(
                completions=SimpleNamespace(create=lambda **_: completion)
            )
        )

        with patch.dict(os.environ, {
            "MIMO_API_KEY": "test-key",
            "MIMO_BASE_URL": "https://shared-mimo.example/v1/",
        }, clear=False), patch.object(
            tts_demo, "OpenAI", return_value=fake_client
        ) as openai_class, patch.object(
            fake_client.chat.completions, "create", wraps=fake_client.chat.completions.create
        ) as create:
            result = tts_demo.synthesize_speech_audio("测试文本", "温柔地说")
            second_result = tts_demo.synthesize_speech_audio("另一段文本", "温柔地说")

        self.assertEqual(VALID_WAV, result)
        self.assertEqual(VALID_WAV, second_result)
        openai_class.assert_called_once()
        openai_class.assert_called_once_with(
            api_key="test-key",
            base_url="https://shared-mimo.example/v1",
            timeout=tts_demo.REQUEST_TIMEOUT_SECONDS,
            max_retries=0,
        )
        self.assertEqual(2, create.call_count)
        request = create.call_args_list[0].kwargs
        self.assertIs(request["stream"], False)
        self.assertEqual("mimo-v2.5-tts", request["model"])
        self.assertEqual(
            [
                {"role": "user", "content": "温柔地说"},
                {"role": "assistant", "content": "测试文本"},
            ],
            request["messages"],
        )
        self.assertEqual({"format": "wav", "voice": "冰糖"}, request["audio"])

    def test_empty_style_omits_optional_user_message(self) -> None:
        completion = SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(
                audio=SimpleNamespace(data=base64.b64encode(VALID_WAV).decode())
            ))]
        )
        fake_client = SimpleNamespace(
            chat=SimpleNamespace(completions=SimpleNamespace(create=lambda **_: completion))
        )

        with patch.dict(os.environ, {"MIMO_API_KEY": "test-key"}, clear=False), patch.object(
            tts_demo, "OpenAI", return_value=fake_client
        ), patch.object(
            fake_client.chat.completions, "create", wraps=fake_client.chat.completions.create
        ) as create:
            tts_demo.synthesize_speech_audio("只读这一句", "")

        self.assertEqual(
            [{"role": "assistant", "content": "只读这一句"}],
            create.call_args.kwargs["messages"],
        )

    def test_rejects_non_wav_response_bytes(self) -> None:
        completion = SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(
                audio=SimpleNamespace(data=base64.b64encode(b"not-a-wave").decode())
            ))]
        )

        with self.assertRaisesRegex(tts_demo.SpeechSynthesisError, "WAV"):
            tts_demo.extract_audio_bytes(completion)

    def test_explicit_agent_config_is_used_for_speech(self) -> None:
        completion = SimpleNamespace(
            choices=[SimpleNamespace(message=SimpleNamespace(
                audio=SimpleNamespace(data=base64.b64encode(VALID_WAV).decode())
            ))]
        )
        fake_client = SimpleNamespace(
            chat=SimpleNamespace(completions=SimpleNamespace(create=lambda **_: completion))
        )
        with patch.object(tts_demo, "OpenAI", return_value=fake_client) as openai_class:
            tts_demo.synthesize_speech_audio(
                "共用智能体配置", "", "agent-key", "https://agent-api.example/v1/"
            )

        openai_class.assert_called_once_with(
            api_key="agent-key",
            base_url="https://agent-api.example/v1",
            timeout=tts_demo.REQUEST_TIMEOUT_SECONDS,
            max_retries=0,
        )


if __name__ == "__main__":
    unittest.main()
