# 个性化学习多智能体系统

> Personalized Learning Multi-Agent System —— 基于 MiMo-v2.5 API 的智能学习分析与规划平台

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5.16 + Spring Security + Java 17 + Maven |
| AI 集成 | Python LangChain + MiMo-v2.5 API |
| 数据库 | MySQL 8.0 + Spring Data JPA + Hibernate |
| 前端框架 | Vue 3 + TypeScript + Vite |
| 状态管理 | Pinia |
| UI 组件 | Element Plus + ECharts |
| 通信方式 | RESTful API + SSE (流式输出) |
| 成长档案 | Spring 异步任务 + JPA 版本关系 + MiMo 结构化评价 |
| 语音陪伴 | MiMo `mimo-v2.5-tts-voiceclone` 非流式语音克隆 + HTMLAudioElement |

## 智能决策链

每轮对话按照以下流程处理：

1. **意图与状态机**：识别学习问答、画像补充、计划创建/修订、成果分析、进度查询和资源请求。
2. **三层记忆**：最近原始消息 + 对话滚动摘要 + 带来源和可信度的长期画像证据。
3. **主动澄清**：仅在缺少会显著改变结果的信息时提出一个高信息量问题，并持久化待处理动作。
4. **计划修改引擎**：区分完整重做、指定周修改、难度/节奏/方向/资源调整；局部修改会保护未涉及周。
5. **临时学习状态**：精力、情绪、可用时间和当前困惑与长期画像分离，并按时间自动失效。
6. **回答质量审查**：检查意图对齐、模板重复、画像倾倒和未执行操作声明，必要时由 MiMo 重写。
7. **可观测与回归**：每轮路由和质量分数落库，学习计划按版本追加保存，并提供无 API Key 的行为评测集。
8. **成果反馈闭环**：成果由“玛丽”评价智能体生成成长档案，具体薄弱点和建议写回对话记忆与画像证据，供后续回答和计划修订使用。
9. **可验证的进步对比**：同一计划任务的多次提交自动形成版本链，系统比较上一份已完成评价的成果，展示分数变化和具体进步证据。

## 玛丽成长档案与成果反馈

该模块把“提交一次文件、得到一个分数”升级为可以连续积累的成长记录。智能体统一命名为
**玛丽**，评价部分保持严格和可追溯，祝福部分使用温柔、自然的陪伴语气。

### 用户可以完成什么

1. 在学习计划中选择具体的周任务，而不是笼统地提交整份计划成果。
2. 上传 PDF、DOCX 或 TXT 成果文件，由系统解析文本并异步调用 MiMo 评价，不阻塞提交请求。
3. 查看综合分以及完成度、准确性、理解深度、实践性、表达规范性五个维度。
4. 查看“真正掌握了什么”“看得见的进步”“下一步小挑战”和玛丽的个性化祝福。
5. 对同一个任务再次提交时，自动看到上一版分数、分数变化和基于内容证据的版本对比。
6. 点击“听玛丽说”，使用当前语音样本生成非流式克隆语音祝福。

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
| 文件解析 | FastAPI `UploadFile` + pypdf + python-docx | 提取 PDF、DOCX、TXT 内容并记录上传元数据 |
| 版本管理 | Spring Data JPA + MySQL/H2 | 保存 `versionNumber`、上一版和实际对比版本 ID |
| 异步评价 | Spring `Executor` + 事务提交后调度 | HTTP 立即返回提交 ID，后台调用 Python `/evaluate` |
| 智能评价 | LangChain `ChatOpenAI` + MiMo-v2.5 | 生成五维分数、证据、掌握点、联动说明和下一步挑战 |
| 成长持久化 | `task_submission` + `ai_evaluation` | 保存版本、结构化评价、分数差和成长结论 |
| 前端呈现 | Vue 3 + TypeScript + CSS 进度条 | 展示成长档案、首次基线或相对上一版的变化 |
| 语音祝福 | MiMo Voice Clone 非流式接口 | 将玛丽的祝福文本生成为 WAV 音频并在浏览器播放 |

### 成长档案数据流

