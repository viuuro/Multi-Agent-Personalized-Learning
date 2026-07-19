"""Verified, concrete learning-resource recommendation."""

from __future__ import annotations

from copy import deepcopy
from html import unescape
import math
import re
import threading
import time
from typing import Any

BILIBILI_SEARCH_API = "https://api.bilibili.com/x/web-interface/search/all/v2"
BILIBILI_VIEW_API = "https://api.bilibili.com/x/web-interface/view"
CACHE_TTL_SECONDS = 60 * 60

STOP_TERMS = {
    "学习", "教程", "基础", "入门", "进阶", "实战", "应用", "掌握", "理解",
    "本周", "第一周", "第二周", "第三周", "第四周", "第1周", "第2周", "第3周", "第4周",
}

PLACEHOLDER_TERMS = {
    "待评估", "待确定", "尚未确定", "未确定", "未知", "暂无", "无", "未设置",
    "探索学习方向", "相关内容", "相关课程", "综合学习",
}

TECH_TERMS = [
    "C++", "C语言", "Java EE", "JavaEE", "Java", "Python", "TypeScript", "JavaScript",
    "Spring Boot", "Spring", "Vue", "React", "MySQL", "SQL", "数据库", "数据结构", "算法",
    "离散数学", "线性代数", "概率论", "数理统计", "计算机组成原理", "操作系统", "Linux",
    "计算机网络", "编译原理", "移动应用", "Android", "游戏软件", "Unity", "软件工程",
    "设计模式", "Git", "Docker",
]

COURSE_CHAPTER_TERMS = [
    "复杂度", "线性表", "链表", "栈", "队列", "数组", "字符串", "KMP", "二叉树",
    "遍历", "平衡树", "堆", "散列表", "哈希", "图", "最短路径", "拓扑排序", "排序",
    "查找", "递归", "动态规划", "并查集", "Trie", "B树", "B+树",
    "数据表示", "补码", "浮点数", "运算器", "ALU", "存储系统", "Cache", "地址映射",
    "虚拟存储", "指令系统", "寻址方式", "数据通路", "控制器", "流水线", "冒险",
    "总线", "中断", "DMA", "数字逻辑", "并行", "性能评估",
]

