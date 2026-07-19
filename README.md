# 个性化学习多智能体系统

> Personalized Learning Multi-Agent System —— 基于 MiMo-v2.5 API 的智能学习分析与规划平台

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5.16 + Spring Security + Java 17 + Maven |
| AI 集成 | Python 3.12 + FastAPI + LangChain + MiMo-v2.5 API |
| 数据库 | MySQL 8.0 + Spring Data JPA + Hibernate |
| 前端框架 | Vue 3 + TypeScript + Vite |
| 状态管理 | Pinia |
| UI 组件 | Element Plus + ECharts |
| 通信方式 | RESTful API + 原生 SSE Token 流式输出 |
| 成长档案 | Spring 异步任务 + JPA 版本关系 + MiMo 结构化评价 |
| 语音陪伴 | MiMo `mimo-v2.5-tts-voiceclone` 非流式语音克隆 + HTMLAudioElement |
| 图片资源 | 独立 OpenAI 兼容 Image Generation API，不复用 MiMo 文本密钥 |
| 测试 | JUnit 5 + MockMvc + Python unittest + Vue TypeScript 生产构建 |

数据结构与计算机组成原理的完整课程建库、知识点图谱、掌握度口径和智能体接入步骤见 [课程知识库接入方案](docs/course-agent-integration.md)。
图片生成、资源存储与多模态审核的分阶段方案见 [图片学习资源智能体接入方案](docs/multimodal-agent-integration.md)。

## 本次维护更新

| 模块 | 改进内容 | 用户可见结果 |
|------|----------|--------------|
| 流式对话 | 新增 Python `/chat/stream`，Spring 逐块桥接 MiMo `astream` 结果，浏览器使用 `ReadableStream` 消费 SSE | 文本回复在模型生成时即可显示，不再等待完整回答后模拟逐字输出 |
| 计划任务状态 | 新增 `GET /api/plan/task-statuses`，评价成功时才把关联任务标记为 `COMPLETED`，启动时同步历史已评价成果 | 未提交或未完成评价的任务显示空心圆；真正完成评价后才打钩 |
| 成果文件 | 前后端统一支持 PDF、DOCX、TXT、MD，统一限制单文件最大 10 MB | 提交界面直接展示支持格式，Markdown 文件可解析、评价和入库 |
| 成长评价稳定性 | 成果评价使用独立 90 秒客户端且不自动重复整次请求；已有异步状态和重启恢复逻辑保持不变 | 较长成果的结构化评价更不容易因通用 30 秒窗口失败 |
| 图片生成 | 图片资源改为独立 Image Generation API；配置状态暴露在能力与健康检查接口中，未配置时明确返回 503 | 不再用本地 SVG 冒充 AI 图片，也不会错误复用 MiMo 文本接口或密钥 |
| 语音陪伴 | 语音克隆结果按输入与样本缓存，欢迎语和成长祝福共用合法 WAV 播放链路 | 重复祝福可更快播放，失败时不会把错误响应当音频 |
| 数据库配置 | 后端优先读取 `DB_*`，兼容早期 `MYSQL_*`，取消内置弱密码并使用占位示例 | 本地与部署环境的 MySQL 配置方式更明确 |
| 回归保障 | 补充真实 SSE、图片生成、评价、语音缓存及计划任务状态测试 | 本次关键故障均有自动化回归覆盖 |

> 当前边界：文本聊天使用原生 Token 流；包含图片的多模态聊天仍通过供应商非流式接口返回一个内容块。图片生成需要单独配置 `IMAGE_GENERATION_API_KEY`。

## 智能决策链

每轮对话按照以下流程处理：

1. **意图与状态机**：识别学习问答、画像补充、计划创建/修订、成果分析、进度查询和资源请求。
2. **三层记忆**：最近原始消息 + 对话滚动摘要 + 带来源和可信度的长期画像证据。
3. **主动澄清**：仅在缺少会显著改变结果的信息时提出一个高信息量问题，并持久化待处理动作。
4. **计划修改引擎**：区分完整重做、指定周修改、难度/节奏/方向/资源调整；局部修改会保护未涉及周。
5. **临时学习状态**：精力、情绪、可用时间和当前困惑与长期画像分离，并按时间自动失效。
6. **回答质量审查**：检查意图对齐、模板重复、画像倾倒和未执行操作声明，必要时由 MiMo 重写。
7. **可观测与回归**：每轮路由和质量分数落库，学习计划按版本追加保存，并提供无 API Key 的行为评测集。
8. **成果反馈闭环**：成果由评价智能体生成成长档案，具体薄弱点和建议写回对话记忆与画像证据，供后续回答和计划修订使用。
9. **可验证的进步对比**：同一计划任务的多次提交自动形成版本链，系统比较上一份已完成评价的成果，展示分数变化和具体进步证据。

