"""Knowledge-grounded practice-question generation pipeline."""

from __future__ import annotations

import difflib
import json
import logging
import os
import re
from typing import Any

logger = logging.getLogger(__name__)

QUESTION_TYPE_RULES = {
    "SINGLE_CHOICE": "四个互斥选项，只有一个正确答案；correctAnswer 只能是 A/B/C/D",
    "MULTIPLE_CHOICE": "四个独立选项，至少两个正确答案；correctAnswer 使用排序后的字母，如 A,C",
    "TRUE_FALSE": "options 固定为 [\"正确\",\"错误\"]；correctAnswer 只能是 A 或 B",
    "SHORT_ANSWER": "options 必须为空数组；correctAnswer 是 3-5 个用中文分号分隔的评分关键词",
}

DIFFICULTY_RULES = {
    "EASY": "单一知识点；直接理解或一步应用；不设置语言陷阱；目标为记忆/理解层级",
    "MEDIUM": "组合一至两个知识点；要求代码阅读、计算、情境应用或原因分析；目标为应用/分析层级",
    "HARD": "多步骤推理；包含调试、边界条件、方案权衡或知识迁移；目标为分析/评价/创造层级",
}

COURSE_TEMPLATES = {
    "programming": "优先使用代码阅读、运行结果、调试、API 选择、边界条件或小型实现题；代码必须可运行且语言明确",
    "database": "优先使用 SQL 结果、表结构设计、范式、索引、事务隔离或查询优化情境；给足表结构和数据前提",
    "data_structure": "优先使用操作序列、复杂度比较、结构变化、算法跟踪或边界用例；明确输入规模和操作约束",
    "mathematics": "优先使用计算、推导、性质辨析、反例或证明思路；符号定义完整，数值应可核算",
    "systems": "优先使用进程线程、调度、同步、内存、文件系统、指令执行或 Linux 命令情境；明确系统假设",
    "network": "优先使用协议交互、分层、地址与子网、报文分析、时序或故障定位；明确拓扑和协议条件",
    "compiler": "优先使用词法/语法分析、文法、语义检查、中间代码或优化过程；明确文法和输入串",
    "software_engineering": "优先使用需求、建模、架构、设计模式、测试、版本管理或项目权衡案例；避免只考术语背诵",
    "mobile_game": "优先使用生命周期、交互、渲染、资源管理、性能和平台约束场景；给出可验证的工程条件",
    "general": "围绕概念理解、实际应用、错误诊断和迁移设计题目，避免空泛的学习方法题",
}


def _call(client, system_prompt: str, user_prompt: str) -> str:
    from langchain_core.messages import HumanMessage, SystemMessage

    response = client.invoke([
        SystemMessage(content=system_prompt),
        HumanMessage(content=user_prompt),
    ])
    return response.content


def _extract_json(text: str) -> str:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?\s*", "", text)
        text = re.sub(r"\s*```$", "", text)
    array_start, object_start = text.find("["), text.find("{")
    starts = [pos for pos in (array_start, object_start) if pos >= 0]
    if not starts:
        raise ValueError("模型响应中没有 JSON")
    start = min(starts)
    end = max(text.rfind("]"), text.rfind("}"))
    if end < start:
        raise ValueError("模型响应中的 JSON 不完整")
    return text[start:end + 1]


def _json_items(raw: str, key: str) -> list[dict]:
    parsed = json.loads(_extract_json(raw))
    if isinstance(parsed, dict):
        parsed = parsed.get(key, [])
    return [item for item in parsed if isinstance(item, dict)] if isinstance(parsed, list) else []


def infer_course_family(text: str) -> str:
    lowered = text.lower()
    groups = [
        ("database", ["数据库", "sql", "mysql", "事务", "索引", "javaee", "jpa"]),
        ("data_structure", ["数据结构", "算法", "链表", "树", "图论", "排序", "复杂度"]),
        ("mathematics", ["离散数学", "线性代数", "概率", "数理统计", "矩阵", "微积分", "集合", "数理逻辑"]),
        ("network", ["计算机网络", "tcp", "udp", "http", "子网", "路由", "协议"]),
        ("compiler", ["编译原理", "词法", "语法分析", "文法", "中间代码"]),
        ("systems", ["操作系统", "linux", "计算机组成", "进程", "线程", "内存", "指令", "文件系统"]),
        ("mobile_game", ["移动应用", "android", "ios", "游戏软件", "unity", "渲染", "生命周期"]),
        ("software_engineering", ["软件工程", "需求", "uml", "架构", "设计模式", "测试", "git", "项目管理"]),
        ("programming", ["c++", "c语言", "java", "python", "编程", "程序", "代码", "函数", "类", "指针"]),
    ]
    for family, keywords in groups:
        if any(keyword in lowered for keyword in keywords):
            return family
    return "general"


