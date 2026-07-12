"""
个性化学习多智能体系统 —— Python AI 服务（基于 LangChain）

提供两个核心能力：
  POST /chat — 画像提取 + 对话生成
  POST /plan — 4 周学习计划生成（含资源推荐）

使用 LangChain 框架调用小米 MiMo-v2.5 API（兼容 OpenAI 接口格式）。
Spring Boot 后端通过 HTTP 调用本服务。

本服务使用 Python LangChain 实现智能体：
  - ChatOpenAI          → LLM 调用客户端（兼容 MiMo-v2.5）
  - ChatPromptTemplate  → 提示词模板
  - SystemMessage        → system role 设置
  - HumanMessage         → user role 设置
"""
from urllib.parse import quote_plus, urlencode
import requests as http_requests
import json
import os
import logging
from fastapi import FastAPI, File, UploadFile

logging.basicConfig(level=logging.INFO, format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

# LangChain 核心组件
from langchain_openai import ChatOpenAI          # LLM 调用客户端（兼容 MiMo-v2.5）
from langchain_core.messages import SystemMessage, HumanMessage  # 消息类型
from langchain_core.prompts import ChatPromptTemplate           # 提示词模板

# ===== 配置 =====
# 小米 MiMo-v2.5 API 密钥，从环境变量读取（必须设置，否则降级为 Mock 模式）
MIMO_API_KEY = os.getenv("MIMO_API_KEY", "")
# 小米 MiMo-v2.5 API 基础地址（兼容 OpenAI 格式）
MIMO_BASE_URL = "https://api.xiaomimimo.com/v1"
# 使用的模型名称
MODEL_NAME = "mimo-v2.5"

# 创建 LangChain ChatOpenAI 实例
llm = ChatOpenAI(
    model=MODEL_NAME,                           # 模型名称：mimo-v2.5
    openai_api_key=MIMO_API_KEY,                # API 密钥
    openai_api_base=MIMO_BASE_URL,              # API 基础地址
    temperature=0.7,                            # 生成温度（0=确定性，1=随机性）
    max_tokens=2048,                            # 最大生成 token 数
    request_timeout=30,                         # 请求超时（秒）
    max_retries=2,                              # 最大重试次数
)

app = FastAPI(title="Edu AI Service")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


# ===== 数据模型 =====

class ChatRequest(BaseModel):
    message: str
    profile_json: str = ""
    image_data: str = ""  # 图片 Base64 数据（可选，用于多模态）


class PlanRequest(BaseModel):
    profile_json: str = ""
    conversation_context: str = ""  # 用户最近的对话内容（用于生成更精准的计划和资源）


# ===== 工具函数 =====

def call_llm(system_prompt: str, user_prompt: str) -> str:
    """
    调用 MiMo-v2.5 API 的核心函数，返回文本响应

    使用 LangChain 的 ChatOpenAI 客户端，对应原 Java LangChain4j 的：
      - OpenAiChatModel.chat(systemPrompt + userPrompt)
      - 内部自动处理 HTTP 请求、超时、重试、日志记录

    参数:
        system_prompt: 系统提示词，设定 AI 的角色和行为规则
        user_prompt: 用户输入的消息内容

    返回:
        AI 生成的文本响应
    """
    # 构造消息列表
    messages = [
        SystemMessage(content=system_prompt),   # 系统角色：设定 AI 行为
        HumanMessage(content=user_prompt),      # 用户角色：实际输入内容
    ]
    # 调用 LLM
    response = llm.invoke(messages)
    return response.content


def extract_json(text: str) -> str:
    """从 LLM 响应中提取 JSON 字符串（处理 markdown 代码块包裹）"""
    t = text.strip()
    if t.startswith("```"):
        nl = t.find("\n")
        end = t.rfind("```")
        if nl >= 0 and end > nl:
            t = t[nl:end].strip()
    # 找 JSON 对象的起止
    brace_start = t.find("{")
    brace_end = t.rfind("}")
    if brace_start >= 0 and brace_end > brace_start:
        return t[brace_start:brace_end + 1]
    # 找 JSON 数组的起止
    bracket_start = t.find("[")
    bracket_end = t.rfind("]")
    if bracket_start >= 0 and bracket_end > bracket_start:
        return t[bracket_start:bracket_end + 1]
    return t


def safe_int(val, default=5):
    try:
        return int(val)
    except (TypeError, ValueError):
        return default


# ===== 画像提取智能体 =====
# 用 LangChain 的 ChatPromptTemplate 构建提示词

# 创建画像提取提示词模板
profile_extraction_prompt = ChatPromptTemplate.from_messages([
    ("system", """你是一个学习分析专家。从学生的对话中抽取6维度信息，只返回严格 JSON：
{{
  "knowledgeBase": 0-10的整数,
  "cognitiveStyle": "visual"或"verbal"或"kinesthetic",
  "weaknessPoints": ["薄弱点1", "薄弱点2"],
  "learningPace": 0-10的整数,
  "interestAreas": ["兴趣1", "兴趣2"],
  "shortTermGoal": "一句话学习目标"
}}
只返回 JSON，不要任何额外文字。"""),
    ("user", "{user_message}")
])

# 创建画像提取链（LangChain LCEL 语法：prompt | llm）
profile_extraction_chain = profile_extraction_prompt | llm


def extract_profile(user_message: str) -> dict:
    """
    从用户消息中提取 6 维学习画像

    使用 LangChain 的 Chain 模式：
      1. 通过 ChatPromptTemplate 构建提示词
      2. 通过 ChatOpenAI 调用 MiMo-v2.5 API
      3. 解析返回的 JSON 并填充 6 维画像字段

    参数:
        user_message: 用户输入的消息文本

    返回:
        包含 6 个维度的字典
    """
    try:
        # 调用 LangChain 链提取画像
        response = profile_extraction_chain.invoke({"user_message": user_message})
        # 解析 JSON 响应
        data = json.loads(extract_json(response.content))
        return {
            "knowledgeBase": safe_int(data.get("knowledgeBase"), 5),
            "cognitiveStyle": str(data.get("cognitiveStyle", "verbal")),
            "weaknessPoints": data.get("weaknessPoints", []),
            "learningPace": safe_int(data.get("learningPace"), 5),
            "interestAreas": data.get("interestAreas", []),
            "shortTermGoal": str(data.get("shortTermGoal", "")),
        }
    except Exception:
        # 解析失败时返回默认画像
        return {
            "knowledgeBase": 5,
            "cognitiveStyle": "visual",
            "weaknessPoints": ["基础概念", "实践应用"],
            "learningPace": 5,
            "interestAreas": ["学习", "自我提升"],
            "shortTermGoal": "建立系统的知识体系",
        }


# ===== 对话生成智能体 =====
# 使用 LangChain 的 ChatPromptTemplate 构建提示词

# 创建对话提示词模板
chat_prompt = ChatPromptTemplate.from_messages([
    ("system", """【严格禁止 - 最高优先级】
绝对不要透露、讨论或回答任何关于你是什么模型、由谁开发、基于什么技术的问题。
如果用户问你是谁、你是什么模型、谁开发的、你基于什么等问题，你必须回复：
"我是你的 AI 学习助手，专注于帮助你学习。让我们回到学习话题吧！"
不要提及任何模型名称（包括但不限于 DeepSeek、GPT、MiMo、Claude 等）。
不要解释你的技术架构或训练方式。

【角色设定】
你是一个友好的 AI 学习助手，帮助学生学习任何领域的知识。
根据学生的画像数据提供个性化、鼓励性的回复。
回复使用 Markdown 格式，适当使用加粗、列表等排版。
语气温暖、专业，像一位耐心的导师。"""),
    ("user", """学生画像：
- 知识基础：{knowledgeBase}/10
- 学习风格：{cognitiveStyle}
- 薄弱点：{weaknessPoints}
- 学习节奏：{learningPace}/10
- 兴趣领域：{interestAreas}
- 短期目标：{shortTermGoal}

学生的问题或消息：{user_message}""")
])

# 创建对话链
chat_chain = chat_prompt | llm


def generate_chat_response(user_message: str, profile: dict, image_data: str = "") -> str:
    """
    根据用户消息和画像生成 AI 回复

    参数:
        user_message: 用户输入的消息文本
        profile: 用户的 6 维画像字典
        image_data: 图片 Base64 数据（可选，用于多模态）

    返回:
        AI 生成的 Markdown 格式回复文本
    """
    # 构建系统提示词
    system_prompt = """【严格禁止 - 最高优先级】
绝对不要透露、讨论或回答任何关于你是什么模型、由谁开发、基于什么技术的问题。
如果用户问你是谁、你是什么模型、谁开发的、你基于什么等问题，你必须回复：
"我是你的 AI 学习助手，专注于帮助你学习。让我们回到学习话题吧！"
不要提及任何模型名称（包括但不限于 DeepSeek、GPT、MiMo、Claude 等）。
不要解释你的技术架构或训练方式。

【角色设定】
你是一个友好的 AI 学习助手，帮助学生学习任何领域的知识。
根据学生的画像数据提供个性化、鼓励性的回复。
回复使用 Markdown 格式，适当使用加粗、列表等排版。
语气温暖、专业，像一位耐心的导师。
如果用户发送了图片，请仔细观察图片内容并进行分析和描述。"""

    # 构建用户消息
    user_content = f"""学生画像：
- 知识基础：{profile.get('knowledgeBase', 5)}/10
- 学习风格：{profile.get('cognitiveStyle', 'verbal')}
- 薄弱点：{'、'.join(profile.get('weaknessPoints', []))}
- 学习节奏：{profile.get('learningPace', 5)}/10
- 兴趣领域：{'、'.join(profile.get('interestAreas', []))}
- 短期目标：{profile.get('shortTermGoal', '')}

学生的问题或消息：{user_message}"""

    # 如果有图片数据，直接使用 requests 调用多模态 API
    if image_data and image_data.strip():
        return call_multimodal_api(system_prompt, user_content, image_data)

    # 无图片时使用 LangChain
    messages = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=user_content)
    ]
    response = llm.invoke(messages)
    return response.content


