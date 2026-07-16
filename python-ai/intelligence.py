"""不依赖模型的智能决策、归一化与质量检查工具。"""

from __future__ import annotations

from difflib import SequenceMatcher
from datetime import datetime, timezone
import json
import re
from typing import Any


INTENTS = {
    "STUDY_QA",
    "PROFILE_DISCOVERY",
    "PLAN_CREATE",
    "PLAN_REVISE",
    "SUBMISSION_REVIEW",
    "PROGRESS_REVIEW",
    "RESOURCE_QUERY",
    "CASUAL",
}

PLAN_ACTIONS = {
    "none",
    "create",
    "full_regenerate",
    "modify_week",
    "adjust_difficulty",
    "adjust_pace",
    "change_direction",
    "adjust_resources",
}


def parse_json_object(value: Any, fallback: dict | None = None) -> dict:
    if isinstance(value, dict):
        return value
    if not isinstance(value, str) or not value.strip():
        return dict(fallback or {})
    try:
        parsed = json.loads(value)
        if isinstance(parsed, str):
            parsed = json.loads(parsed)
        return parsed if isinstance(parsed, dict) else dict(fallback or {})
    except (TypeError, ValueError, json.JSONDecodeError):
        return dict(fallback or {})


def _contains(text: str, patterns: list[str]) -> bool:
    return any(re.search(pattern, text, re.IGNORECASE) for pattern in patterns)


def classify_intent(message: str) -> str:
    text = (message or "").strip()
    if _contains(text, [r"(提交|上传).{0,8}(成果|作业|报告)", r"(评分|评价|批改).{0,6}(成果|作业|报告)"]):
        return "SUBMISSION_REVIEW"
    if _contains(text, [r"(进度|活跃度|完成情况|学得怎么样|学习情况)"]):
        return "PROGRESS_REVIEW"
    if _contains(text, [r"(资源|课程|教程|书|视频|资料).{0,8}(推荐|找|有没有)", r"推荐.{0,8}(资源|课程|教程|书|视频|资料)"]):
        return "RESOURCE_QUERY"
    if _contains(text, [
        r"(重新|更新|调整|修改|重做|改一下|替换|换一份).{0,12}(学习)?计划",
        r"(学习)?计划.{0,20}(难度|更难|简单|节奏|进度|周期|时间|方向|主题|资源|课程|教程|视频|资料)",
        r"把.{0,8}(学习)?计划.{0,20}(换成|改成|调整到|转到)",
        r"第\s*[一二三四1234]\s*周.{0,20}(难|简单|修改|调整|替换|改成|资源|任务|内容|主题)",
        r"(替换|修改|调整|改).{0,20}第\s*[一二三四1234]\s*周",
    ]):
        return "PLAN_REVISE"
    if _contains(text, [r"(生成|制定|做|安排).{0,30}(学习)?计划", r"学习计划怎么安排"]):
        return "PLAN_CREATE"
    if _contains(text, [r"我.{0,6}(想|希望).{0,6}(学|掌握|完成)", r"我(想学|的目标|擅长|不擅长|喜欢|每天能学|基础)", r"我的.{0,8}(目标|水平|基础|习惯|偏好)"]):
        return "PROFILE_DISCOVERY"
    if _contains(text, [r"^(你好|嗨|在吗|谢谢|好的|明白了|哈哈)[！!。.]?$", r"你是谁|什么模型"]):
        return "CASUAL"
    return "STUDY_QA"


def extract_revision_scope(message: str) -> dict:
    text = (message or "").strip()
    week_map = {"一": 1, "二": 2, "三": 3, "四": 4}
    weeks: list[int] = []
    for raw in re.findall(r"第\s*([一二三四1234])\s*周", text):
        week = week_map.get(raw, int(raw) if raw.isdigit() else 0)
        if week and week not in weeks:
            weeks.append(week)
    direction = ""
    if _contains(text, [r"更难|难一点|提高难度|提高(一些|一点)|难度.{0,4}(高|提升)|进阶"]):
        direction = "harder"
    elif _contains(text, [r"简单一点|降低难度|降低(一些|一点)|太难|轻松一点"]):
        direction = "easier"
    return {"weeks": weeks, "direction": direction}


