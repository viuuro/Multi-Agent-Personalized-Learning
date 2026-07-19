import unittest

from fastapi.testclient import TestClient

from main import app


class FileSecurityTest(unittest.TestCase):
    client = TestClient(app)

    def test_parse_file_accepts_utf8_markdown(self):
        response = self.client.post(
            "/parse-file",
            files={"file": ("notes.md", "# 学习记录\n今天完成了测试。".encode("utf-8"), "text/markdown")},
        )

        self.assertEqual(200, response.status_code)
        self.assertEqual("notes.md", response.json()["filename"])

    def test_parse_file_rejects_binary_renamed_as_text(self):
        response = self.client.post(
            "/parse-file",
            files={"file": ("payload.txt", b"text\x00binary", "text/plain")},
        )

        self.assertEqual(415, response.status_code)

    def test_parse_file_rejects_invalid_pdf_signature(self):
        response = self.client.post(
            "/parse-file",
            files={"file": ("payload.pdf", b"not-a-pdf", "application/pdf")},
        )

        self.assertEqual(415, response.status_code)

    def test_python_ai_service_does_not_allow_cross_origin_preflight(self):
        response = self.client.options(
            "/artifacts/image",
            headers={
                "Origin": "https://attacker.example",
                "Access-Control-Request-Method": "POST",
                "Access-Control-Request-Headers": "content-type",
            },
        )

        self.assertNotIn("access-control-allow-origin", response.headers)

    def test_python_ai_service_rejects_untrusted_host(self):
        response = self.client.get("/health", headers={"Host": "public.example"})

        self.assertEqual(400, response.status_code)


if __name__ == "__main__":
    unittest.main()
