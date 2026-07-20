"""MiMo ``mimo-v2.5-tts`` 非流式预置音色语音合成客户端。

用法（PowerShell）：
    $env:MIMO_API_KEY = "your-api-key"
    py -3.12 python-ai/tts_demo.py "你好，很高兴继续陪你学习。"

目标文本必须放在 ``assistant`` 消息中；可选风格指令放在 ``user`` 消息中。
本项目固定使用 MiMo 官方中文女性预置音色“冰糖”。
"""

from __future__ import annotations

import argparse
import base64
import binascii
import os
from functools import lru_cache
from pathlib import Path
from typing import Any

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI


MIMO_BASE_URL = "https://api.xiaomimimo.com/v1"
MODEL_NAME = "mimo-v2.5-tts"
PRESET_VOICE = "冰糖"
REQUEST_TIMEOUT_SECONDS = 120
DEFAULT_STYLE_INSTRUCTION = (
    "角色是一名温柔含蓄、会长期陪伴用户学习的少女。声音轻柔温暖，咬字清晰、字音饱满，"
    "语速自然，保持轻柔平缓且连贯的整体节奏。"
    "以完整意群自然连读，只在句末和语义自然转折处轻微停顿；情感起伏细腻自然，"
    "避免逐字断开、刻意顿挫或拖长音。"
    "语气温柔真诚、含蓄自然，带有轻微羞怯感、关怀和祝福感；"
    "避免过度活泼跳跃或过分沉稳疏离的语调，以及刻意表演感、生硬重音、"
    "宗教仪式腔或夸张撒娇。加入自然细微的呼吸、连读、音高起伏和句尾收束，"
    "不要让每个字都同样用力或过分工整，像真人在安静地轻声交流。"
)


class SpeechSynthesisError(RuntimeError):
    """Raised when MiMo cannot produce a valid WAV response."""


def is_wav_audio(audio_bytes: bytes) -> bool:
    """Return whether bytes contain the minimum RIFF/WAVE signature browsers expect."""
    return (
        len(audio_bytes) >= 12
        and audio_bytes[:4] == b"RIFF"
        and audio_bytes[8:12] == b"WAVE"
    )


def get_mimo_api_key() -> str | None:
    """Read ``MIMO_API_KEY`` from the environment or a local .env file."""
    if api_key := os.getenv("MIMO_API_KEY"):
        return api_key

    script_dir = Path(__file__).resolve().parent
    candidate_paths = (Path.cwd() / ".env", script_dir / ".env", script_dir.parent / ".env")
    visited_paths: set[Path] = set()
    for env_path in candidate_paths:
        resolved_path = env_path.resolve()
        if resolved_path in visited_paths or not resolved_path.is_file():
            continue
        visited_paths.add(resolved_path)

        for raw_line in resolved_path.read_text(encoding="utf-8-sig").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            if line.startswith("export "):
                line = line[7:].lstrip()
            name, separator, value = line.partition("=")
            if separator and name.strip() == "MIMO_API_KEY":
                value = value.strip()
                if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
                    value = value[1:-1]
                if value:
                    return value
    return None


@lru_cache(maxsize=2)
def _get_tts_client(api_key: str) -> OpenAI:
    """Reuse the MiMo HTTP connection pool instead of creating it per request."""
    return OpenAI(
        api_key=api_key,
        base_url=MIMO_BASE_URL,
        timeout=REQUEST_TIMEOUT_SECONDS,
        max_retries=0,
    )


def extract_audio_bytes(completion: Any) -> bytes:
    """Extract the Base64 WAV payload from a non-streaming Chat Completion."""
    try:
        audio_data = completion.choices[0].message.audio.data
    except (AttributeError, IndexError, TypeError) as exc:
        raise SpeechSynthesisError(
            "MiMo 响应不包含 choices[0].message.audio.data"
        ) from exc

    if not isinstance(audio_data, str) or not audio_data:
        raise SpeechSynthesisError("MiMo 返回的 audio.data 为空或格式错误。")

    try:
        audio_bytes = base64.b64decode(audio_data, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise SpeechSynthesisError("MiMo 返回的 audio.data 不是有效的 Base64 数据。") from exc
    if not is_wav_audio(audio_bytes):
        raise SpeechSynthesisError("MiMo 返回的数据不是有效的 WAV 音频。")
    return audio_bytes


def synthesize_speech(
    text_to_speak: str,
    output_path: str | Path = "output_tts.wav",
    style_instruction: str = DEFAULT_STYLE_INSTRUCTION,
) -> Path:
    """Synthesize speech and save the resulting WAV file."""
    target_path = Path(output_path).expanduser()
    audio_bytes = synthesize_speech_audio(text_to_speak, style_instruction)
    target_path.parent.mkdir(parents=True, exist_ok=True)
    target_path.write_bytes(audio_bytes)
    return target_path


def synthesize_speech_audio(
    text_to_speak: str,
    style_instruction: str = DEFAULT_STYLE_INSTRUCTION,
) -> bytes:
    """Return WAV bytes using MiMo's official non-streaming preset-voice format."""
    api_key = get_mimo_api_key()
    if not api_key:
        raise SpeechSynthesisError("请设置 MIMO_API_KEY，或在 .env 文件中配置它。")

    text = text_to_speak.strip()
    if not text:
        raise SpeechSynthesisError("要合成的文本不能为空。")

    messages: list[dict[str, str]] = []
    if style := style_instruction.strip():
        messages.append({"role": "user", "content": style})
    messages.append({"role": "assistant", "content": text})

    client = _get_tts_client(api_key)
    try:
        completion = client.chat.completions.create(
            model=MODEL_NAME,
            stream=False,
            messages=messages,
            audio={"format": "wav", "voice": PRESET_VOICE},
        )
    except APIStatusError as exc:
        raise SpeechSynthesisError(f"MiMo API 返回 HTTP {exc.status_code}：{exc.message}") from exc
    except (APIConnectionError, APITimeoutError) as exc:
        raise SpeechSynthesisError(f"请求 MiMo API 失败：{exc}") from exc
    except Exception as exc:
        raise SpeechSynthesisError(f"调用 MiMo 语音合成失败：{exc}") from exc

    return extract_audio_bytes(completion)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="使用 MiMo 冰糖预置音色合成语音")
    parser.add_argument("text", help="要合成为语音的文本")
    parser.add_argument("-o", "--output", default="output_tts.wav", help="输出 WAV 文件路径")
    parser.add_argument(
        "-s",
        "--style",
        default=DEFAULT_STYLE_INSTRUCTION,
        help="可选：控制语速、情绪或语气的自然语言指令（不会被朗读）",
    )
    return parser.parse_args()


if __name__ == "__main__":
    arguments = parse_args()
    try:
        saved_path = synthesize_speech(arguments.text, arguments.output, arguments.style)
    except SpeechSynthesisError as exc:
        raise SystemExit(f"语音合成失败：{exc}") from exc

    print(f"语音合成完成（冰糖）：{saved_path}")
