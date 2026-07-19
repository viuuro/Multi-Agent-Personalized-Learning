import unittest
from types import SimpleNamespace

from question_pipeline import _review_candidates, deduplicate, infer_course_family, normalize_candidate


class StaticReviewer:
    def invoke(self, _messages):
        return SimpleNamespace(content='''[
          {"candidateIndex":0,"verifiedAnswer":"B","verdict":"PASS","confidence":92,"reason":"可验证"},
          {"candidateIndex":1,"verifiedAnswer":"D","verdict":"PASS","confidence":90,"reason":"独立答案不一致"}
        ]''')


class QuestionPipelineTest(unittest.TestCase):
    def test_course_templates_cover_core_software_engineering_courses(self):
        self.assertEqual("programming", infer_course_family("C++ 指针与函数"))
        self.assertEqual("database", infer_course_family("MySQL 事务隔离与索引"))
        self.assertEqual("mathematics", infer_course_family("线性代数矩阵运算"))
        self.assertEqual("systems", infer_course_family("操作系统进程调度"))
        self.assertEqual("computer_organization", infer_course_family("计算机组成原理 Cache 地址映射"))
        self.assertEqual("network", infer_course_family("计算机网络 TCP 协议"))

    def test_choice_validation_rejects_ambiguous_or_malformed_answers(self):
        valid = {
            "question": "在四个选项中选择满足条件的唯一结果。",
            "options": ["结果一", "结果二", "结果三", "结果四"],
            "correctAnswer": "B",
            "explanation": "结果二满足题设条件，其余选项违反前提。",
            "sourceChunkIds": [7],
            "knowledgePoint": "条件判断",
            "learningObjective": "能够识别满足条件的唯一结果",
            "cognitiveLevel": "应用",
        }
        self.assertIsNotNone(normalize_candidate(valid, "SINGLE_CHOICE", {7}, require_sources=True))

        malformed = {**valid, "correctAnswer": "A,B"}
        self.assertIsNone(normalize_candidate(malformed, "SINGLE_CHOICE", {7}, require_sources=True))
        duplicated_options = {**valid, "options": ["相同", "相同", "第三项", "第四项"]}
        self.assertIsNone(normalize_candidate(
            duplicated_options, "SINGLE_CHOICE", {7}, require_sources=True))

        normalized_duplicate_options = {
            **valid, "options": ["结果一。", "结果一", "第三项", "第四项"]}
        self.assertIsNone(normalize_candidate(
            normalized_duplicate_options, "SINGLE_CHOICE", {7}, require_sources=True))

        stem_as_option = {
            **valid,
            "question": "下列哪一项完整描述了数据库事务的原子性？",
            "options": ["下列哪一项完整描述了数据库事务的原子性", "一致性", "隔离性", "持久性"],
        }
        self.assertIsNone(normalize_candidate(
            stem_as_option, "SINGLE_CHOICE", {7}, require_sources=True))

        opaque_reference = {
            **valid,
            "options": ["资料1中的定义", "结果二", "结果三", "结果四"],
        }
        self.assertIsNone(normalize_candidate(
            opaque_reference, "SINGLE_CHOICE", {7}, require_sources=True))

    def test_grounded_question_must_reference_an_allowed_chunk(self):
        item = {
            "question": "根据资料判断该概念的适用条件是什么？",
            "options": ["条件一", "条件二", "条件三", "条件四"],
            "correctAnswer": "A",
            "explanation": "资料明确说明条件一是该概念成立的必要前提。",
            "sourceChunkIds": [999],
            "knowledgePoint": "适用条件",
            "learningObjective": "能够判断概念的适用范围",
            "cognitiveLevel": "理解",
        }
        self.assertIsNone(normalize_candidate(
            item, "SINGLE_CHOICE", {7}, require_sources=True))

    def test_fuzzy_deduplication_removes_rephrased_duplicates(self):
        first = {"question": "Java 中 ArrayList 的随机访问时间复杂度是多少？"}
        second = {"question": "Java中ArrayList的随机访问时间复杂度是多少"}
        third = {"question": "LinkedList 在头部插入元素的时间复杂度是多少？"}
        result = deduplicate([first, second, third], 10)
        self.assertEqual([first, third], result)

    def test_deduplication_rejects_reused_option_sets_across_different_stems(self):
        first = {
            "question": "哪项实践最能验证链表插入操作？",
            "options": ["运行边界用例", "背诵定义", "忽略输出", "复制答案"],
        }
        reordered = {
            "question": "学习数据库事务时应该采用哪种方式？",
            "options": ["复制答案", "运行边界用例", "忽略输出", "背诵定义"],
        }
        distinct = {
            "question": "哪项证据可以说明索引优化有效？",
            "options": ["对比执行计划", "删除测试数据", "关闭数据库", "省略查询条件"],
        }
        self.assertEqual([first, distinct], deduplicate([first, reordered, distinct], 10))

    def test_history_is_included_in_deduplication(self):
        old = {"question": "Java 中 List 接口的主要用途是什么？", "options": ["一", "二", "三", "四"]}
        repeated = {"question": "Java中List接口的主要用途是什么", "options": ["甲", "乙", "丙", "丁"]}
        fresh = {"question": "HashMap 如何处理键的哈希冲突？", "options": ["链表", "数组", "队列", "栈"]}
        self.assertEqual([fresh], deduplicate([repeated, fresh], 10, [old]))

    def test_blind_reviewer_rejects_answer_disagreement(self):
        base = {
            "options": ["选项一", "选项二", "选项三", "选项四"],
            "correctAnswer": "B",
            "explanation": "选项二符合题设条件，其余选项不满足前提。",
            "knowledgePoint": "条件判断",
            "learningObjective": "能够判断唯一正确结果",
            "cognitiveLevel": "应用",
            "sourceChunkIds": [],
            "qualityScore": 70,
        }
        candidates = [
            {**base, "question": "根据给定条件，第一道题的唯一正确结果是哪项？"},
            {**base, "question": "根据另一组条件，第二道题的唯一正确结果是哪项？",
             "options": ["结果甲", "结果乙", "结果丙", "结果丁"]},
        ]
        req = SimpleNamespace(week_topic="条件判断", task_title="完成条件练习")
        reviewed = _review_candidates(
            req, candidates, 2, "SINGLE_CHOICE", "MEDIUM", "", set(), StaticReviewer())
        self.assertEqual([candidates[0]["question"]], [item["question"] for item in reviewed])


if __name__ == "__main__":
    unittest.main()
