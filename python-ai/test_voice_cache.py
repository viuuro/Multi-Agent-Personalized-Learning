import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import main

VALID_WAV = b"RIFF\x04\x00\x00\x00WAVEdata"


class VoiceCacheTest(unittest.TestCase):
    def test_cache_key_is_stable_and_changes_with_text(self):
        first = main.build_voice_cache_key("第一句祝福", "轻柔")
        same = main.build_voice_cache_key("第一句祝福", "轻柔")
        changed = main.build_voice_cache_key("第二句祝福", "轻柔")

        self.assertEqual(first, same)
        self.assertNotEqual(first, changed)
        self.assertEqual(64, len(first))

    def test_written_voice_can_be_read_from_cache(self):
        with tempfile.TemporaryDirectory() as temp_dir, patch.object(
            main, "VOICE_CACHE_DIR", Path(temp_dir)
        ), patch.object(main, "VOICE_CACHE_TTL_SECONDS", 60):
            main.write_cached_voice("cache-key", VALID_WAV)
            cached = main.read_cached_voice("cache-key")

        self.assertEqual(VALID_WAV, cached)

    def test_invalid_cached_voice_is_deleted(self):
        with tempfile.TemporaryDirectory() as temp_dir, patch.object(
            main, "VOICE_CACHE_DIR", Path(temp_dir)
        ), patch.object(main, "VOICE_CACHE_TTL_SECONDS", 60):
            cached_file = Path(temp_dir) / "cache-key.wav"
            cached_file.write_bytes(b"not-a-wave")

            self.assertIsNone(main.read_cached_voice("cache-key"))
            self.assertFalse(cached_file.exists())


if __name__ == "__main__":
    unittest.main()
