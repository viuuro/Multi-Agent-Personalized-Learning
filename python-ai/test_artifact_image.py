import unittest
from unittest.mock import Mock, patch

from fastapi import HTTPException

import main


class ArtifactImageTest(unittest.TestCase):
    def test_image_generation_does_not_reuse_mimo_configuration(self):
        request = main.ArtifactRequest(prompt="画一张二叉树遍历知识图")
        with patch.object(main, "IMAGE_GENERATION_API_KEY", ""):
            with self.assertRaises(HTTPException) as context:
                main._generate_image(request)

        self.assertEqual(503, context.exception.status_code)
        self.assertIn("IMAGE_GENERATION_API_KEY", context.exception.detail)

    def test_image_generation_calls_dedicated_api(self):
        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = {"data": [{"b64_json": "aW1hZ2U="}]}
        request = main.ArtifactRequest(prompt="画一张二叉树遍历知识图")

        with (
            patch.object(main, "IMAGE_GENERATION_API_BASE", "https://ark.example/api/v3"),
            patch.object(main, "IMAGE_GENERATION_API_KEY", "image-key"),
            patch.object(main, "IMAGE_GENERATION_MODEL", "seedream-image-model"),
            patch.object(main.http_requests, "post", return_value=response) as post,
        ):
            result = main._generate_image(request)

        self.assertEqual("data:image/png;base64,aW1hZ2U=", result["dataUrl"])
        url = post.call_args.args[0]
        kwargs = post.call_args.kwargs
        self.assertEqual("https://ark.example/api/v3/images/generations", url)
        self.assertEqual("Bearer image-key", kwargs["headers"]["Authorization"])
        self.assertEqual("seedream-image-model", kwargs["json"]["model"])
        self.assertEqual("b64_json", kwargs["json"]["response_format"])
        self.assertNotIn("n", kwargs["json"])

    def test_image_generation_rejects_invalid_provider_shape(self):
        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = {"data": "not-a-list"}

        with (
            patch.object(main, "IMAGE_GENERATION_API_BASE", "https://ark.example/api/v3"),
            patch.object(main, "IMAGE_GENERATION_API_KEY", "image-key"),
            patch.object(main, "IMAGE_GENERATION_MODEL", "seedream-image-model"),
            patch.object(main.http_requests, "post", return_value=response),
        ):
            with self.assertRaises(HTTPException) as context:
                main._generate_image(main.ArtifactRequest(prompt="知识图"))

        self.assertEqual(502, context.exception.status_code)

    def test_remote_image_is_converted_to_persistent_data_url(self):
        provider_response = Mock()
        provider_response.raise_for_status.return_value = None
        provider_response.json.return_value = {"data": [{"url": "https://cdn.example/image.png"}]}
        image_response = Mock()
        image_response.raise_for_status.return_value = None
        image_response.headers = {"Content-Type": "image/png"}
        image_response.iter_content.return_value = [b"persistent-image"]

        with (
            patch.object(main, "IMAGE_GENERATION_API_KEY", "image-key"),
            patch.object(main.http_requests, "post", return_value=provider_response),
            patch.object(main.http_requests, "get", return_value=image_response) as get,
        ):
            result = main._generate_image(main.ArtifactRequest(prompt="知识图"))

        self.assertTrue(result["dataUrl"].startswith("data:image/png;base64,"))
        get.assert_called_once_with("https://cdn.example/image.png", timeout=30, stream=True)


if __name__ == "__main__":
    unittest.main()
