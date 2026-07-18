from __future__ import annotations

import base64
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import voice_clone_demo


class VoiceCloneDemoTest(unittest.TestCase):
    def test_build_reference_audio_data_url_for_mp3(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            audio_path = Path(temp_dir) / "reference.mp3"
            audio_path.write_bytes(b"ID3-reference-audio")

            data_url = voice_clone_demo.build_reference_audio_data_url(audio_path)

        prefix, encoded = data_url.split(",", 1)
        self.assertEqual("data:audio/mpeg;base64", prefix)
        self.assertEqual(b"ID3-reference-audio", base64.b64decode(encoded))

    def test_rejects_unsupported_reference_format(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            audio_path = Path(temp_dir) / "reference.ogg"
            audio_path.write_bytes(b"audio")

            with self.assertRaisesRegex(voice_clone_demo.VoiceCloneError, "只支持"):
                voice_clone_demo.build_reference_audio_data_url(audio_path)

    def test_non_streaming_request_and_response(self) -> None:
        completion = SimpleNamespace(
            choices=[
                SimpleNamespace(
                    message=SimpleNamespace(
                        audio=SimpleNamespace(data=base64.b64encode(b"RIFF-result").decode())
                    )
                )
            ]
        )
        fake_client = SimpleNamespace(
            chat=SimpleNamespace(
                completions=SimpleNamespace(create=lambda **_: completion)
            )
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            audio_path = Path(temp_dir) / "reference.wav"
            audio_path.write_bytes(b"RIFF-reference")
            with patch.dict(os.environ, {"MIMO_API_KEY": "test-key"}, clear=False), patch.object(
                voice_clone_demo, "OpenAI", return_value=fake_client
            ) as openai_class, patch.object(
                fake_client.chat.completions, "create", wraps=fake_client.chat.completions.create
            ) as create:
                result = voice_clone_demo.voice_clone_audio(
                    audio_path,
                    "测试文本",
                    "温柔地说",
                )

        self.assertEqual(b"RIFF-result", result)
        openai_class.assert_called_once()
        request = create.call_args.kwargs
        self.assertIs(request["stream"], False)
        self.assertEqual(voice_clone_demo.MODEL_NAME, request["model"])
        self.assertEqual("温柔地说", request["messages"][0]["content"])
        self.assertEqual("测试文本", request["messages"][1]["content"])
        self.assertEqual("wav", request["audio"]["format"])


if __name__ == "__main__":
    unittest.main()