def detect_plan_action(message: str, intent: str | None = None) -> str:
    text = (message or "").strip()
    resolved_intent = intent or classify_intent(text)
    if resolved_intent == "PLAN_CREATE":
        return "create"
    if resolved_intent != "PLAN_REVISE":
        return "none"
    if _contains(text, [r"资源|课程|教程|书|视频|资料"]):
        return "adjust_resources"
    if _contains(text, [r"第\s*[一二三四1234]\s*周"]):
        return "modify_week"
    if _contains(text, [r"难度|太难|太简单|更难|简单一点|进阶"]):
        return "adjust_difficulty"
    if _contains(text, [r"节奏|进度|每天|每周|时间|周期|快一点|慢一点"]):
        return "adjust_pace"
    if _contains(text, [r"方向|主题|改学|换成|转到"]):
        return "change_direction"
    return "full_regenerate"


def _profile_has_direction(profile: dict) -> bool:
    interests = profile.get("interestAreas")
    goal = str(profile.get("shortTermGoal", "")).strip()
    valid_interests = [str(v).strip() for v in interests] if isinstance(interests, list) else []
    invalid = {"", "待评估", "综合学习"}
    return any(v not in invalid for v in valid_interests) or goal not in {"", "待评估", "探索学习方向"}


def _message_has_learning_direction(message: str) -> bool:
    text = re.sub(
        r"(请|帮我|给我|想要|我想|生成|制定|做|安排|一份|一个|四周|4周|学习|计划|谢谢|吧)",
        "",
        message or "",
    )
    text = re.sub(r"[\s，。！？:：;；、]", "", text)
    return len(text) >= 2


def _extract_temporary_state(message: str, existing: dict | None = None) -> dict:
    state = dict(existing or {})
    text = (message or "").strip()
    if _contains(text, [r"累了|很累|没精神|状态不好|困了"]):
        state["energy"] = "low"
    elif _contains(text, [r"精力很好|状态很好|很有精神"]):
        state["energy"] = "high"
    if _contains(text, [r"焦虑|着急|来不及|压力大"]):
        state["emotion"] = "anxious"
    elif _contains(text, [r"挫败|沮丧|学不会|想放弃"]):
        state["emotion"] = "frustrated"
    elif _contains(text, [r"有信心|感觉不错|掌握了"]):
        state["emotion"] = "confident"
    time_match = re.search(r"(?:每天|一天|每周|这周|今天)?.{0,4}(\d+(?:\.\d+)?)\s*(小时|分钟|天|周)", text)
    if time_match:
        state["timeAvailable"] = f"{time_match.group(1)}{time_match.group(2)}"
    confusion_match = re.search(r"(?:不懂|不会|没理解|搞不清|卡在)(.{1,40})", text)
    if confusion_match:
        state["currentConfusion"] = confusion_match.group(1).strip("，。！？ ")
    return state


def expire_temporary_state(state: dict | None, updated_at: str = "") -> dict:
    result = dict(state or {})
    if not updated_at:
        return result
    try:
        timestamp = datetime.fromisoformat(updated_at.replace("Z", "+00:00"))
        if timestamp.tzinfo is None:
            timestamp = timestamp.replace(tzinfo=datetime.now().astimezone().tzinfo)
        age_hours = (datetime.now(timezone.utc) - timestamp.astimezone(timezone.utc)).total_seconds() / 3600
    except (TypeError, ValueError):
        return result
    if age_hours > 12:
        for key in ("energy", "emotion", "timeAvailable", "currentConfusion", "currentGoal"):
            result.pop(key, None)
    if age_hours > 24 * 7:
        for key in ("pendingPlanAction", "pendingPlanRequest", "pendingQuestion"):
            result.pop(key, None)
    return result