def _safe_int(value: Any, fallback: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return fallback


def _source_ids(value: Any, allowed: set[int]) -> list[int]:
    if not isinstance(value, list):
        return []
    result: list[int] = []
    for raw_id in value:
        chunk_id = _safe_int(raw_id, -1)
        if chunk_id in allowed and chunk_id not in result:
            result.append(chunk_id)
    return result


def _local_blueprints(count: int, question_type: str, difficulty: str,
                      task_title: str, family: str, source_ids: list[int]) -> list[dict]:
    methods = {
        "programming": ["代码阅读", "错误诊断", "边界应用"],
        "database": ["查询分析", "设计判断", "事务情境"],
        "data_structure": ["过程跟踪", "复杂度分析", "边界用例"],
        "mathematics": ["性质辨析", "计算推导", "反例分析"],
        "systems": ["执行过程", "故障定位", "方案权衡"],
        "network": ["协议时序", "报文分析", "故障定位"],
        "compiler": ["分析过程", "结果推导", "错误诊断"],
        "software_engineering": ["案例判断", "方案权衡", "实践应用"],
        "mobile_game": ["场景应用", "性能诊断", "工程权衡"],
        "general": ["概念理解", "实际应用", "错误诊断"],
    }
    levels = {"EASY": "理解", "MEDIUM": "应用", "HARD": "分析与评价"}
    return [{
        "knowledgePoint": task_title[:120],
        "learningObjective": f"能够通过{methods[family][index % 3]}掌握{task_title}"[:240],
        "cognitiveLevel": levels[difficulty],
        "misconception": "只记结论而忽略适用条件",
        "assessmentMethod": methods[family][index % 3],
        "questionType": question_type,
        "difficulty": difficulty,
        "sourceChunkIds": source_ids[:3],
    } for index in range(count)]


def _build_blueprints(req, count: int, question_type: str, difficulty: str,
                      family: str, profile: dict, context: str,
                      allowed_ids: set[int], reviewer) -> list[dict]:
    fallback = _local_blueprints(
        count, question_type, difficulty, req.task_title, family, sorted(allowed_ids))
    # 默认使用稳定、零延迟的规则蓝图，把模型预算留给命题和独立审核。
    # 需要实验 LLM 蓝图时可显式开启，但不影响后续蓝图字段和校验协议。
    if os.getenv("QUESTION_LLM_BLUEPRINT_ENABLED", "false").lower() != "true":
        return fallback
    grounding_rule = (
        "知识库内容非空，知识点、结论和 sourceChunkIds 必须能从知识库直接得到；不得补造资料中没有的事实。"
        if context else "知识库暂未命中，可使用该课程公认基础知识，但 sourceChunkIds 必须为空。"
    )
    prompt = f"""请先设计 {count} 个互不重复的出题蓝图，不要直接写题目。
本周主题：{req.week_topic}
小任务：{req.task_title}
课程类别：{family}
课程出题模板：{COURSE_TEMPLATES[family]}
题型：{question_type}；{QUESTION_TYPE_RULES[question_type]}
难度：{difficulty}；{DIFFICULTY_RULES[difficulty]}
学生基础：{profile.get('knowledgeBase', 5)}/10
薄弱点：{'、'.join(profile.get('weaknessPoints', [])) or '暂无明确薄弱点'}
规则：{grounding_rule}

知识库内容：
{context or '（无检索结果）'}

只输出 JSON 数组。每项必须包含 knowledgePoint、learningObjective、cognitiveLevel、misconception、assessmentMethod、questionType、difficulty、sourceChunkIds。蓝图之间必须覆盖不同知识点、误区或考查方式。"""
    try:
        items = _json_items(_call(
            reviewer,
            "你是软件工程专业课程的测评设计师。先建立可验证的测评蓝图，再允许出题。",
            prompt,
        ), "blueprints")
        normalized: list[dict] = []
        for index, item in enumerate(items[:count]):
            base = fallback[index]
            knowledge_point = str(item.get("knowledgePoint", "")).strip()
            objective = str(item.get("learningObjective", "")).strip()
            if not knowledge_point or not objective:
                continue
            sources = _source_ids(item.get("sourceChunkIds"), allowed_ids)
            if context and not sources:
                sources = base["sourceChunkIds"]
            normalized.append({
                "knowledgePoint": knowledge_point[:120],
                "learningObjective": objective[:240],
                "cognitiveLevel": str(item.get("cognitiveLevel", base["cognitiveLevel"]))[:40],
                "misconception": str(item.get("misconception", base["misconception"]))[:180],
                "assessmentMethod": str(item.get("assessmentMethod", base["assessmentMethod"]))[:100],
                "questionType": question_type,
                "difficulty": difficulty,
                "sourceChunkIds": sources,
            })
        if normalized:
            normalized.extend(fallback[len(normalized):count])
            return normalized[:count]
    except Exception as exc:
        logger.warning("出题蓝图生成失败，使用确定性蓝图: %s", exc)
    return fallback


def _answer_letters(answer: str) -> list[str]:
    return sorted(set(part for part in re.split(r"[,，、\s]+", answer.upper().strip()) if part))


def _content_key(text: str) -> str:
    return re.sub(r"[^\w\u4e00-\u9fff]+", "", str(text or "").lower())


def _question_repeats_option(question: str, options: list[str]) -> bool:
    """Reject an option that accidentally copies most or all of its own question stem."""
    question_key = _content_key(question)
    for option in options:
        option_key = _content_key(option)
        if len(option_key) < 4 or not question_key:
            continue
        shorter, longer = sorted((len(option_key), len(question_key)))
        length_ratio = shorter / max(longer, 1)
        similarity = difflib.SequenceMatcher(None, question_key, option_key).ratio()
        if question_key == option_key:
            return True
        if length_ratio >= 0.65 and similarity >= 0.86:
            return True
        if length_ratio >= 0.78 and (option_key in question_key or question_key in option_key):
            return True
    return False


def _has_opaque_source_reference(text: str) -> bool:
    """Reject retrieval labels that are meaningless outside the internal prompt."""
    compact = re.sub(r"\s+", "", str(text or ""))
    return re.search(
        r"(?:资料|材料|文档|知识片段)(?:编号)?[一二三四五六七八九十\d]+|"
        r"(?:上述|下列|给定|前述)(?:资料|材料|文档|知识片段)", compact) is not None


def normalize_candidate(item: dict, question_type: str, allowed_ids: set[int],
                        fallback_blueprint: dict | None = None,
                        require_sources: bool = False) -> dict | None:
    blueprint = fallback_blueprint or {}
    question = str(item.get("question", "")).strip()
    explanation = str(item.get("explanation", "")).strip()
    if len(question) < 6 or len(explanation) < 8 or len(question) > 2000:
        return None
    if re.search(r"(?:正确答案|答案)\s*(?:是|为|[:：])", question):
        return None
    if _has_opaque_source_reference(question):
        return None
    raw_options = item.get("options", [])
    if not isinstance(raw_options, list):
        return None
    options = [str(option).strip()[:500] for option in raw_options if str(option).strip()]
    answer = ",".join(_answer_letters(str(item.get("correctAnswer", ""))))

    if question_type == "SHORT_ANSWER":
        options = []
        keywords = [part.strip() for part in re.split(
            r"[;；,，、\n]+", str(item.get("correctAnswer", ""))) if len(part.strip()) >= 2]
        keywords = list(dict.fromkeys(keywords))[:5]
        if len(keywords) < 3:
            return None
        answer = "；".join(keywords)
    elif question_type == "TRUE_FALSE":
        if answer not in {"A", "B"}:
            return None
        options = ["正确", "错误"]
    else:
        option_keys = [_content_key(option) for option in options]
        if (len(options) != 4 or any(not key for key in option_keys)
                or len(set(option_keys)) != 4
                or _question_repeats_option(question, options)
                or any(_has_opaque_source_reference(option) for option in options)):
            return None
        letters = _answer_letters(answer)
        if any(letter not in {"A", "B", "C", "D"} for letter in letters):
            return None
        if question_type == "SINGLE_CHOICE" and len(letters) != 1:
            return None
        if question_type == "MULTIPLE_CHOICE" and len(letters) < 2:
            return None
        answer = ",".join(letters)

    source_ids = _source_ids(
        item.get("sourceChunkIds", blueprint.get("sourceChunkIds", [])), allowed_ids)
    if require_sources and not source_ids:
        return None
    knowledge_point = str(item.get(
        "knowledgePoint", blueprint.get("knowledgePoint", ""))).strip()[:255]
    learning_objective = str(item.get(
        "learningObjective", blueprint.get("learningObjective", ""))).strip()[:500]
    cognitive_level = str(item.get(
        "cognitiveLevel", blueprint.get("cognitiveLevel", ""))).strip()[:64]
    if not knowledge_point or not learning_objective or not cognitive_level:
        return None
    quality = _safe_int(item.get("qualityScore"), 0)
    if quality <= 0:
        quality = 65 + (8 if source_ids else 0) + (5 if len(explanation) >= 40 else 0)
    return {
        "question": question,
        "options": options,
        "correctAnswer": answer[:1000],
        "explanation": explanation[:3000],
        "knowledgePoint": knowledge_point,
        "learningObjective": learning_objective,
        "cognitiveLevel": cognitive_level,
        "sourceChunkIds": source_ids,
        "qualityScore": max(0, min(100, quality)),
    }


def _stem_key(question: str) -> str:
    return _content_key(question)


def _option_key_set(item: dict) -> set[str]:
    options = item.get("options", [])
    if not isinstance(options, list) or len(options) < 3:
        return set()
    return {key for option in options if (key := _content_key(option))}


def _options_repeat(left: set[str], right: set[str]) -> bool:
    if len(left) < 3 or len(right) < 3:
        return False
    overlap = len(left & right)
    return left == right or overlap / min(len(left), len(right)) >= 0.75


def deduplicate(items: list[dict], limit: int, blocked: list[dict] | None = None) -> list[dict]:
    unique: list[dict] = []
    blocked = blocked or []
    keys: list[str] = [_stem_key(item.get("question", "")) for item in blocked]
    option_sets: list[set[str]] = [_option_key_set(item) for item in blocked]
    for item in items:
        key = _stem_key(item["question"])
        options = _option_key_set(item)
        if not key or any(key == existing or difflib.SequenceMatcher(
                None, key, existing).ratio() >= 0.88 for existing in keys):
            continue
        if options and any(_options_repeat(options, existing) for existing in option_sets):
            continue
        unique.append(item)
        keys.append(key)
        option_sets.append(options)
        if len(unique) >= limit:
            break
    return unique


def _generate_candidates(req, blueprints: list[dict], question_type: str,
                         difficulty: str, family: str, context: str,
                         allowed_ids: set[int], generator,
                         blocked: list[dict] | None = None) -> list[dict]:
    grounding_rule = (
        "所有事实、代码语义和答案依据必须来自给定知识库；sourceChunkIds 只能引用蓝图或知识库中出现的 ID。"
        if context else "可使用课程公认基础知识，但不要虚构标准、版本或来源，sourceChunkIds 保持为空。"
    )
    prompt = f"""根据以下蓝图逐项生成候选题，每个蓝图生成且只生成一道题。
课程类别：{family}
课程模板：{COURSE_TEMPLATES[family]}
本周主题：{req.week_topic}
小任务：{req.task_title}
题型规则：{QUESTION_TYPE_RULES[question_type]}
难度规则：{DIFFICULTY_RULES[difficulty]}
事实约束：{grounding_rule}

蓝图：{json.dumps(blueprints, ensure_ascii=False)}

知识库：{context or '（无检索结果）'}

需要避开的历史题目与选项：{json.dumps(blocked or [], ensure_ascii=False)}

只输出 JSON 数组。每项包含 question、options、correctAnswer、explanation、knowledgePoint、learningObjective、cognitiveLevel、sourceChunkIds。题干必须自包含，脱离检索上下文也能独立作答；知识库中的资料编号和 chunkId 仅供内部溯源，严禁在题干或选项中出现“资料1、资料3、上述材料、给定文档”等不可见指代，必要事实必须直接写入题干；答案唯一且可验证；干扰项来自真实误区且句式长度接近；解析同时说明正确原因及关键错误项为何错误；不得在题干泄露答案；任何选项不得复述题干；不同题目不得复用相同或仅改变顺序的选项集合；题目之间不得换词重复。"""
    items = _json_items(_call(
        generator,
        "你是严谨的课程命题教师。严格依照测评蓝图和给定知识，不得用空泛学习方法替代专业题目。",
        prompt,
    ), "questions")
    normalized: list[dict] = []
    for index, item in enumerate(items):
        blueprint = blueprints[min(index, len(blueprints) - 1)]
        candidate = normalize_candidate(item, question_type, allowed_ids, blueprint, bool(context))
        if candidate:
            normalized.append(candidate)
    return deduplicate(normalized, len(blueprints), blocked)


def _review_candidates(req, candidates: list[dict], count: int,
                       question_type: str, difficulty: str, context: str,
                       allowed_ids: set[int], reviewer,
                       blocked: list[dict] | None = None) -> list[dict]:
    # 隐藏生成器给出的答案和解析，让审核智能体先独立作答，避免照抄原答案。
    audit_items = [{
        "candidateIndex": index,
        "question": item["question"],
        "options": item["options"],
        "knowledgePoint": item["knowledgePoint"],
        "sourceChunkIds": item["sourceChunkIds"],
    } for index, item in enumerate(candidates)]
    prompt = f"""请对以下候选题进行盲审：你看不到命题者给出的答案，必须独立求解后判断是否可用。
审核维度：与周主题和小任务直接相关；符合题型和难度；题目必须自包含，不得用“资料1、资料3、上述材料、给定文档”等用户不可见的检索标签代替事实；答案唯一且可由题干和知识库证明；有知识库时事实和 sourceChunkIds 均可追溯；选项不得复述题干；不同题目不得复用相同或高度重合的选项集合。
题型规则：{QUESTION_TYPE_RULES[question_type]}
难度规则：{DIFFICULTY_RULES[difficulty]}
周主题：{req.week_topic}
小任务：{req.task_title}
知识库：{context or '（无检索结果）'}
候选题：{json.dumps(audit_items, ensure_ascii=False)}

只输出 JSON 数组，每项包含 candidateIndex、verifiedAnswer、verdict、confidence、reason。verdict 只能是 PASS 或 FAIL；confidence 为 0-100；选择题 verifiedAnswer 使用 A 或 A,C，简答题给出评分关键词。"""
    items = _json_items(_call(
        reviewer,
        "你是独立解题与试题审核员。不得猜测命题者答案，必须先自行求解，再给出可验证结论。",
        prompt,
    ), "verifications")
    verified: list[dict] = []
    for audit in items:
        index = _safe_int(audit.get("candidateIndex"), -1)
        if index < 0 or index >= len(candidates):
            continue
        if str(audit.get("verdict", "")).upper() != "PASS" or _safe_int(audit.get("confidence"), 0) < 65:
            continue
        candidate = candidates[index]
        proposed = str(audit.get("verifiedAnswer", "")).strip()
        if question_type == "SHORT_ANSWER":
            expected = {_content_key(part) for part in re.split(r"[;；,，、\s]+", candidate["correctAnswer"]) if part}
            solved = {_content_key(part) for part in re.split(r"[;；,，、\s]+", proposed) if part}
            if not expected or len(expected & solved) / len(expected) < 0.6:
                continue
        elif ",".join(_answer_letters(proposed)) != candidate["correctAnswer"]:
            continue
        accepted = dict(candidate)
        accepted["qualityScore"] = max(accepted.get("qualityScore", 0), _safe_int(audit.get("confidence"), 0))
        verified.append(accepted)
    return deduplicate(verified, count, blocked)


def generate_questions_pipeline(req, generator, reviewer) -> list[dict]:
    """Run retrieval-grounded blueprint, generation, review, validation and deduplication."""
    count = max(1, min(req.count, 10))
    question_type = req.question_type.upper().strip()
    if question_type not in QUESTION_TYPE_RULES:
        question_type = "SINGLE_CHOICE"
    difficulty = req.difficulty.upper().strip()
    if difficulty not in DIFFICULTY_RULES:
        difficulty = "MEDIUM"
    try:
        profile = json.loads(req.profile_json) if req.profile_json else {}
        if not isinstance(profile, dict):
            profile = {}
    except json.JSONDecodeError:
        profile = {}

    context = (req.knowledge_context or "").strip()[:12000]
    allowed_ids = set(req.source_chunk_ids[:12])
    family = infer_course_family(f"{req.week_topic} {req.task_title} {context[:1200]}")
    blocked = [{"question": text, "options": req.avoid_option_sets[index]
                if index < len(req.avoid_option_sets) else []}
               for index, text in enumerate(req.avoid_question_texts[:40]) if str(text).strip()]
    candidate_count = min(12, max(count + 2, int(count * 1.5)))
    blueprints = _build_blueprints(
        req, candidate_count, question_type, difficulty, family,
        profile, context, allowed_ids, reviewer)
    try:
        candidates = _generate_candidates(
            req, blueprints, question_type, difficulty, family,
            context, allowed_ids, generator, blocked)
        if not candidates:
            return []
        try:
            reviewed = _review_candidates(
                req, candidates, count, question_type, difficulty,
                context, allowed_ids, reviewer, blocked)
        except Exception as exc:
            logger.warning("候选题审核失败，使用已通过规则校验的候选题: %s", exc)
            reviewed = None
        final = deduplicate(candidates if reviewed is None else reviewed, count, blocked)
        logger.info(
            "出题流水线完成: course=%s blueprint=%d candidate=%d reviewed=%d final=%d",
            family, len(blueprints), len(candidates), len(reviewed or []), len(final),
        )
        return final
    except Exception as exc:
        logger.warning("题目生成流水线失败，将由 Spring 服务降级: %s", exc)
        return []