## 智能体架构

按当前实际参与业务流程的独立职责统计，Python AI 服务包含 **10 个智能体角色**。其中 8 个角色直接调用 MiMo-v2.5，资源推荐和默认出题蓝图采用可验证的搜索或规则实现。

| 智能体角色 | 实现方式 | 职责 |
|------------|----------|------|
| 学习决策与画像智能体 | MiMo + 规则降级 | 识别意图和对话状态，维护长期画像证据、临时状态、滚动摘要、计划动作与澄清问题 |
| 对话导师智能体 | MiMo | 结合画像、历史对话、当前计划和知识库生成个性化学习回答 |
| 回答审核智能体 | MiMo + 确定性检查 | 检查意图对齐、模板重复、画像倾倒和虚假操作声明，必要时重写回答 |
| 学习计划智能体 | MiMo + 版本合并规则 | 创建四周计划，支持完整重做、指定周修改以及难度、节奏、方向和资源调整 |
| 资源推荐智能体 | B站 API + 官方资源目录 | 检索并验证可直接打开的 BV 视频，按相关性和质量排序；失败时返回具体官方课程或教材 |
| 出题蓝图智能体 | 课程规则；可选 MiMo | 确定知识点、学习目标、认知层级、误区、考查方式、难度和知识块来源 |
| 候选题生成智能体 | MiMo | 根据蓝图、课程模板、画像和知识库生成数量略多于目标值的候选题 |
| 题目审核智能体 | MiMo + 严格校验 | 修复答案歧义、题型错误、难度偏差和来源问题，评分并进行模糊去重 |
| 对话标题智能体 | MiMo + 本地降级 | 从整体对话中提取稳定主题，生成历史对话标题 |
| 成长评价智能体 | MiMo | 对成果进行五维评价，比较版本进步并生成建议、挑战和祝福 |

`llm`、`review_llm`、`question_llm` 和 `question_review_llm` 是由多个角色共享的四个模型客户端，并不代表系统只有四个智能体。语音克隆和文件解析属于工具能力，不计入智能体数量。

`extract_profile()` 和 `_legacy_generate_practice_questions()` 是为兼容旧调用保留的实现，当前 `/chat` 与 `/questions/generate` 主链路不会调用它们，因此也不计入上述数量。

## 成长档案与成果反馈

该模块把“提交一次文件、得到一个分数”升级为可以连续积累的成长记录。评价部分保持严格和可追溯，祝福部分使用温柔、自然的陪伴语气。

### 用户可以完成什么

1. 在学习计划中选择具体的周任务，而不是笼统地提交整份计划成果。
2. 上传 PDF、DOCX、TXT 或 MD 成果文件，由系统解析文本并异步调用 MiMo 评价，不阻塞提交请求；单文件最大 10 MB。
3. 查看综合分以及完成度、准确性、理解深度、实践性、表达规范性五个维度。
4. 查看“真正掌握了什么”“看得见的进步”“下一步小挑战”和个性化祝福。
5. 对同一个任务再次提交时，自动看到上一版分数、分数变化和基于内容证据的版本对比。
6. 点击“播放祝福”，使用当前语音样本生成非流式克隆语音祝福。

### 与学习画像和历史行为的联动

MiMo 评价请求会同时获得以下上下文：

- 当前对话的六维学习画像和四周学习计划；
- 同一任务上一份已评价成果及其评价摘要；
- 当前对话内最近的用户提问；
- 用户曾上传的图片/文件名称、类型、用途和解析文本摘要；
- 本次成果文件的解析文本。

模型只能在存在直接证据时输出 `behavior_links`，用于解释本次成果与过去提问、附件或画像之间的关系。
后端不会把历史图片的 Data URL/Base64 原始内容发送给评价接口，只发送受长度限制的文本摘要和元数据。

### 技术实现