```text
选择具体计划任务 → 上传并解析成果 → 创建 PENDING 版本
        → 事务提交后异步评价 → 汇总画像/计划/历史问题/附件摘要/上一版
        → Python LangChain 调用 MiMo → 保存五维评价和成长证据
        → 前端轮询恢复档案 → 可选生成并播放玛丽语音祝福
```

## 智能决策链

每轮对话按照以下流程处理：

1. **意图与状态机**：识别学习问答、画像补充、计划创建/修订、成果分析、进度查询和资源请求。
2. **三层记忆**：最近原始消息 + 对话滚动摘要 + 带来源和可信度的长期画像证据。
3. **主动澄清**：仅在缺少会显著改变结果的信息时提出一个高信息量问题，并持久化待处理动作。
4. **计划修改引擎**：区分完整重做、指定周修改、难度/节奏/方向/资源调整；局部修改会保护未涉及周。
5. **临时学习状态**：精力、情绪、可用时间和当前困惑与长期画像分离，并按时间自动失效。
6. **回答质量审查**：检查意图对齐、模板重复、画像倾倒和未执行操作声明，必要时由 MiMo 重写。
7. **可观测与回归**：每轮路由和质量分数落库，学习计划按版本追加保存，并提供无 API Key 的行为评测集。
8. **成果反馈闭环**：成果由 Python AI 的 MiMo 评价智能体评分，具体薄弱点和建议写回对话记忆与画像证据，供后续回答和计划修订使用。

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
│       │   │   ├── AiEvaluation.java           # 五维评价、进步证据与玛丽祝福
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
│   ├── main.py                       # FastAPI + LangChain 智能体及 /evaluate
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
# MiMo-v2.5 API Key（可选：默认使用 Mock 模式，无需真实 Key）
export MIMO_API_KEY=sk-your-key-here

# 可选：语音克隆参考音频；相对路径以 python-ai 目录为基准
export MIMO_VOICE_REFERENCE_AUDIO=samples/your-reference.mp3

# 不配置时默认使用 backend/data 下的持久化 H2 文件库。
# 切换 MySQL 时设置：
export DB_URL='jdbc:mysql://localhost:3306/edu_agent?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'
export DB_USER=root
export DB_PASSWORD=123456
export DB_DRIVER=com.mysql.cj.jdbc.Driver

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

SSE 流式聊天接口 —— 发送消息并实时接收 AI 回复。

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
data: {"type":"content","content":"你"}     ← 逐字推送（打字机效果）

event: message
data: {"type":"content","content":"好"}

... （更多字符） ...

event: profile                              ← 画像更新事件
data: {"type":"profile_update","content":{"knowledgeBase":7,...}}