def _rule_profile_evidence(message: str) -> list[dict]:
    text = (message or "").strip()
    evidence: list[dict] = []
    interest = re.search(r"我(?:真正)?想(?:系统)?学(?:习)?[：:]?\s*([^，。！？\n]{1,40})", text)
    if interest:
        value = interest.group(1).strip()
        evidence.append({
            "dimension": "interestAreas", "value": [value], "evidence": interest.group(0),
            "confidence": 0.95, "scope": "LONG_TERM", "action": "merge",
        })
    goal = re.search(r"(?:我的)?(?:短期)?目标是[：:]?\s*([^。！？\n]{1,80})", text)
    if goal:
        evidence.append({
            "dimension": "shortTermGoal", "value": goal.group(1).strip(), "evidence": goal.group(0),
            "confidence": 0.97, "scope": "LONG_TERM", "action": "replace",
        })
    if _contains(text, [r"喜欢看(视频|图解|图示)", r"看图.{0,8}(容易|好理解)"]):
        evidence.append({
            "dimension": "cognitiveStyle", "value": "visual", "evidence": text[:120],
            "confidence": 0.88, "scope": "LONG_TERM", "action": "replace",
        })
    elif _contains(text, [r"喜欢.{0,5}(文字|阅读|讲解)", r"听讲.{0,8}(容易|好理解)"]):
        evidence.append({
            "dimension": "cognitiveStyle", "value": "verbal", "evidence": text[:120],
            "confidence": 0.86, "scope": "LONG_TERM", "action": "replace",
        })
    elif _contains(text, [r"喜欢.{0,5}(动手|实践|做项目|练习)"]):
        evidence.append({
            "dimension": "cognitiveStyle", "value": "kinesthetic", "evidence": text[:120],
            "confidence": 0.88, "scope": "LONG_TERM", "action": "replace",
        })
    return evidence


def build_rolling_summary(existing_summary: str, message: str, max_length: int = 1400) -> str:
    summary = (existing_summary or "").strip()
    latest = re.sub(r"\s+", " ", (message or "").strip())[:240]
    if latest:
        summary = f"{summary}\n用户最新诉求：{latest}".strip()
    return summary[-max_length:]


def rule_based_turn_analysis(
    message: str,
    profile: dict | None = None,
    existing_state: dict | None = None,
    existing_summary: str = "",
) -> dict:
    profile = profile or {}
    state = _extract_temporary_state(message, existing_state)
    pending_action = str(state.get("pendingPlanAction", "none"))
    pending_request = str(state.get("pendingPlanRequest", "")).strip()
    intent = classify_intent(message)
    plan_action = detect_plan_action(message, intent)
    revision_request = (message or "").strip()

    # 用户正在回答上一轮的计划澄清问题时，恢复被挂起的计划操作。
    if pending_action in PLAN_ACTIONS - {"none"} and intent not in {"PLAN_CREATE", "PLAN_REVISE"}:
        intent = "PLAN_REVISE" if pending_action != "create" else "PLAN_CREATE"
        plan_action = pending_action
        revision_request = f"{pending_request}；用户补充：{message}".strip("；")
        state.pop("pendingPlanAction", None)
        state.pop("pendingPlanRequest", None)

    scope = extract_revision_scope(revision_request)
    needs_clarification = False
    question = ""
    if plan_action == "create" and not _profile_has_direction(profile) and not _message_has_learning_direction(message):
        needs_clarification = True
        question = "你最希望这份计划围绕哪个学习方向或具体目标展开？"
    elif plan_action == "modify_week" and not scope["weeks"]:
        needs_clarification = True
        question = "你希望调整第几周，以及主要想改难度、内容还是任务量？"
    elif plan_action == "adjust_difficulty" and not scope["direction"]:
        needs_clarification = True
        question = "你希望整体难度提高还是降低，是否只调整某一周？"
    elif plan_action == "change_direction" and not _contains(message, [r"换成|改学|转到|方向是"]):
        needs_clarification = True
        question = "你希望把计划调整到哪个新的学习方向？"

    if needs_clarification:
        state["pendingPlanAction"] = plan_action
        state["pendingPlanRequest"] = revision_request
        executable_action = "none"
        dialogue_state = "AWAITING_CLARIFICATION"
    else:
        executable_action = plan_action
        dialogue_state = {
            "PLAN_CREATE": "CREATING_PLAN",
            "PLAN_REVISE": "REVISING_PLAN",
            "PROFILE_DISCOVERY": "COLLECTING_PROFILE",
            "SUBMISSION_REVIEW": "REVIEWING_SUBMISSION",
        }.get(intent, "ANSWERING")

    return {
        "intent": intent,
        "dialogue_state": dialogue_state,
        "plan_action": executable_action,
        "requested_plan_action": plan_action,
        "plan_revision_request": revision_request,
        "revision_scope": scope,
        "needs_clarification": needs_clarification,
        "clarifying_question": question,
        "temporary_state": state,
        "profile_evidence": _rule_profile_evidence(message),
        "memory_summary": build_rolling_summary(existing_summary, message),
    }