# 所有 URL 都是具体课程、教材、教程或实践站点，不包含搜索结果页。
CURATED_RESOURCES = [
    ({"c语言"}, "C 语言参考手册", "https://zh.cppreference.com/w/c/language", "cppreference", "article"),
    ({"c++", "cpp"}, "LearnCpp：C++ 系统教程", "https://www.learncpp.com/", "LearnCpp", "course"),
    ({"java"}, "Dev.java：Java 官方学习路径", "https://dev.java/learn/", "Oracle Java", "course"),
    ({"java ee", "javaee", "jakarta"}, "Jakarta EE 官方教程", "https://jakarta.ee/learn/", "Jakarta EE", "course"),
    ({"python"}, "Python 3 官方中文教程", "https://docs.python.org/zh-cn/3/tutorial/", "Python Docs", "course"),
    ({"数据库", "mysql", "sql"}, "MySQL 官方入门教程", "https://dev.mysql.com/doc/refman/8.4/en/tutorial.html", "MySQL", "course"),
    ({"数据结构", "算法"}, "VisuAlgo：数据结构与算法可视化", "https://visualgo.net/zh", "VisuAlgo", "practice"),
    ({"算法"}, "OI Wiki：算法与数据结构知识库", "https://oi-wiki.org/", "OI Wiki", "article"),
    ({"数据结构", "算法", "线性表", "链表", "栈", "队列", "树", "图", "排序", "查找"},
     "OpenDSA：数据结构与算法交互教材", "https://opendsa-server.cs.vt.edu/home/books", "OpenDSA", "course"),
    ({"数据结构", "算法", "排序", "查找", "图", "字符串"},
     "Algorithms 4e：算法、代码与练习", "https://algs4.cs.princeton.edu/home/", "Princeton", "course"),
    ({"离散数学"}, "Discrete Mathematics: An Open Introduction", "https://discrete.openmathbooks.org/dmoi3.html", "Open Math Books", "course"),
    ({"线性代数", "矩阵"}, "MIT 18.06 Linear Algebra 课程", "https://ocw.mit.edu/courses/18-06-linear-algebra-spring-2010/", "MIT OpenCourseWare", "course"),
    ({"概率论", "数理统计", "统计"}, "OpenIntro Statistics 免费教材", "https://www.openintro.org/book/os/", "OpenIntro", "course"),
    ({"计算机组成原理", "计算机组成"}, "Nand2Tetris：从逻辑门到计算机系统", "https://www.nand2tetris.org/course", "Nand2Tetris", "course"),
    ({"计算机组成原理", "计算机组成", "数据表示", "存储系统", "cache", "指令系统", "流水线"},
     "CS:APP：程序员视角的计算机系统", "https://csapp.cs.cmu.edu/", "CMU", "course"),
    ({"计算机组成原理", "计算机组成", "cache", "指令系统", "数据通路", "流水线", "中断"},
     "CS61C：计算机体系结构课程与练习", "https://cs61c.org/", "UC Berkeley", "course"),
    ({"操作系统"}, "OSTEP：Operating Systems: Three Easy Pieces", "https://pages.cs.wisc.edu/~remzi/OSTEP/", "OSTEP", "course"),
    ({"linux"}, "Linux Journey 系统学习路径", "https://linuxjourney.com/", "Linux Journey", "course"),
    ({"计算机网络", "网络", "tcp", "udp"}, "Computer Networking 在线课程讲义与视频", "https://gaia.cs.umass.edu/kurose_ross/online_lectures.htm", "UMass", "course"),
    ({"编译原理", "编译器"}, "Crafting Interpreters 在线教材", "https://craftinginterpreters.com/contents.html", "Crafting Interpreters", "course"),
    ({"软件工程"}, "SWEBOK：软件工程知识体系", "https://www.computer.org/education/bodies-of-knowledge/software-engineering", "IEEE Computer Society", "article"),
    ({"软件工程"}, "Software Engineering at Google 在线书籍", "https://abseil.io/resources/swe-book/html/toc.html", "Google", "course"),
    ({"git", "版本控制"}, "Pro Git 官方中文电子书", "https://git-scm.com/book/zh/v2", "Git", "course"),
    ({"spring", "spring boot"}, "Spring 官方入门指南", "https://spring.io/guides", "Spring", "course"),
    ({"vue"}, "Vue 官方互动教程", "https://cn.vuejs.org/tutorial/", "Vue", "course"),
    ({"移动应用", "android"}, "Android Basics with Compose 官方课程", "https://developer.android.com/courses/android-basics-compose/course", "Android Developers", "course"),
    ({"游戏软件", "unity"}, "Unity Essentials 官方学习路径", "https://learn.unity.com/pathway/unity-essentials", "Unity Learn", "course"),
    ({"设计模式"}, "Refactoring.Guru 设计模式教程", "https://refactoringguru.cn/design-patterns", "Refactoring.Guru", "course"),
    ({"web", "前端", "javascript"}, "MDN Web 开发学习路径", "https://developer.mozilla.org/zh-CN/docs/Learn_web_development", "MDN", "course"),
]

GENERAL_RESOURCES = [
    {"title": "CS50x：计算机科学导论", "url": "https://cs50.harvard.edu/x/", "platform": "Harvard CS50", "type": "course"},
    {"title": "MIT 6.0001：计算机科学与 Python 编程导论", "url": "https://ocw.mit.edu/courses/6-0001-introduction-to-computer-science-and-programming-in-python-fall-2016/", "platform": "MIT OpenCourseWare", "type": "course"},
]

_cache: dict[str, tuple[float, list[dict]]] = {}
_cache_lock = threading.Lock()