event: done                                 ← 流结束
data: {"type":"done","content":""}
```

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
- `blessingText`：玛丽的个性化文字祝福。

### GET /api/conversations/{conversationId}/submissions

按时间倒序恢复当前对话的成果提交和评价。刷新页面或切换会话后，前端用该接口恢复最近的成长档案。

### POST /api/voice/welcome

接收 `username`、`text` 和可选 `style`，通过 Python AI 服务调用 MiMo
`mimo-v2.5-tts-voiceclone` 非流式接口，返回 WAV 音频。欢迎语和玛丽的成长祝福共用该语音生成能力。

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

**响应示例：**
```json
{
  "code": 200,
  "message": "多智能体协同完成，学习计划已生成",
  "data": {
    "weeks": [
      {
        "weekNumber": 1,
        "topic": "Java 编程基础入门",
        "tasks": ["了解核心概念", "完成基础框架搭建", "阅读入门教材前3章"],
        "resources": [
          {
            "title": "【B站】Java 编程基础入门 — 入门到精通全套教程",
            "url": "https://www.bilibili.com/video/BV1xx411c7mD",
            "platform": "B站",
            "type": "video"
          },
          {
            "title": "【中国大学MOOC】Java 编程基础入门 — 国家级精品课程",
            "url": "https://www.icourse163.org/course/PKU-1000001",
            "platform": "中国大学MOOC",
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

| 对比项 | Mock 模式（默认） | 真实 API |
|--------|------------------|----------|
| 配置文件 | Mock 模式（默认） | 真实 API 模式 |
| API Key | 不需要 | 必须设置 `MIMO_API_KEY` |
| 画像提取 | 关键词匹配推断 | MiMo-v2.5 LLM 分析 |
| 对话回复 | 预设模板文本 | MiMo-v2.5 LLM 生成 |
| 学习计划 | 预设 4 周结构 | MiMo-v2.5 LLM 生成 |
| 资源推荐 | B站/慕课固定链接 | MiMo-v2.5 LLM 搜索推荐 |
| 成长档案 | 固定的结构化五维评价，可验证版本链 | MiMo 根据成果、画像和历史行为生成证据化评价 |
| 玛丽语音 | 不生成语音 | 需要 `MIMO_API_KEY` 和授权的参考音频 |
| 适用场景 | 开发演示、无 API Key | 生产环境 |

**切换到真实 API：**
1. 在环境变量中设置 `MIMO_API_KEY=your-mimo-api-key`
2. 重启后端服务和 Python AI 服务

## 设计思路

### 多智能体协同

系统通过 Spring Boot 与 Python AI 服务编排多个专职智能体：

1. **规划智能体 (PlanningAgent)**：
   根据用户 6 维画像（知识基础、学习风格、薄弱点、学习节奏、兴趣领域、短期目标）
   生成结构化的 4 周学习计划。Mock 模式返回预设模板，真实模式通过 Python LangChain
   调用 MiMo-v2.5 API 生成个性化计划 JSON。

2. **资源智能体**：
   为每周学习主题推荐 2 个高质量学习资源链接。Mock 模式返回 B站/中国大学MOOC
   等平台的固定链接，真实模式通过 Python LangChain 调用 MiMo-v2.5 API 生成推荐。

3. **画像提取与对话智能体**：
   从对话中提取带证据的长期画像与临时学习状态，并根据画像、记忆和当前意图生成回复。

4. **玛丽成长评价智能体**：
   对具体计划任务的成果进行五维评价；有上一版时生成可核验的差异证据，同时把本次薄弱点反馈到对话记忆和画像证据。

计划与资源由 `AgentOrchestrationService` 编排；成长评价由 `SubmissionService` 在事务提交后异步调用 Python `/evaluate`，避免阻塞上传接口。

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
- **task_submission 表**：保存成果内容、文件信息、处理状态、版本号、上一版和实际对比版本
- **ai_evaluation 表**：保存五维评分、掌握点、进步证据、历史行为联动、分数变化、下一步挑战和玛丽祝福
- **uploaded_file_record 表**：保存上传文件的名称、大小、用途和会话归属，不保存原始文件二进制
- 使用 Spring Data JPA + `ddl-auto: update` 自动建表

### 流式输出 (SSE)

后端 `ChatService` 使用独立线程逐字符推送 AI 回复（间隔约 15ms），
前端通过 `ReadableStream` 逐块读取并实时渲染，配合闪烁光标动画，
实现类似 ChatGPT 的打字机效果。

## 非功能性需求完成情况

| 需求 | 实现方式 |
|------|----------|
| 多智能体协同 | Python AI 服务中 LangChain 智能体协同，`AgentOrchestrationService` 编排调用 |
| 画像动态更新 | SSE profile_update 事件 → Pinia Store → ECharts 雷达图响应式刷新 |
| 界面美观 | 渐变色标题栏、圆角卡片、柔和阴影、Markdown 渲染、打字光标动画 |
| 响应式布局 | 小屏幕（<900px）下右侧边栏自动折叠到下方 |
| MySQL 持久化 | 对话记录 + 画像数据双表存储，重启不丢失 |
| LLM 超时重试 | 30 秒超时 + 最多 2 次重试 |
| 资源扩展点 | Python AI 服务中可扩展搜索 API 集成 |
| 成果异步处理 | 专用 Spring Executor；状态支持 PENDING/RUNNING/EVALUATED/ERROR，重启后恢复未完成任务 |
| 版本化成长对比 | 同一具体计划任务按版本递增，只和最近一份已完成评价的版本比较 |
| 历史行为隐私 | 评价只发送问题文本、附件元数据和受限摘要，不发送历史图片 Base64 |
| 评价提示词安全 | 历史成果和学习行为被标记为数据区，不能覆盖系统评价规则 |