def normalize_turn_analysis(data: Any, fallback: dict) -> dict:
    raw = data if isinstance(data, dict) else {}
    result = dict(fallback)
    intent = str(raw.get("intent", fallback.get("intent", "STUDY_QA"))).upper()
    result["intent"] = intent if intent in INTENTS else fallback.get("intent", "STUDY_QA")
    action = str(raw.get("plan_action", fallback.get("plan_action", "none"))).lower()
    result["plan_action"] = action if action in PLAN_ACTIONS else fallback.get("plan_action", "none")
    requested = str(raw.get("requested_plan_action", action)).lower()
    result["requested_plan_action"] = requested if requested in PLAN_ACTIONS else result["plan_action"]
    result["dialogue_state"] = str(raw.get("dialogue_state", fallback.get("dialogue_state", "ANSWERING")))[:40]
    result["needs_clarification"] = bool(raw.get("needs_clarification", False)) or bool(
        fallback.get("needs_clarification", False))
    result["clarifying_question"] = str(
        raw.get("clarifying_question") or fallback.get("clarifying_question", "")
    ).strip()[:300]
    result["plan_revision_request"] = str(raw.get("plan_revision_request", fallback.get("plan_revision_request", ""))).strip()[:1000]
    temporary_state = dict(fallback.get("temporary_state", {}))
    if isinstance(raw.get("temporary_state"), dict):
        temporary_state.update(raw["temporary_state"])
    if result["needs_clarification"]:
        requested_action = result.get("requested_plan_action", "none")
        if requested_action in PLAN_ACTIONS - {"none"}:
            temporary_state["pendingPlanAction"] = requested_action
            temporary_state["pendingPlanRequest"] = str(
                raw.get("plan_revision_request", fallback.get("plan_revision_request", ""))
            )[:1000]
    elif result["plan_action"] in PLAN_ACTIONS - {"none"}:
        temporary_state.pop("pendingPlanAction", None)
        temporary_state.pop("pendingPlanRequest", None)
        temporary_state.pop("pendingQuestion", None)
    result["temporary_state"] = temporary_state
    result["revision_scope"] = raw.get("revision_scope") if isinstance(raw.get("revision_scope"), dict) else fallback.get("revision_scope", {})
    result["memory_summary"] = str(raw.get("memory_summary", fallback.get("memory_summary", ""))).strip()[-1800:]
    if result["intent"] not in {"PLAN_CREATE", "PLAN_REVISE"}:
        result["plan_action"] = "none"
        result["requested_plan_action"] = "none"
    elif result["needs_clarification"]:
        result["requested_plan_action"] = (
            result["requested_plan_action"]
            if result["requested_plan_action"] != "none"
            else fallback.get("requested_plan_action", "none")
        )
        result["plan_action"] = "none"
    elif result["plan_action"] == "none" and fallback.get("plan_action") in PLAN_ACTIONS - {"none"}:
        result["plan_action"] = fallback["plan_action"]
        result["requested_plan_action"] = fallback.get("requested_plan_action", fallback["plan_action"])
    if result["needs_clarification"] and result["requested_plan_action"] in PLAN_ACTIONS - {"none"}:
        result["temporary_state"]["pendingPlanAction"] = result["requested_plan_action"]
        result["temporary_state"]["pendingPlanRequest"] = result["plan_revision_request"]
    elif result["plan_action"] in PLAN_ACTIONS - {"none"}:
        result["temporary_state"].pop("pendingPlanAction", None)
        result["temporary_state"].pop("pendingPlanRequest", None)
    evidence = raw.get("profile_evidence")
    if not isinstance(evidence, list):
        evidence = fallback.get("profile_evidence", [])
    normalized_evidence = []
    allowed_dimensions = {"knowledgeBase", "cognitiveStyle", "weaknessPoints", "learningPace", "interestAreas", "shortTermGoal"}
    for item in evidence[:12]:
        if not isinstance(item, dict) or item.get("dimension") not in allowed_dimensions:
            continue
        try:
            confidence = max(0.0, min(1.0, float(item.get("confidence", 0.5))))
        except (TypeError, ValueError):
            confidence = 0.5
        normalized_evidence.append({
            "dimension": item["dimension"],
            "value": item.get("value"),
            "evidence": str(item.get("evidence", "")).strip()[:500],
            "confidence": confidence,
            "scope": "SHORT_TERM" if str(item.get("scope", "LONG_TERM")).upper() == "SHORT_TERM" else "LONG_TERM",
            "action": str(item.get("action", "retain")).lower()[:20],
        })
    result["profile_evidence"] = normalized_evidence
    return result