| 环节 | 实现技术 | 作用 |
|------|----------|------|
| 任务定位 | Vue 3 `computed` + Element Plus Dialog | 将提交绑定到 `weekNumber + taskIndex` 的具体计划任务 |
| 文件解析 | FastAPI `UploadFile` + pypdf + python-docx + UTF-8 文本解析 | 提取 PDF、DOCX、TXT、MD 内容并记录上传元数据 |
| 知识库 | MySQL ngram FULLTEXT + 可移植中文检索 | 文档分块、来源引用、用户/对话隔离与软件工程专业课程种子知识 |
| 版本管理 | Spring Data JPA + MySQL/H2 | 保存 `versionNumber`、上一版和实际对比版本 ID |
| 异步评价 | Spring `Executor` + 事务提交后调度 | HTTP 立即返回提交 ID，后台调用 Python `/evaluate` |
| 智能评价 | LangChain `ChatOpenAI` + MiMo-v2.5 | 生成五维分数、证据、掌握点、联动说明和下一步挑战 |
| 成长持久化 | `task_submission` + `ai_evaluation` | 保存版本、结构化评价、分数差和成长结论 |
| 前端呈现 | Vue 3 + TypeScript + CSS 进度条 | 展示成长档案、首次基线或相对上一版的变化 |
| 语音祝福 | MiMo Voice Clone 非流式接口 | 将祝福文本生成为 WAV 音频并在浏览器播放 |

### 成长档案数据流

```text
选择具体计划任务 → 上传并解析成果 → 创建 PENDING 版本
        → 事务提交后异步评价 → 汇总画像/计划/历史问题/附件摘要/上一版
        → Python LangChain 调用 MiMo → 保存五维评价和成长证据
        → 前端轮询恢复档案 → 可选生成并播放语音祝福
```

## 项目结构

```
├── backend/                          # Spring Boot 后端
│   ├── pom.xml                       # Maven 依赖配置
│   └── src/main/
│       ├── java/com/edu/agent/
│       │   ├── EduAgentApplication.java        # 主启动类
│       │   ├── config/
│       │   │   ├── DataInitializer.java        # 默认用户初始化
│       │   │   └── WebConfig.java              # CORS 跨域配置
│       │   ├── model/
│       │   │   ├── ApiResponse.java            # 统一响应格式
│       │   │   ├── ChatMessage.java            # 聊天消息 DTO
│       │   │   ├── Conversation.java           # 对话记录实体 (MySQL)
│       │   │   ├── LearningPlan.java           # 学习计划 (含 weeks/resources)
│       │   │   ├── Task.java                   # 计划中的具体任务位置
│       │   │   ├── TaskSubmission.java         # 成果提交及版本关系
│       │   │   ├── AiEvaluation.java           # 五维评价、进步证据与个性化祝福
│       │   │   └── UserProfile.java            # 用户画像实体 (MySQL JSON)
│       │   ├── repository/
│       │   │   ├── ConversationRepository.java # 对话数据访问层
│       │   │   └── UserProfileRepository.java  # 画像数据访问层
│       │   ├── service/
│       │   │   ├── AgentOrchestrationService.java  # 多智能体协同编排
│       │   │   ├── ChatService.java                # 聊天 + SSE 流式输出
│       │   │   ├── MockAiService.java              # Mock 模式 AI 服务
│       │   │   ├── ProfileService.java             # 画像管理
│       │   │   └── SubmissionService.java          # 版本提交、行为汇总与异步评价
│       │   └── controller/
│       │       ├── ChatController.java             # SSE 流式聊天 API
│       │       ├── ProfileController.java          # 画像查询 API
│       │       ├── PlanController.java             # 计划生成 API
│       │       └── SubmissionController.java       # 成果提交和成长档案 API
│       └── resources/
│           └── application.yml           # Spring Boot 配置
├── python-ai/                        # Python AI 服务（LangChain 智能体）
│   ├── main.py                       # FastAPI、对话/计划/评价智能体及内部 API
│   ├── intelligence.py               # 意图、状态、计划修订和质量检查的规则安全网
│   ├── question_pipeline.py          # 蓝图、候选题、审核、校验与去重流水线
│   ├── resource_recommender.py        # 具体视频验证、排序、缓存和官方资源兜底
│   ├── test_question_pipeline.py     # 出题规则、来源约束和去重测试
│   ├── test_resource_recommender.py  # 资源解析、直达链接和离线降级测试
│   ├── voice_clone_demo.py           # MiMo 非流式语音克隆客户端
│   ├── evals/                        # 智能行为离线回归评测
│   └── requirements.txt              # Python 依赖
├── frontend/                         # Vue 3 前端
│   ├── package.json
│   ├── vite.config.ts               # Vite + API 代理配置
│   ├── index.html                   # 入口 HTML
│   └── src/
│       ├── main.ts                  # 入口文件：挂载 Pinia + Element Plus
│       ├── App.vue                  # 根组件（标题栏 + 主布局）
│       ├── views/
│       │   └── ChatView.vue         # 主页面（聊天区 + 侧边栏）
│       ├── components/
│       │   ├── RadarChart.vue       # ECharts 6 维雷达图
│       │   ├── PlanCard.vue         # 计划卡片（可折叠面板）
│       │   └── MessageBubble.vue    # 消息气泡（Markdown 渲染 + 打字光标）
│       ├── stores/
│       │   ├── chatStore.ts         # 聊天状态管理 (Pinia)
│       │   └── profileStore.ts      # 画像状态管理 (Pinia)
│       └── services/
│           ├── api.ts               # REST API 封装
│           └── sse.ts               # SSE 流式连接处理
└── README.md                        # 本文档
```

