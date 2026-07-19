import asyncio
import json
import unittest
from unittest.mock import patch

from langchain_core.messages import AIMessage

import main


class EvaluationEndpointTest(unittest.TestCase):
    def test_evaluation_client_keeps_room_for_reasoning_and_json(self):
        self.assertGreaterEqual(main.evaluation_llm.max_tokens, 4096)
        self.assertEqual(main.evaluation_llm.request_timeout, 90.0)
        self.assertEqual(main.evaluation_llm.max_retries, 0)

    def test_evaluate_submission_returns_structured_result(self):
        model_result = {
            "score": 86,
            "dimensions": {
                "completion": 88,
                "accuracy": 85,
                "depth": 84,
                "practice": 82,
                "expression": 90,
            },
            "analysis": "任务要求已完成，结果表达清晰。",
            "suggestion": "补充一项可重复验证的数据。",
            "strengths": ["结构清晰"],
            "mastered_points": ["能够完成任务"],
            "progress_evidence": ["已建立首次成果基线"],
            "behavior_links": [],
            "weaknesses": ["验证数据不足"],
            "recommended_actions": ["补充验证记录"],
            "next_challenge": "增加一次复现实验。",
            "blessing_text": "这是很认真完成的一步。",
        }
        request = main.EvaluationRequest(
            task_description="完成测试任务",
            submission_content="测试已经完成。",
        )

        with patch.object(main, "evaluation_llm") as evaluation_client:
            evaluation_client.invoke.return_value = AIMessage(
                content=json.dumps(model_result, ensure_ascii=False)
            )
            result = asyncio.run(main.evaluate_submission(request))

        self.assertEqual(result["score"], 86)
        self.assertEqual(result["dimensions"]["completion"], 88)
        self.assertTrue(result["analysis"])
        self.assertTrue(result["suggestion"])


if __name__ == "__main__":
    unittest.main()