def call_multimodal_api(system_prompt: str, user_content: str, image_data: str) -> str:
    """
    直接调用 MiMo 多模态 API（绕过 LangChain，确保格式正确）

    参数:
        system_prompt: 系统提示词
        user_content: 用户消息内容
        image_data: 图片 Base64 数据

    返回:
        AI 生成的回复文本
    """
    import requests

    logger.info(f"call_multimodal_api - image_data 长度: {len(image_data)}")
    logger.info(f"image_data 前50字符: {image_data[:50]}")

    headers = {
        "Authorization": f"Bearer {MIMO_API_KEY}",
        "Content-Type": "application/json"
    }

    data = {
        "model": MODEL_NAME,
        "messages": [
            {"role": "system", "content": system_prompt},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": user_content},
                    {"type": "image_url", "image_url": {"url": image_data}}
                ]
            }
        ],
        "max_tokens": 2048,
        "temperature": 0.7
    }

    try:
        response = requests.post(
            f"{MIMO_BASE_URL}/chat/completions",
            headers=headers,
            json=data,
            timeout=60
        )
        if response.status_code == 200:
            result = response.json()
            return result['choices'][0]['message']['content']
        else:
            logger.error(f"多模态 API 错误: {response.status_code} {response.text}")
            return "抱歉，图片处理时出现问题，请稍后重试。"
    except Exception as e:
        logger.error(f"多模态 API 异常: {e}")
        return "抱歉，图片处理时出现问题，请稍后重试。"