def parse_human_count(value: Any) -> int:
    """Parse Bilibili values such as 1234, '1.2万' and '--'."""
    if isinstance(value, (int, float)):
        return max(0, int(value))
    text = str(value or "").replace(",", "").strip().lower()
    if not text or text == "--":
        return 0
    multiplier = 1
    if text.endswith("万"):
        multiplier, text = 10_000, text[:-1]
    elif text.endswith("亿"):
        multiplier, text = 100_000_000, text[:-1]
    try:
        return max(0, round(float(text) * multiplier))
    except ValueError:
        return 0


def contains_placeholder(value: str) -> bool:
    compact = re.sub(r"\s+", "", str(value or "")).lower()
    return any(term.lower() in compact for term in PLACEHOLDER_TERMS)


def resolve_topic(topic: str, tasks: list[str] | None = None, context: str = "") -> str:
    """Replace profile placeholders with an evidenced technology or a useful project default."""
    clean_topic = re.sub(r"第\s*[一二三四1-4]\s*周[:：]?", "", str(topic or "")).strip()
    if clean_topic and not contains_placeholder(clean_topic):
        return clean_topic
    evidence = " ".join([*(tasks or []), (context or "")[-1600:]]).lower()
    for term in TECH_TERMS:
        if term.lower() in evidence:
            return term
    return "软件工程基础"


def build_focus(topic: str, tasks: list[str] | None = None, context: str = "") -> tuple[str, list[str]]:
    """Build a concise search query and a stable relevance term list."""
    topic = resolve_topic(topic, tasks, context)
    # 知识库上下文中包含课程名与完整章节路径；保留更大的尾部窗口以提取章节级关键词。
    source = " ".join([topic, *(tasks or [])[:3], (context or "")[-5000:]])
    found: list[str] = []
    lowered = source.lower()
    for term in TECH_TERMS:
        if term.lower() in lowered and term.lower() not in {item.lower() for item in found}:
            found.append(term)
    for term in COURSE_CHAPTER_TERMS:
        if term.lower() in lowered and term.lower() not in {item.lower() for item in found}:
            found.append(term)
    fragments = re.findall(r"[A-Za-z][A-Za-z0-9+#.]{1,24}|[\u4e00-\u9fff]{2,10}", topic or "")
    for fragment in fragments:
        cleaned = re.sub(r"第[一二三四1-4]周", "", fragment).strip()
        if (cleaned and cleaned.lower() not in STOP_TERMS and not contains_placeholder(cleaned)
                and cleaned.lower() not in {item.lower() for item in found}):
            found.append(cleaned)
    terms = found[:6] or [re.sub(r"\s+", " ", topic or "计算机科学").strip()]
    query = " ".join(terms[:4])
    return query[:100], terms


def relevance_score(title: str, terms: list[str]) -> float:
    lowered = title.lower()
    score = 0.0
    for index, term in enumerate(terms):
        if term.lower() in lowered:
            score += 10.0 if index == 0 else 5.0
    if any(noisy in lowered for noisy in ("广告", "招生", "面试八股", "速成包会")):
        score -= 12.0
    return score


def _request_json(session: Any, url: str, params: dict, timeout: int = 8) -> dict:
    response = session.get(url, params=params, timeout=timeout)
    response.raise_for_status()
    data = response.json()
    return data if isinstance(data, dict) else {}


