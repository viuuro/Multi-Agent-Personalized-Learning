"""MiMo ``mimo-v2.5-tts-voiceclone`` 非流式语音克隆示例。

用法（PowerShell）：
    $env:MIMO_API_KEY = "your-api-key"
    py -3.12 python-ai/voice_clone_demo.py .\reference.wav "你好，这是克隆语音。"

参考音频只能使用 WAV 或 MP3。MiMo 要求传入带 MIME 前缀的 Data URL，且
Base64 编码后的完整内容不能超过 10 MiB。
"""

from __future__ import annotations

import argparse
import base64
import binascii
import os
from pathlib import Path
from typing import Any

from openai import APIConnectionError, APIStatusError, APITimeoutError, OpenAI


MIMO_BASE_URL = "https://api.xiaomimimo.com/v1"
MODEL_NAME = "mimo-v2.5-tts-voiceclone"
REQUEST_TIMEOUT_SECONDS = 120
MAX_BASE64_AUDIO_BYTES = 10 * 1024 * 1024
DEFAULT_STYLE_INSTRUCTION = (
    "角色是一名温柔含蓄的少女修女。声音轻柔温暖，咬字清晰、字音饱满，"
    "语速自然，保持轻柔平缓且连贯的整体节奏。"
    "以完整意群自然连读，只在句末和语义自然转折处轻微停顿；情感起伏细腻自然，"
    "避免逐字断开、刻意顿挫或拖长音。"
    "语气温柔真诚、含蓄自然，带有轻微羞怯感、关怀和祝福感；"
    "避免过度活泼跳跃或过分沉稳疏离的语调，以及刻意表演感、生硬重音、"
    "宗教仪式腔或夸张撒娇。加入自然细微的呼吸、连读、音高起伏和句尾收束，"
    "不要让每个字都同样用力或过分工整，像真人在安静地轻声交流。"
)
SUPPORTED_AUDIO_TYPES = {
    ".wav": "audio/wav",
    ".mp3": "audio/mpeg",
}


class VoiceCloneError(RuntimeError):
    """Raised when the voice-clone request cannot produce an audio file."""


def get_mimo_api_key() -> str | None:
    """Read ``MIMO_API_KEY`` from the environment or a local .env file.

    This intentionally supports just the single key needed by this standalone
    example and never logs its value. Environment variables take precedence.
    """
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


def build_reference_audio_data_url(reference_audio_path: Path) -> str:
    """Validate and encode an official MiMo voice-reference Data URL."""
    if not reference_audio_path.is_file():
        raise VoiceCloneError(f"参考音频不存在或不是文件：{reference_audio_path}")

    mime_type = SUPPORTED_AUDIO_TYPES.get(reference_audio_path.suffix.lower())
    if not mime_type:
        extensions = "、".join(SUPPORTED_AUDIO_TYPES)
        raise VoiceCloneError(f"参考音频只支持 {extensions} 格式：{reference_audio_path.name}")

    audio_bytes = reference_audio_path.read_bytes()
    if not audio_bytes:
        raise VoiceCloneError("参考音频不能为空。")

    encoded_audio = base64.b64encode(audio_bytes)
    if len(encoded_audio) > MAX_BASE64_AUDIO_BYTES:
        raise VoiceCloneError(
            "参考音频 Base64 编码后超过 MiMo 的 10 MiB 限制；请缩短或压缩音频。"
        )

    return f"data:{mime_type};base64,{encoded_audio.decode('ascii')}"


def extract_audio_bytes(completion: Any) -> bytes:
    """Extract the Base64 audio payload from a non-streaming Chat Completion."""
    try:
        audio_data = completion.choices[0].message.audio.data
    except (AttributeError, IndexError, TypeError) as exc:
        raise VoiceCloneError(
            "MiMo 响应不包含 choices[0].message.audio.data"
        ) from exc

    if not isinstance(audio_data, str) or not audio_data:
        raise VoiceCloneError("MiMo 返回的 audio.data 为空或格式错误。")

    try:
        return base64.b64decode(audio_data, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise VoiceCloneError("MiMo 返回的 audio.data 不是有效的 Base64 数据。") from exc


def voice_clone(
    reference_audio_path: str | Path,
    text_to_speak: str,
    output_path: str | Path = "output_tts.wav",
    style_instruction: str = DEFAULT_STYLE_INSTRUCTION,
) -> Path:
    """Generate a clone and save the resulting WAV file to ``output_path``."""
    target_path = Path(output_path).expanduser()
    audio_bytes = voice_clone_audio(reference_audio_path, text_to_speak, style_instruction)
    target_path.parent.mkdir(parents=True, exist_ok=True)
    target_path.write_bytes(audio_bytes)
    return target_path


def voice_clone_audio(
    reference_audio_path: str | Path,
    text_to_speak: str,
    style_instruction: str = DEFAULT_STYLE_INSTRUCTION,
) -> bytes:
    """Return WAV bytes using MiMo's official non-streaming voice-clone format.

    ``assistant`` message content is the text to synthesize. ``user`` content is
    optional and is used only as a natural-language style instruction; it is not
    spoken. The endpoint returns one JSON Chat Completion instead of SSE chunks.
    """
    api_key = get_mimo_api_key()
    if not api_key:
        raise VoiceCloneError("请设置 MIMO_API_KEY，或在 .env 文件中配置它。")
    if not text_to_speak.strip():
        raise VoiceCloneError("要合成的文本不能为空。")

    reference_path = Path(reference_audio_path).expanduser()
    voice_data_url = build_reference_audio_data_url(reference_path)

    # MiMo 官方 OpenAI 兼容非流式协议：目标文案必须位于 assistant 消息。
    client = OpenAI(
        api_key=api_key,
        base_url=MIMO_BASE_URL,
        timeout=REQUEST_TIMEOUT_SECONDS,
        max_retries=0,
    )
    try:
        completion = client.chat.completions.create(
            model=MODEL_NAME,
            stream=False,
            messages=[
                {"role": "user", "content": style_instruction.strip()},
                {"role": "assistant", "content": text_to_speak.strip()},
            ],
            audio={"format": "wav", "voice": voice_data_url},
        )
    except APIStatusError as exc:
        raise VoiceCloneError(f"MiMo API 返回 HTTP {exc.status_code}：{exc.message}") from exc
    except (APIConnectionError, APITimeoutError) as exc:
        raise VoiceCloneError(f"请求 MiMo API 失败：{exc}") from exc
    except Exception as exc:
        raise VoiceCloneError(f"调用 MiMo 语音克隆失败：{exc}") from exc

    audio_bytes = extract_audio_bytes(completion)
    if not audio_bytes:
        raise VoiceCloneError("MiMo 返回的音频为空。")
    return audio_bytes


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="使用 MiMo 官方非流式接口克隆语音")
    parser.add_argument("reference_audio", help="参考音频路径（WAV 或 MP3，Base64 后不超过 10 MiB）")
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
        saved_path = voice_clone(
            arguments.reference_audio,
            arguments.text,
            arguments.output,
            arguments.style,
        )
    except VoiceCloneError as exc:
        raise SystemExit(f"语音克隆失败：{exc}") from exc

    print(f"语音合成完成：{saved_path}")