# ===== 学习计划生成智能体 =====
# 使用 LangChain 的 ChatPromptTemplate 构建提示词

# 创建计划生成提示词模板
plan_prompt = ChatPromptTemplate.from_messages([
    ("system", """你是一个专业的学习规划专家。根据学生的画像数据和对话记录，生成一个4周的学习计划。

要求：
1. 输出严格 JSON 数组，格式如下：
[
  {{
    "weekNumber": 1,
    "topic": "本周主题",
    "tasks": ["任务1", "任务2", "任务3"]
  }}
]
2. 每周包含3-5个具体任务。
3. 重点围绕学生在对话中提到的具体技术/语言/工具来安排主题（例如学生问了C++怎么学，主题就应该围绕C++展开）。
4. 难度要匹配学生的知识基础评分和学习节奏。
5. 主题要具体，不要泛泛而谈（好："C++基础语法与面向对象"，差："编程基础入门"）。
6. 只返回 JSON，不要任何其他文字。"""),
    ("user", """学生画像：
- 知识基础：{knowledgeBase}/10
- 学习风格：{cognitiveStyle}
- 薄弱点：{weaknessPoints}
- 学习节奏：{learningPace}/10
- 兴趣领域：{interestAreas}
- 短期目标：{shortTermGoal}

{conversation_context}

请生成4周学习计划。""")
])

# 创建计划生成链
plan_chain = plan_prompt | llm