def _search_bilibili(query: str, terms: list[str], limit: int,
                      session: Any) -> list[dict]:
    """Search, verify through the view API, score and return direct BV links."""
    payload = _request_json(session, BILIBILI_SEARCH_API, {
        "keyword": f"{query} 教程",
        "page": 1,
        "pagesize": 20,
        "order": "click",
    })
    if payload.get("code") != 0:
        return []
    groups = payload.get("data", {}).get("result", [])
    raw_candidates: list[dict] = []
    for group in groups if isinstance(groups, list) else []:
        if not isinstance(group, dict) or group.get("result_type") != "video":
            continue
        for item in group.get("data", []) if isinstance(group.get("data"), list) else []:
            bvid = str(item.get("bvid", "")).strip()
            title = re.sub(r"<[^>]+>", "", unescape(str(item.get("title", "")))).strip()
            if not re.fullmatch(r"BV[0-9A-Za-z]{10}", bvid) or not title:
                continue
            raw_candidates.append({
                "bvid": bvid,
                "title": title,
                "search_views": parse_human_count(item.get("play")),
                "search_score": relevance_score(title, terms),
            })
    raw_candidates.sort(
        key=lambda item: (item["search_score"], item["search_views"]), reverse=True)

    verified: list[dict] = []
    seen: set[str] = set()
    for candidate in raw_candidates[:8]:
        if candidate["bvid"] in seen:
            continue
        seen.add(candidate["bvid"])
        try:
            detail = _request_json(session, BILIBILI_VIEW_API, {"bvid": candidate["bvid"]}, timeout=6)
            video = detail.get("data", {}) if detail.get("code") == 0 else {}
            if not isinstance(video, dict) or str(video.get("bvid", "")) != candidate["bvid"]:
                continue
            title = str(video.get("title", candidate["title"])).strip()
            description = str(video.get("desc", "")).strip()
            category = str(video.get("tname_v2") or video.get("tname") or "").strip()
            page_titles = " ".join(str(page.get("part", "")) for page in (video.get("pages") or [])
                                   if isinstance(page, dict))
            duration = parse_human_count(video.get("duration"))
            stats = video.get("stat") or {}
            views = parse_human_count(stats.get("view"))
            likes = parse_human_count(stats.get("like"))
            coins = parse_human_count(stats.get("coin"))
            favorites = parse_human_count(stats.get("favorite"))
            owner = str((video.get("owner") or {}).get("name", "")).strip()
            content_text = " ".join((title, description, category, page_titles, owner))
            title_score = relevance_score(title, terms)
            content_score = relevance_score(content_text, terms)
            if title_score <= 0 and content_score < 8:
                continue
            score = title_score + min(18.0, content_score * 0.55)
            score += min(30.0, math.log10(max(views, 1)) * 4.5)
            engagement = likes + coins * 2 + favorites * 2
            score += min(12.0, engagement / max(views, 1) * 100.0)
            if 180 <= duration <= 8 * 60 * 60:
                score += 4.0
            if page_titles and len(page_titles) >= 20:
                score += 3.0
            if score < 7:
                continue
            verified.append({
                "title": f"【B站】{title}" + (f" · {owner}" if owner else ""),
                "url": f"https://www.bilibili.com/video/{candidate['bvid']}",
                "platform": "B站",
                "type": "video",
                "_score": score,
            })
        except Exception:
            continue
    verified.sort(key=lambda item: item["_score"], reverse=True)
    return verified[:limit]


def _curated_candidates(focus_text: str, terms: list[str]) -> list[dict]:
    lowered = focus_text.lower()
    ranked: list[dict] = []
    for keywords, title, url, platform, resource_type in CURATED_RESOURCES:
        matches = sum(1 for keyword in keywords if keyword in lowered)
        if matches == 0:
            continue
        ranked.append({
            "title": title,
            "url": url,
            "platform": platform,
            "type": resource_type,
            "_score": 18.0 * matches + relevance_score(title, terms),
        })
    ranked.sort(key=lambda item: item["_score"], reverse=True)
    return ranked


def _is_concrete_url(url: str, platform: str = "") -> bool:
    lowered = url.lower()
    search_markers = ("/search", "search?", "search.htm", "google.com/search", "?q=")
    if not lowered.startswith("https://") or any(marker in lowered for marker in search_markers):
        return False
    if "bilibili" in lowered or "b站" in platform.lower():
        return re.fullmatch(r"https://www\.bilibili\.com/video/BV[0-9A-Za-z]{10}/?", url) is not None
    return True


