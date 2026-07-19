import unittest

from resource_recommender import (
    _is_concrete_url,
    build_focus,
    contains_placeholder,
    parse_human_count,
    recommend_resources,
    resolve_topic,
)
from main import _default_plan, _sanitize_plan_profile


class OfflineSession:
    headers = {}

    def get(self, *args, **kwargs):
        raise ConnectionError("offline")


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def raise_for_status(self):
        return None

    def json(self):
        return self.payload


class VideoSession:
    headers = {}

    def get(self, url, **kwargs):
        if url.endswith("/search/all/v2"):
            return FakeResponse({
                "code": 0,
                "data": {"result": [{
                    "result_type": "video",
                    "data": [{
                        "bvid": "BV1YA411T76k",
                        "title": "Java ArrayList 集合框架教程",
                        "play": "1.2万",
                    }],
                }]},
            })
        return FakeResponse({
            "code": 0,
            "data": {
                "bvid": "BV1YA411T76k",
                "title": "Java ArrayList 集合框架教程",
                "duration": 600,
                "stat": {"view": 12000},
                "owner": {"name": "课程作者"},
            },
        })


class ResourceRecommenderTest(unittest.TestCase):
    def test_human_readable_play_counts_are_normalized(self):
        self.assertEqual(12_000, parse_human_count("1.2万"))
        self.assertEqual(230_000_000, parse_human_count("2.3亿"))
        self.assertEqual(1234, parse_human_count("1,234"))
        self.assertEqual(0, parse_human_count("--"))

    def test_focus_keeps_course_and_technology_terms(self):
        query, terms = build_focus("第 2 周：Java 集合框架进阶", ["理解 ArrayList 和复杂度"])
        self.assertIn("Java", query)
        self.assertIn("Java", terms)

    def test_offline_fallback_returns_specific_resources_not_search_pages(self):
        resources = recommend_resources(
            "操作系统进程与线程", ["理解调度与同步"], count=2, session=OfflineSession())
        self.assertEqual(2, len(resources))
        self.assertTrue(any("OSTEP" in item["title"] for item in resources))
        self.assertTrue(all(_is_concrete_url(item["url"]) for item in resources))

    def test_data_structure_resources_follow_course_and_chapter_focus(self):
        query, terms = build_focus(
            "数据结构：图与最短路径",
            ["比较 Dijkstra 与 Floyd 的适用条件"],
            "章节=数据结构与算法 > 第9章 图 > 最短路径")
        self.assertIn("数据结构", query)
        self.assertIn("最短路径", terms)
        resources = recommend_resources(
            "数据结构：图与最短路径", ["完成图算法追踪题"],
            count=2, session=OfflineSession())
        self.assertEqual(2, len(resources))
        self.assertTrue(all(item["platform"] in {
            "OpenDSA", "Princeton", "VisuAlgo", "OI Wiki"} for item in resources))

    def test_computer_organization_resources_follow_cache_focus(self):
        query, terms = build_focus(
            "计算机组成原理：Cache 地址映射",
            ["完成标记、组号与块内偏移计算"],
            "章节=计算机组成原理 > 存储系统 > Cache")
        self.assertIn("计算机组成原理", query)
        self.assertIn("Cache", terms)
        resources = recommend_resources(
            "计算机组成原理：Cache 地址映射", ["分析组相联命中过程"],
            count=2, session=OfflineSession())
        self.assertEqual(2, len(resources))
        self.assertTrue(all(item["platform"] in {
            "CMU", "UC Berkeley", "Nand2Tetris"} for item in resources))

    def test_search_pages_are_rejected(self):
        self.assertFalse(_is_concrete_url("https://search.bilibili.com/all?keyword=Java"))
        self.assertFalse(_is_concrete_url("https://www.bilibili.com/", "B站"))
        self.assertFalse(_is_concrete_url("https://www.bilibili.com/video/av123", "B站"))
        self.assertFalse(_is_concrete_url("https://github.com/search?q=java"))
        self.assertTrue(_is_concrete_url("https://www.bilibili.com/video/BV1234567890"))

    def test_placeholder_interest_never_becomes_a_resource_topic(self):
        self.assertTrue(contains_placeholder("待评估入门"))
        self.assertEqual("软件工程基础", resolve_topic("待评估"))
        resources = recommend_resources("待评估入门", count=2, session=OfflineSession())
        self.assertEqual(2, len(resources))
        self.assertTrue(all("待评估" not in item["title"] for item in resources))
        self.assertTrue(all(_is_concrete_url(item["url"], item["platform"]) for item in resources))

    def test_placeholder_profile_produces_a_clean_default_plan_topic(self):
        profile = _sanitize_plan_profile({
            "interestAreas": ["待评估"],
            "weaknessPoints": ["待评估"],
            "shortTermGoal": "探索学习方向",
        })
        plan = _default_plan(profile)
        self.assertEqual("软件工程基础入门", plan[0]["topic"])
        self.assertTrue(all("待评估" not in week["topic"] for week in plan))

    def test_video_candidates_are_verified_and_returned_as_direct_bv_links(self):
        resources = recommend_resources(
            "Java ArrayList 专项", ["分析集合复杂度"], count=2, session=VideoSession())
        self.assertEqual("video", resources[0]["type"])
        self.assertEqual("https://www.bilibili.com/video/BV1YA411T76k", resources[0]["url"])
        self.assertEqual("Oracle Java", resources[1]["platform"])

    def test_negative_feedback_removes_a_previously_disliked_resource(self):
        resources = recommend_resources(
            "Java ArrayList 专项", ["分析集合复杂度"], count=2, session=VideoSession(),
            feedback_scores={"https://www.bilibili.com/video/BV1YA411T76k": -45})
        self.assertEqual(2, len(resources))
        self.assertNotIn(
            "https://www.bilibili.com/video/BV1YA411T76k",
            {item["url"] for item in resources})
        self.assertTrue(any(item["platform"] == "Oracle Java" for item in resources))


if __name__ == "__main__":
    unittest.main()