def generate_plan(profile: dict, conversation_context: str = "") -> list:
    """
    根据画像和对话上下文生成 4 周学习计划

    参数:
        profile: 用户的 6 维画像字典
        conversation_context: 用户最近的对话记录（用于生成更精准的计划主题）

    返回:
        周计划列表
    """
    # 构建上下文提示
    ctx = ""
    if conversation_context:
        ctx = f"以下是学生最近的对话记录，请从中提取学生关心的具体技术/话题：\n{conversation_context}"
    else:
        ctx = "（无对话记录，请根据画像中的兴趣领域生成计划）"

    try:
        response = plan_chain.invoke({
            "knowledgeBase": profile.get('knowledgeBase', 5),
            "cognitiveStyle": profile.get('cognitiveStyle', 'verbal'),
            "weaknessPoints": '、'.join(profile.get('weaknessPoints', [])),
            "learningPace": profile.get('learningPace', 5),
            "interestAreas": '、'.join(profile.get('interestAreas', [])),
            "shortTermGoal": profile.get('shortTermGoal', ''),
            "conversation_context": ctx,
        })
        weeks = json.loads(extract_json(response.content))
        return weeks
    except Exception:
        return _default_plan(profile)


def _default_plan(profile: dict) -> list:
    """默认学习计划（API 调用失败时的降级方案）"""
    topic = (profile.get("interestAreas") or ["综合学习"])[0]
    return [
        {"weekNumber": 1, "topic": f"{topic}基础入门",
         "tasks": [f"了解{topic}的核心概念", "完成基础知识框架搭建", "阅读入门教材前3章", "整理学习笔记"]},
        {"weekNumber": 2, "topic": f"{topic}进阶学习",
         "tasks": [f"深入学习{topic}的核心原理", "完成配套练习", "阅读进阶教材", "参与在线讨论"]},
        {"weekNumber": 3, "topic": f"{topic}实战应用",
         "tasks": ["完成综合实践项目", "分析经典案例", "编写学习报告", "与同学交流心得"]},
        {"weekNumber": 4, "topic": f"{topic}总结提升",
         "tasks": ["回顾本月知识点", "完成综合测评", "制定下一阶段目标", "撰写学习总结"]},
    ]


# ===== 资源推荐智能体 =====

def _search_bilibili(keyword: str, count: int = 2) -> list[dict]:
    """
    调用 B站搜索 API，查找真实视频资源。
    使用 /x/web-interface/search/all/v2 端点，按播放量排序返回真实 BV 号视频。
    """
    import re
    try:
        session = http_requests.Session()
        session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Referer": "https://search.bilibili.com",
            "Origin": "https://search.bilibili.com",
        })
        session.get("https://www.bilibili.com", timeout=8)

        resp = session.get(
            "https://api.bilibili.com/x/web-interface/search/all/v2",
            params={
                "keyword": keyword,
                "page": 1,
                "pagesize": count * 2,  # 多取一些，后面按播放量筛选
                "search_type": "video",
                "order": "click",  # 按播放量排序
            },
            timeout=10,
        )
        if resp.status_code == 200:
            data = resp.json()
            if data.get("code") == 0:
                results = data.get("data", {}).get("result", [])
                videos = []
                for r in results:
                    if r.get("result_type") == "video":
                        for v in r.get("data", []):
                            bvid = v.get("bvid", "")
                            title = re.sub(r"<[^>]+>", "", v.get("title", ""))
                            play_count = v.get("play", 0)
                            if bvid and play_count > 1000:  # 过滤低播放量
                                videos.append({
                                    "title": f"【B站】{title}",
                                    "url": f"https://www.bilibili.com/video/{bvid}",
                                    "platform": "B站",
                                    "type": "video",
                                    "_play": play_count,
                                })
                        break
                # 按播放量降序排列，取前 count 个
                videos.sort(key=lambda x: x.get("_play", 0), reverse=True)
                for v in videos:
                    v.pop("_play", None)
                if videos:
                    return videos[:count]
    except Exception as e:
        logger.warning(f"B站搜索失败: {e}")
    return []


def _search_mooc(keyword: str, count: int = 2) -> list[dict]:
    """
    中国大学 MOOC 搜索（API 封闭较严，使用搜索链接兜底）。
    """
    encoded = quote_plus(keyword)
    return [{
        "title": f"【中国大学MOOC】{keyword}相关课程",
        "url": f"https://www.icourse163.org/search.htm?keyword={encoded}",
        "platform": "中国大学MOOC",
        "type": "course",
    }]