def _select(candidates: list[dict], count: int) -> list[dict]:
    selected: list[dict] = []
    seen_urls: set[str] = set()
    platform_counts: dict[str, int] = {}
    for candidate in sorted(candidates, key=lambda item: item.get("_score", 0), reverse=True):
        url = str(candidate.get("url", "")).rstrip("/")
        platform = str(candidate.get("platform", ""))
        if (candidate.get("_rejected") or not _is_concrete_url(url, platform) or url in seen_urls
                or contains_placeholder(str(candidate.get("title", "")))):
            continue
        # 优先保证视频与教材/官方课程的互补，而不是返回两个高度相似的视频。
        if platform_counts.get(platform, 0) >= 1 and any(
                item.get("platform") != platform for item in candidates):
            continue
        selected.append({key: candidate[key] for key in ("title", "url", "platform", "type")})
        seen_urls.add(url)
        platform_counts[platform] = platform_counts.get(platform, 0) + 1
        if len(selected) >= count:
            break
    return selected


def recommend_resources(topic: str, tasks: list[str] | None = None,
                        context: str = "", count: int = 2,
                        session: Any | None = None,
                        feedback_scores: dict[str, float] | None = None) -> list[dict]:
    """Return concrete, validated and topic-relevant resources without search-page URLs."""
    count = max(1, min(count, 4))
    safe_topic = resolve_topic(topic, tasks, context)
    query, terms = build_focus(safe_topic, tasks, context)
    feedback_scores = feedback_scores or {}
    feedback_key = "|".join(f"{url}:{score:.2f}" for url, score in sorted(feedback_scores.items()))
    cache_key = f"{query.lower()}|{count}|{feedback_key[:800]}"
    with _cache_lock:
        cached = _cache.get(cache_key)
        if cached and time.time() - cached[0] < CACHE_TTL_SECONDS:
            return deepcopy(cached[1])

    focus_text = " ".join([safe_topic, *(tasks or []), *terms]).lower()
    curated = _curated_candidates(focus_text, terms)
    if session is None:
        import requests
        client = requests.Session()
    else:
        client = session
    client.headers.update({
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124 Safari/537.36",
        "Referer": "https://www.bilibili.com/",
        "Accept": "application/json,text/plain,*/*",
    })
    try:
        videos = _search_bilibili(query, terms, max(2, count), client)
    except Exception:
        videos = []

    # 具体视频优先，其次是匹配课程的官方教程；完全无匹配时使用具体通识课程。
    candidates = []
    candidates.extend({**item, "_score": item.get("_score", 0) + 35} for item in videos)
    candidates.extend(curated)
    # 通识资源始终作为低分候选保留，显式负反馈淘汰某资源后仍能补足推荐数量。
    candidates.extend({**item, "_score": 1} for item in GENERAL_RESOURCES)
    for candidate in candidates:
        feedback = float(feedback_scores.get(str(candidate.get("url", "")), 0.0))
        candidate["_score"] = candidate.get("_score", 0) + feedback
        candidate["_rejected"] = feedback <= -20
    selected = _select(candidates, count)
    if len(selected) < count:
        # 平台多样性约束无法凑足时，放宽平台限制，但仍禁止搜索页和重复 URL。
        selected_urls = {item["url"].rstrip("/") for item in selected}
        for candidate in sorted(candidates, key=lambda item: item.get("_score", 0), reverse=True):
            url = str(candidate.get("url", "")).rstrip("/")
            if (candidate.get("_rejected")
                    or not _is_concrete_url(url, str(candidate.get("platform", "")))
                    or url in selected_urls
                    or contains_placeholder(str(candidate.get("title", "")))):
                continue
            selected.append({key: candidate[key] for key in ("title", "url", "platform", "type")})
            selected_urls.add(url)
            if len(selected) >= count:
                break

    with _cache_lock:
        _cache[cache_key] = (time.time(), deepcopy(selected))
        if len(_cache) > 128:
            oldest = min(_cache, key=lambda key: _cache[key][0])
            _cache.pop(oldest, None)
    return selected