## 快速开始

### 1. 环境准备

**前置依赖：**
- JDK 17+
- Maven 3.8+
- Python 3.12.10（项目固定版本见 `.python-version`）
- MySQL 8.0+
- Node.js 18+ & npm 9+

### 2. 创建数据库

```sql
CREATE DATABASE edu_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 配置环境变量

```bash
# MiMo-v2.5 API Key（真实 AI 模式必填；Mock 模式可不设置）
export MIMO_API_KEY=sk-your-key-here

# 可选：默认使用低延迟的规则出题蓝图；设为 true 后由 MiMo 额外生成蓝图
export QUESTION_LLM_BLUEPRINT_ENABLED=false

# 可选：语音克隆参考音频；相对路径以 python-ai 目录为基准
export MIMO_VOICE_REFERENCE_AUDIO=samples/your-reference.mp3

# 后端默认连接本机 MySQL；生产环境必须显式提供数据库密码。
export DB_URL='jdbc:mysql://localhost:3306/edu_agent?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'
export DB_USER=root
export DB_PASSWORD=your_mysql_password
export DB_DRIVER=com.mysql.cj.jdbc.Driver

# 兼容已有配置：未设置 DB_USER/DB_PASSWORD 时，后端也会读取 MYSQL_USER/MYSQL_PASSWORD。

# 可选：独立图片生成服务。未配置 Key 时图片接口返回 503，不影响其他模块。
export IMAGE_GENERATION_API_BASE=https://ark.cn-beijing.volces.com/api/v3
export IMAGE_GENERATION_API_KEY=your_image_api_key
export IMAGE_GENERATION_MODEL=doubao-seedream-5-0-lite-260128
export IMAGE_GENERATION_SIZE=2K

# Spring 本地 Mock 仅在显式开启时使用
export AI_MOCK_ENABLED=false

# 生产环境必须配置：精确的前端来源、HTTPS Cookie 和数据库密码
export APP_ALLOWED_ORIGINS=https://your-frontend.example.com
export SESSION_COOKIE_SECURE=true
export DB_PASSWORD=change-this-password
```

### 4. 启动 Python AI 服务

```bash
cd python-ai
py -3.12 -m venv venv
.\venv\Scripts\python.exe -m pip install -r requirements.txt
.\venv\Scripts\python.exe -m uvicorn main:app --port 8000
```

语音克隆使用 MiMo 非流式接口。参考音频仅支持 WAV/MP3，Base64 编码后不得超过
10 MiB；请只使用已获得声音所有者授权的音频。

也可单独运行语音克隆命令：

```bash
python voice_clone_demo.py samples/your-reference.mp3 "你好，这是克隆语音。" -o output_tts.wav
```

### 5. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端启动在 `http://localhost:8080`，启动日志会提示当前是否处于 Mock 模式。

### 6. 启动前端

安装 Node.js 依赖并启动开发服务器：

```bash
# 1. 进入前端目录
cd frontend

# 2. 安装依赖（首次启动或 node_modules 缺失时需要）
npm install

# 3. 启动前端开发服务器
npm run dev
```

前端默认访问地址：**http://localhost:5173**

> Vite 会自动将 `/api` 请求代理到后端 `localhost:8080`，无需额外配置。

> 如果遇到依赖安装失败，可以尝试清空 `node_modules` 和 `package-lock.json` 后重试：
> ```bash
> rm -rf node_modules package-lock.json
> npm install
> ```