def _build_search_url(platform: str, topic: str) -> dict:
    """根据平台和主题关键词，构造搜索页资源（兜底用）"""
    encoded = quote_plus(topic)
    platforms = {
        "B站": {"url": f"https://search.bilibili.com/all?keyword={encoded}", "type": "video"},
        "bilibili": {"url": f"https://search.bilibili.com/all?keyword={encoded}", "type": "video"},
        "中国大学MOOC": {"url": f"https://www.icourse163.org/search.htm?keyword={encoded}", "type": "course"},
        "慕课网": {"url": f"https://www.imooc.com/search/?keyword={encoded}", "type": "course"},
        "GitHub": {"url": f"https://github.com/search?q={encoded}+tutorial&type=repositories", "type": "community"},
        "LeetCode": {"url": f"https://leetcode.cn/search/?query={encoded}", "type": "practice"},
    }
    for key, val in platforms.items():
        if key.lower() in platform.lower():
            return val
    return {"url": f"https://www.google.com/search?q={encoded}+教程", "type": "article"}


def _extract_search_keywords(topic: str, context: str = "") -> str:
    """
    从主题和对话上下文中提取搜索关键词。
    核心思路：直接用完整的周主题作为搜索词，确保每周结果不同。
    如果主题中没有包含上下文里的技术名，则补上。
    """
    import re

    # 从上下文提取技术关键词
    tech_keyword = ""
    if context:
        patterns = [
            r'(C\+\+|Python|Java(?:Script)?|TypeScript|Go|Rust|React|Vue|Angular|Spring|Django|Flask)',
            r'(数据结构|算法|操作系统|计算机网络|数据库|编译原理|设计模式)',
            r'(Linux|Git|Docker|MySQL|Redis|MongoDB|Nginx)',
            r'(机器学习|深度学习|人工智能|神经网络|大模型)',
        ]
        for p in patterns:
            matches = re.findall(p, context, re.IGNORECASE)
            if matches:
                tech_keyword = matches[0]
                break

    # 直接用完整主题，不做过度清理
    # 如果主题中没有技术关键词，补上（确保搜索精准）
    if tech_keyword and tech_keyword.lower() not in topic.lower():
        return f"{tech_keyword} {topic}"
    return topic


def _find_real_resources(topic: str, context: str = "") -> list[dict]:
    """
    为给定主题查找真实可用的学习资源。
    结合对话上下文提取精准搜索关键词，优先从 B站 API 查找真实视频。
    """
    resources = []

    # 用上下文+主题提取精准关键词
    search_keyword = _extract_search_keywords(topic, context)

    # 尝试从 B站搜索真实视频
    try:
        bilibili_results = _search_bilibili(f"{search_keyword} 教程", count=2)
        if bilibili_results and isinstance(bilibili_results, list):
            for r in bilibili_results[:1]:
                if isinstance(r, dict) and r.get("url"):
                    resources.append(r)
    except Exception as e:
        logger.warning(f"B站搜索失败: {e}")

    # MOOC 搜索链接兜底
    try:
        mooc_results = _search_mooc(search_keyword, count=1)
        if mooc_results and isinstance(mooc_results, list):
            for r in mooc_results[:1]:
                if isinstance(r, dict) and r.get("url"):
                    resources.append(r)
    except Exception as e:
        logger.warning(f"MOOC搜索失败: {e}")

    # 兜底：如果搜索都失败，使用搜索链接
    if not resources:
        resources = [
            {"title": f"【B站】{search_keyword}教程", "url": f"https://search.bilibili.com/all?keyword={quote_plus(search_keyword + ' 教程')}", "platform": "B站", "type": "video"},
            {"title": f"【中国大学MOOC】{search_keyword}", "url": f"https://www.icourse163.org/search.htm?keyword={quote_plus(search_keyword)}", "platform": "中国大学MOOC", "type": "course"},
        ]

    # 确保至少 2 个
    while len(resources) < 2:
        if len(resources) == 0:
            resources.append({"title": f"【B站】{search_keyword}教程", "url": f"https://search.bilibili.com/all?keyword={quote_plus(search_keyword)}", "platform": "B站", "type": "video"})
        else:
            existing_platform = resources[0].get("platform", "B站") if isinstance(resources[0], dict) else "B站"
            missing = "中国大学MOOC" if existing_platform == "B站" else "B站"
            fb = _build_search_url(missing, search_keyword)
            resources.append({"title": f"【{missing}】{search_keyword}相关资源", "url": fb["url"], "platform": missing, "type": fb["type"]})

    return resources[:2]