def _plan_weeks(plan: Any) -> list[dict]:
    parsed = plan
    if isinstance(plan, str):
        try:
            parsed = json.loads(plan)
        except (TypeError, ValueError, json.JSONDecodeError):
            return []
    if isinstance(parsed, dict):
        parsed = parsed.get("weeks", [])
    return [dict(week) for week in parsed if isinstance(week, dict)] if isinstance(parsed, list) else []


def merge_plan_revision(existing_plan: Any, proposed_plan: Any, action: str, scope: dict | None = None) -> list[dict]:
    existing = _plan_weeks(existing_plan)
    proposed = _plan_weeks(proposed_plan)
    if not proposed:
        return existing
    if not existing or action not in {"modify_week", "adjust_resources"}:
        return proposed
    changed_weeks = {
        int(value) for value in (scope or {}).get("weeks", [])
        if str(value).isdigit() and 1 <= int(value) <= 4
    }
    if not changed_weeks:
        return proposed
    proposed_by_week = {int(w.get("weekNumber", 0)): w for w in proposed}
    merged = []
    for old in existing:
        number = int(old.get("weekNumber", 0))
        merged.append(proposed_by_week.get(number, old) if number in changed_weeks else old)
    return merged


def deterministic_quality_check(response: str, recent_responses: list[str] | None = None) -> dict:
    text = (response or "").strip()
    issues: list[str] = []
    score = 100
    if not text:
        issues.append("empty_response")
        score -= 100
    if _contains(text, [r"你好[！!]我已经分析了你的学习情况", r"根据我们的对话，我了解到你的学习目标"]):
        issues.append("template_repetition")
        score -= 35
    if len(text) > 5000:
        issues.append("excessive_length")
        score -= 10
    if len(re.findall(r"知识基础.{0,8}/10|学习风格|薄弱点|短期目标", text)) >= 3:
        issues.append("profile_dump")
        score -= 20
    max_similarity = 0.0
    for previous in (recent_responses or [])[-3:]:
        if previous:
            max_similarity = max(max_similarity, SequenceMatcher(None, text, previous).ratio())
    if max_similarity >= 0.82:
        issues.append("high_response_similarity")
        score -= 30
    return {
        "score": max(0, score),
        "issues": issues,
        "max_similarity": round(max_similarity, 3),
        "needs_revision": score < 80,
    }