### 7. 访问系统

打开浏览器访问 `http://localhost:5173`，即可看到聊天界面。输入学习情况（如"我想学Java编程"）即可体验完整流程。

## API 文档

### 通用响应格式

所有接口返回统一 JSON 结构：

```json
{
  "code": 200,
  "message": "ok",
  "data": {}
}
```

### POST /api/chat

SSE 流式聊天接口 —— 发送消息并实时接收 MiMo 原生输出块。

**请求示例：**
```json
{
  "message": "我想学习 Java 编程",
  "conversationId": "abc123"
}
```

**SSE 事件流：**
```
event: conversation
data: {"type":"conversation_id","content":"a1b2c3d4e5f6"}

event: message
data: {"type":"content","content":"你好"}   ← 模型原生输出块

event: message
data: {"type":"content","content":"好"}

... （更多 Token/文本块） ...

event: profile                              ← 画像更新事件
data: {"type":"profile_update","content":{"knowledgeBase":7,...}}

event: done                                 ← 流结束
data: {"type":"done","content":""}
```

聊天前，后端会自动检索当前用户可访问的知识分块并注入 MiMo。全局软件工程专业课程知识对所有用户可见，聊天上传资料只对当前对话可见，学习成果和 `KNOWLEDGE` 类型资料对当前用户的所有对话可见。知识库命中的回答会使用 `[资料1]`、`[资料2]` 标注依据。

画像和意图决策需要完整 JSON，因此会在首个回复 Token 前执行；纯文本回复随后使用原生 SSE 流。图片多模态请求目前保持非流式，但使用相同事件结构返回内容。

内置种子按课程独立索引，当前覆盖软件工程基础、C、C++、Java、Java EE、Python、数据库、数据结构、离散数学、线性代数、计算机组成原理、操作系统、Linux、概率论与数理统计、计算机网络、编译原理、移动应用开发和游戏软件开发。应用启动时会复用内容未变化的课程索引，并自动替换已更新课程、删除旧版或已移除课程，避免新旧种子重复召回。

### 知识库接口

- `GET /api/knowledge/documents?conversationId=...`：列出当前用户可访问的全局、用户和当前对话知识文档。
- `GET /api/knowledge/search?q=需求追踪&conversationId=...&limit=6`：检索相关分块及来源。
- `DELETE /api/knowledge/documents/{id}`：删除当前用户拥有的知识文档；全局种子知识不可删除。
- `POST /api/parse-file`：解析不超过 10 MB 的 PDF、DOCX、TXT、MD，并自动分块入库。`CHAT` 为对话级作用域，`SUBMISSION` 和 `KNOWLEDGE` 为用户级作用域。

MySQL 环境启动后会自动创建 `knowledge_chunk(heading, content)` 的 `ngram FULLTEXT` 索引；H2 开发和测试环境自动使用内置中英文词元评分，无需额外服务。可通过以下环境变量控制：

```bash
KNOWLEDGE_SEED_SOFTWARE_ENGINEERING_ENABLED=true
KNOWLEDGE_MYSQL_FULLTEXT_ENABLED=true
```

### 学习资源收藏接口

- `GET /api/resource-collections`：读取当前用户的课程收藏夹、自定义收藏夹及资源卡片；首次访问会初始化“数据结构”和“计算机组成原理”两个课程收藏夹。
- `POST /api/resource-collections`：创建自定义收藏夹，请求体为 `{ "name": "期末冲刺" }`。
- `POST /api/resource-collections/{collectionId}/resources`：收藏智能体推荐资源，保存标题、HTTPS 链接、平台、资源类型以及课程/章节 key。
- `DELETE /api/resource-collections/{collectionId}/resources/{favoriteId}`：取消收藏。
- `DELETE /api/resource-collections/{collectionId}`：删除自定义收藏夹；课程收藏夹不可删除。

### GET /api/profile

获取当前用户的 6 维学习画像数据。

**响应示例：**
```json
{
  "code": 200,
  "message": "ok",
  "data": {
    "id": 1,
    "knowledgeBase": 7,
    "cognitiveStyle": "kinesthetic",
    "weaknessPoints": ["并发编程", "设计模式"],
    "learningPace": 5,
    "interestAreas": ["编程", "软件工程"],
    "shortTermGoal": "成为全栈开发工程师",
    "createdAt": "2024-01-01T12:00:00"
  }
}
```

### POST /api/plan