def enrich_resources(week: dict, context: str = "") -> dict:
    """
    为单周计划推荐 2 个学习资源。
    结合对话上下文让资源更贴合用户实际关心的内容。
    """
    topic = week.get('topic', '学习')
    week["resources"] = _find_real_resources(topic, context)
    return week


# ===== API 端点 =====
# 以下端点由 Spring Boot 后端通过 HTTP 调用

@app.post("/chat")
async def chat(req: ChatRequest):
    """
    处理聊天消息：提取画像 → 生成回复 → 返回结果

    流程：
      1. Spring Boot ChatService 接收用户消息，转发到此端点
      2. Python LangChain 调用 MiMo-v2.5 API 提取画像 + 生成回复
      3. 返回 {response, profile_json} 给 Spring Boot
      4. Spring Boot 负责更新画像到 MySQL 和 SSE 流式输出
    """
    # 日志：检查图片数据
    has_image = bool(req.image_data and req.image_data.strip())
    logger.info(f"收到请求 - message: {req.message[:50]}..., has_image: {has_image}, image_data_length: {len(req.image_data) if req.image_data else 0}")

    # 解析已有画像
    existing_profile = {}
    if req.profile_json:
        try:
            # 处理可能的双重编码
            parsed = json.loads(req.profile_json)
            if isinstance(parsed, str):
                # 如果解析出来还是字符串，再解析一次
                existing_profile = json.loads(parsed)
            elif isinstance(parsed, dict):
                existing_profile = parsed
            else:
                existing_profile = {}
        except (json.JSONDecodeError, TypeError):
            existing_profile = {}

    # Step 1: 画像提取
    profile = extract_profile(req.message)
    # 合并已有画像（保留未被覆盖的字段）
    if isinstance(existing_profile, dict):
        for key in existing_profile:
            if key not in profile or not profile[key]:
                profile[key] = existing_profile[key]

    # Step 2: 生成回复（支持多模态）
    response = generate_chat_response(req.message, profile, req.image_data)
    return {
        "response": response,
        "profile_json": json.dumps(profile, ensure_ascii=False),
    }


@app.post("/plan")
async def plan(req: PlanRequest):
    """
    生成 4 周学习计划（含资源推荐）

    流程：
      1. Spring Boot AgentOrchestrationService 获取用户画像，转发到此端点
      2. Python LangChain 调用 MiMo-v2.5 API 生成 4 周计划框架
      3. Python LangChain 为每周调用 MiMo-v2.5 API 推荐 2 个学习资源
      4. 返回 {weeks} 给 Spring Boot，由 Spring Boot 构建 LearningPlan 对象
    """
    # 解析用户画像
    profile = {}
    if req.profile_json:
        try:
            profile = json.loads(req.profile_json)
        except json.JSONDecodeError:
            pass

    # Step 1: 生成计划框架（结合对话上下文）
    context = req.conversation_context or ""
    weeks = generate_plan(profile, context)
    # Step 2: 为每周补充资源（结合对话上下文提取精准关键词）
    weeks = [enrich_resources(w, context) for w in weeks]
    return {"weeks": weeks}


@app.post("/parse-file")
async def parse_file(file: UploadFile = File(...)):
    """
    解析上传的文件，提取文本内容

    支持格式：PDF (.pdf)、Word (.docx)、纯文本 (.txt)
    返回提取的文本内容（截取前 5000 字符）
    """
    filename = file.filename or "unknown"
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    content = await file.read()

    try:
        if ext == "txt":
            text = content.decode("utf-8", errors="ignore")
        elif ext == "pdf":
            from pypdf import PdfReader
            import io
            reader = PdfReader(io.BytesIO(content))
            text = "\n".join(page.extract_text() or "" for page in reader.pages)
        elif ext == "docx":
            from docx import Document
            import io
            doc = Document(io.BytesIO(content))
            text = "\n".join(para.text for para in doc.paragraphs)
        else:
            return {"text": "", "error": f"不支持的文件格式: .{ext}"}

        # 截取前 5000 字符
        text = text[:5000]
        logger.info(f"解析文件成功: {filename}, 提取 {len(text)} 字符")
        return {"text": text, "filename": filename, "length": len(text)}
    except Exception as e:
        logger.error(f"解析文件失败: {filename}, 错误: {e}")
        return {"text": "", "error": f"文件解析失败: {str(e)}"}


@app.get("/health")
async def health():
    """健康检查端点，用于 Spring Boot 启动时验证 Python AI 服务是否可用"""
    return {"status": "ok", "service": "edu-ai-python"}