触发多智能体协同（规划智能体 + 资源智能体）生成 4 周学习计划。

计划不会覆盖旧记录，而是保存为可回溯版本。对话中提出“修改第三周”“降低难度”或“更换资源”时，会走局部修订流程并通过 SSE `plan_update` 更新界面。

### GET /api/plan/history

按版本号倒序返回指定用户、指定对话的学习计划历史。

### GET /api/plan/task-statuses

返回当前登录用户、当前对话最新计划版本中各任务的真实状态：`weekNumber`、`taskIndex` 和 `status`。任务初始为 `PENDING`；只有关联成果完成评价后才变为 `COMPLETED`。后端重启时会根据历史 `EVALUATED` 成果修复旧任务状态。

### POST /api/practice/questions/generate

根据当前对话中某一周的具体小任务生成练习题。后端会先检索当前用户可访问的知识库，再把知识上下文和允许引用的知识块 ID 交给 Python 出题流水线。

```json
{
  "conversationId": "abc123",
  "weekNumber": 1,
  "taskIndex": 0,
  "questionType": "SINGLE_CHOICE",
  "difficulty": "MEDIUM",
  "count": 3
}
```

支持 `SINGLE_CHOICE`、`MULTIPLE_CHOICE`、`TRUE_FALSE` 和 `SHORT_ANSWER`。题目会保存知识点、学习目标、认知层级、来源知识块和质量分，并与用户及当前对话隔离。

### POST http://localhost:8000/questions/generate（内部接口）

Spring Boot 调用的知识约束出题接口。执行流程为：

```text
课程与难度规则 → 出题蓝图 → 生成冗余候选题 → 独立审核修复
        → 答案/选项/来源严格校验 → 模糊去重 → 返回目标数量
```

请求除画像、周主题、小任务、题型、难度和数量外，还包含 `knowledge_context` 与 `source_chunk_ids`。当知识上下文非空时，题目必须引用允许列表中的知识块；Python 与 Spring 两层都会再次校验结构和来源。

### GET /api/conversations/{conversationId}/intelligence

返回当前登录用户的滚动摘要、临时学习状态、待澄清问题、画像证据、最近决策与计划版本，用于调试和智能行为评测。用户身份来自服务器 Session。

### POST /api/submissions

为当前学习计划中的具体任务提交成果。后端创建带版本号的 `TaskSubmission`，在数据库事务完成后交给专用线程池异步评价，并立即返回 `submissionId`。

该接口要求先通过 `/api/auth/login` 登录，并携带服务器签发的 HttpOnly Session Cookie。
后端只从认证会话读取用户 ID，忽略客户端伪造的 `userId` 或旧版 `X-User-Id`。

```json
{
  "conversationId": "abc123",
  "weekNumber": 1,
  "taskIndex": 0,
  "fileName": "result-v2.pdf",
  "fileSize": 204800,
  "content": "文件解析后的成果文本"
}
```

`taskIndex` 从 `0` 开始。同一计划、同一周、同一任务位置的再次提交会自动增加 `versionNumber`，并记录 `previousSubmissionId`。

### GET /api/submissions/{id}

查询单次提交状态和成长档案。状态依次为 `PENDING`、`RUNNING`、`EVALUATED`，失败时为 `ERROR`。完成后 `evaluation` 主要包含：

- `score`、`previousScore`、`scoreDelta`、`growthOutcome`；
- `dimensionsJson`：完成度、准确性、深度、实践性、表达五维分数；
- `masteredPointsJson`、`progressEvidenceJson`、`behaviorLinksJson`；
- `analysis`、`suggestion`、`nextChallenge`；
- `blessingText`：个性化文字祝福。

### GET /api/conversations/{conversationId}/submissions

按时间倒序恢复当前对话的成果提交和评价。刷新页面或切换会话后，前端用该接口恢复最近的成长档案。

### POST /api/voice/welcome

接收 `username`、`text` 和可选 `style`，通过 Python AI 服务调用 MiMo
`mimo-v2.5-tts-voiceclone` 非流式接口，返回 WAV 音频。欢迎语和成长祝福共用该语音生成能力。

### POST /api/artifacts/image

调用独立配置的 Image Generation API 生成学习图片。必须设置 `IMAGE_GENERATION_API_KEY`；未配置时返回 HTTP 503，供应商失败时返回 HTTP 502。接口不会复用 `MIMO_API_KEY`，也不会用本地占位图伪装生成成功。

Python 能力状态可通过 `GET http://localhost:8000/artifacts/capabilities` 或 `GET http://localhost:8000/health` 的 `image_model_configured` 字段检查。

### POST http://localhost:8000/evaluate（内部接口）

Spring Boot 调用的 Python AI 内部接口。除当前任务和成果外，还接收 `profile_json`、
`current_plan_json`、`previous_submission_content`、`previous_evaluation_json` 和
`learning_behavior_json`。这些历史内容被明确标记为“待评价数据”，不能覆盖系统评价规则。

### 智能行为回归评测

```powershell
cd python-ai
python evals/run_evals.py
```

评测覆盖意图路由、主动澄清、细粒度计划动作、局部计划保护和重复回复拦截，不需要 MiMo API Key。

运行全部 Python 单元与回归测试：

```powershell
cd python-ai
py -3.12 -m unittest discover -v
```

其中包含对话决策与出题、真实 SSE 事件顺序、图片 API 独立配置、长耗时评价客户端、语音缓存及 WAV 有效性的回归用例。后端使用 `cd backend; .\mvnw.cmd test`，前端使用 `cd frontend; npm run build` 验证 TypeScript 和生产构建。

**响应示例：**
```json
{
  "code": 200,
  "message": "多智能体协同完成，学习计划已生成",
  "data": {
    "weeks": [
      {
        "weekNumber": 1,
        "topic": "Java 基础入门",
        "tasks": ["了解核心概念", "完成基础框架搭建", "阅读入门教材前3章"],
        "resources": [
          {
            "title": "【B站】Java 零基础系统教程",
            "url": "https://www.bilibili.com/video/BV1YA411T76k",
            "platform": "B站",
            "type": "video"
          },
          {
            "title": "Dev.java：Java 官方学习路径",
            "url": "https://dev.java/learn/",
            "platform": "Oracle Java",
            "type": "course"
          }
        ]
      }
      // ... 第 2、3、4 周
    ]
  }
}
```

## Mock 模式 vs 真实 API

| 对比项 | Mock 模式（需显式开启） | 真实 API（默认） |
|--------|------------------|----------|
| 配置文件 | `AI_MOCK_ENABLED=true` | `AI_MOCK_ENABLED=false` |
| API Key | 不需要 | 必须设置 `MIMO_API_KEY` |
| 决策与画像 | 规则识别与保守更新 | MiMo 分析 + 规则证据安全网 |
| 对话回复 | 预设模板文本 | MiMo-v2.5 LLM 生成 |
| 学习计划 | 预设 4 周结构 | MiMo-v2.5 LLM 生成 |
| 资源推荐 | 具体官方课程目录 | B站直达视频 + 具体官方课程/教材，不返回搜索结果页 |
| 练习题 | Spring 本地可作答题目 | 知识库约束的蓝图、候选生成、独立审核与严格校验 |
| 成长档案 | 固定的结构化五维评价，可验证版本链 | MiMo 根据成果、画像和历史行为生成证据化评价 |
| 语音祝福 | 不生成语音 | 需要 `MIMO_API_KEY` 和授权的参考音频 |
| 图片生成 | 不生成图片 | 需要独立 `IMAGE_GENERATION_API_KEY` |
| 适用场景 | 开发演示、无 API Key | 生产环境 |

**切换到真实 API：**
1. 在环境变量中设置 `MIMO_API_KEY=your-mimo-api-key`
2. 确认 `AI_MOCK_ENABLED=false`
3. 重启后端服务和 Python AI 服务

## 设计思路

### 多智能体协同

系统采用“职责型智能体 + 确定性安全网”的组合架构，而不是让一个大提示词承担所有任务。当前 10 个角色分为四条协作链：

1. **对话链**：学习决策与画像 → 对话导师 → 回答审核。
2. **计划链**：学习计划 → 计划版本合并 → 真实资源推荐。
3. **练习链**：知识库检索 → 出题蓝图 → 候选题生成 → 题目审核 → Spring 二次校验与持久化。
4. **成果链**：成果与历史行为汇总 → 成长评价 → 薄弱点和建议反馈画像及后续计划。

Spring Boot 的 `ChatService`、`AgentOrchestrationService`、`PracticeQuestionService` 和 `SubmissionService` 分别负责调用这些链路，并承担认证、对话隔离、SSE、知识库检索、事务及持久化。Python 服务负责模型推理、结构化结果生成和规则校验。

### 画像动态更新

每次对话后，系统自动从用户消息中提取 6 维画像信息并更新到 MySQL 的
`user_profile` 表。前端通过 SSE 接收到 `profile_update` 事件后，
Pinia Store 更新 → ECharts 雷达图实时刷新。每次更新创建新记录（不覆盖），
便于追溯画像变化历史。

### 数据持久化

- **conversation 表**：存储所有对话记录（role, content, timestamp, conversation_id），
  通过 `conversation_id` 关联同一轮对话
- **user_profile 表**：以 MySQL JSON 类型存储画像数据，支持版本追溯
- **profile_evidence 表**：保存画像字段、用户原话、可信度、长期/短期作用域及冲突状态
- **conversation_session 表**：保存标题、滚动摘要、临时状态、意图、对话状态和待澄清问题
- **learning_plan 表**：追加保存计划版本、修订类型、修订原因和父版本
- **agent_decision_record 表**：保存每轮意图路由、计划动作、澄清决策与回答质量分数
- **learning_task 表**：将计划中的具体周和任务下标映射为稳定的成果任务
- **practice_question 表**：保存题目、作答草稿、评分、出题蓝图元数据、来源知识块和质量分
- **knowledge_document / knowledge_chunk 表**：保存全局、用户或对话作用域的知识文档及检索分块
- **task_submission 表**：保存成果内容、文件信息、处理状态、版本号、上一版和实际对比版本
- **ai_evaluation 表**：保存五维评分、掌握点、进步证据、历史行为联动、分数变化、下一步挑战和个性化祝福
- **uploaded_file_record 表**：保存上传文件的名称、大小、用途和会话归属，不保存原始文件二进制
- 使用 Spring Data JPA + `ddl-auto: update` 自动建表

### 流式输出 (SSE)

Python `/chat/stream` 使用 LangChain `llm.astream()` 读取 MiMo 原生文本块，Spring
`ChatService` 在独立工作线程中逐块桥接到浏览器，前端通过 `ReadableStream` 实时渲染。
系统会依次发送 `metadata`、`content`、`done`，Spring 再发布画像和计划更新事件。
若 Python 流在任何内容产生前无法建立，后端才降级到 Mock；若已输出部分内容后失败，则返回错误并停止，避免把两份回答拼接在一起。

## 非功能性需求完成情况

| 需求 | 实现方式 |
|------|----------|
| 多智能体协同 | 10 个职责型智能体组成对话、计划、练习和成果四条协作链，Spring 服务按业务编排 |
| 画像动态更新 | SSE profile_update 事件 → Pinia Store → ECharts 雷达图响应式刷新 |
| 界面美观 | 渐变色标题栏、圆角卡片、柔和阴影、Markdown 渲染、打字光标动画 |
| 响应式布局 | 小屏幕（<900px）下右侧边栏自动折叠到下方 |
| MySQL 持久化 | 对话、画像证据、计划版本、知识库、练习题、成果版本与评价均持久化，重启不丢失 |
| LLM 超时重试 | 通用调用 30 秒、出题调用 40 秒；按角色最多重试 1-2 次，Spring 出题总超时 120 秒 |
| 资源检索 | B站候选检索、BV 详情验证、简介/分区/分P内容与互动质量评分、用户反馈排序、平台去重、缓存及具体官方资源降级 |
| 出题质量 | 知识库来源白名单、课程模板、分级难度、隐藏原答案的独立盲审、跨批次历史去重、双层结构校验和选项集合去重 |
| 成果异步处理 | 专用 Spring Executor；状态支持 PENDING/RUNNING/EVALUATED/ERROR，重启后恢复未完成任务 |
| 任务完成真实性 | 计划任务默认 PENDING，仅成果评价成功后标记 COMPLETED；历史评价在启动时自动同步 |
| 版本化成长对比 | 同一具体计划任务按版本递增，只和最近一份已完成评价的版本比较 |
| 文件兼容性 | 前后端统一验证 PDF/DOCX/TXT/MD 与 10 MB 上限，避免界面允许但服务端拒绝 |
| 图片服务隔离 | 图片生成使用独立 Base URL、Key 和模型；未配置明确失败，不复用 MiMo 文本密钥 |
| 历史行为隐私 | 评价只发送问题文本、附件元数据和受限摘要，不发送历史图片 Base64 |
| 评价提示词安全 | 历史成果和学习行为被标记为数据区，不能覆盖系统评价规则 |
