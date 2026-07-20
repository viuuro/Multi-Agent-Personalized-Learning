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
| 语音陪伴 | MiMo `mimo-v2.5-tts` 非流式语音合成 + “冰糖”预置音色 + HTMLAudioElement |
| 图片资源 | 独立 OpenAI 兼容 Image Generation API，不复用 MiMo 文本密钥 |
| 测试 | JUnit 5 + MockMvc + Python unittest + Vue TypeScript 生产构建 |

数据结构与计算机组成原理的完整课程建库、知识点图谱、掌握度口径和智能体接入步骤见 [课程知识库接入方案](docs/course-agent-integration.md)。
图片生成、资源存储与多模态审核的分阶段方案见 [图片学习资源智能体接入方案](docs/multimodal-agent-integration.md)。

## 当前功能概览

- **独立对话工作区**：每个对话拥有独立的消息、标题、学习画像、计划版本、练习题、成果和智能记忆；登录后默认恢复最近使用的对话，支持新建、切换和右键删除。
- **三栏学习界面**：左侧保留学习数据、历史文件、对话列表和 Live2D；中间为 Markdown/SSE 聊天；右侧可手动收起，集中展示雷达画像、紧凑分析、周计划和成果入口。
- **动态学习画像**：根据完整对话及带可信度的画像证据更新六维雷达图，临时情绪、精力和时间状态不会直接污染长期画像。
- **可修订学习计划**：生成四周计划并保存版本；“重新生成”“修改某周”“调整难度/节奏/方向/资源”等自然语言请求会触发对应更新策略。
- **练习空间与学习总览**：从底部切换进入独立工作区，可按周任务、题型、难度和数量生成题目，实时保存答案；总览展示课程进度、章节掌握、推荐资源和收藏夹。
- **成果成长档案**：成果绑定具体周任务，异步生成五维评价、进步证据、下一步挑战和版本对比，评价成功后才更新任务完成状态。
- **知识库与资源推荐**：内置软件工程专业课程种子知识，支持 PDF/DOCX/TXT/MD 分块检索；计划资源优先返回可直接打开的 B 站 BV 视频或具体官方课程页面。
- **多模态与语音**：用户图片随对话持久化；生成图片会固化并保存到消息历史；语音朗读使用 MiMo `mimo-v2.5-tts`，与对话智能体共用 MiMo Key 和 Base URL。
- **真实学习活跃度**：按每日用户对话、成果提交和已完成评价计算活跃度，不使用前端模拟数据。

## 系统架构

```text
Vue 3 :5173
  ├─ REST / Session / CSRF ───────────────┐
  └─ POST /api/chat + SSE ────────────────┤
                                           ▼
Spring Boot :8080 ───── JPA ──────── MySQL :3306
  ├─ 鉴权、数据隔离、限流、事务和持久化
  ├─ 对话/画像/计划/题目/成果/知识库 API
  └─ 服务端调用
         ▼
FastAPI :8000 ── LangChain / OpenAI-compatible API
  ├─ MiMo-v2.5：对话、画像、计划、出题、审核和评价
  ├─ MiMo-v2.5-tts：语音朗读（共用 MiMo API 配置）
  └─ 独立 Image Generation API：学习图片生成
```

浏览器只访问 Spring Boot 的 `/api`。Python AI 服务默认绑定 `127.0.0.1`，用于后端内部调用，不应直接暴露到公网。

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

`llm`、`review_llm`、`question_llm` 和 `question_review_llm` 是由多个角色共享的四个模型客户端，并不代表系统只有四个智能体。语音合成和文件解析属于工具能力，不计入智能体数量。

`extract_profile()` 和 `_legacy_generate_practice_questions()` 是为兼容旧调用保留的实现，当前 `/chat` 与 `/questions/generate` 主链路不会调用它们，因此也不计入上述数量。

## 成长档案与成果反馈

该模块把“提交一次文件、得到一个分数”升级为可以连续积累的成长记录。评价部分保持严格和可追溯，祝福部分使用温柔、自然的陪伴语气。

### 用户可以完成什么

1. 在学习计划中选择具体的周任务，而不是笼统地提交整份计划成果。
2. 上传 PDF、DOCX、TXT 或 MD 成果文件，由系统解析文本并异步调用 MiMo 评价，不阻塞提交请求；单文件最大 10 MB。
3. 查看综合分以及完成度、准确性、理解深度、实践性、表达规范性五个维度。
4. 查看“真正掌握了什么”“看得见的进步”“下一步小挑战”和个性化祝福。
5. 对同一个任务再次提交时，自动看到上一版分数、分数变化和基于内容证据的版本对比。
6. 点击“播放祝福”，使用 MiMo“冰糖”预置音色生成非流式语音祝福。

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
| 语音祝福 | MiMo `mimo-v2.5-tts` 非流式接口 | 与对话智能体共用 API Key/Base URL，将祝福文本生成为 WAV 并在浏览器播放 |

### 成长档案数据流

```text
选择具体计划任务 → 上传并解析成果 → 创建 PENDING 版本
        → 事务提交后异步评价 → 汇总画像/计划/历史问题/附件摘要/上一版
        → Python LangChain 调用 MiMo → 保存五维评价和成长证据
        → 前端轮询恢复档案 → 可选生成并播放语音祝福
```

## 项目结构

```
├── backend/                              # Spring Boot：API、鉴权、事务和 MySQL 持久化
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/edu/agent/
│       │   ├── config/                    # Security、CORS、异步评价线程池
│       │   ├── controller/                # Auth/Chat/Profile/Plan/Practice/Submission/Knowledge 等 API
│       │   ├── handler/                   # 统一异常响应
│       │   ├── model/                     # JPA 实体和接口模型
│       │   ├── repository/                # Spring Data JPA 仓库
│       │   ├── security/                  # 当前登录用户解析
│       │   └── service/
│       │       ├── ChatService.java               # SSE 对话与消息持久化
│       │       ├── AgentOrchestrationService.java # 计划/资源多智能体编排
│       │       ├── ArtifactPersistenceService.java # 生成图片持久化
│       │       ├── PracticeQuestionService.java   # 题目、草稿和评分
│       │       ├── SubmissionService.java         # 成果版本与异步评价
│       │       ├── KnowledgeBaseService.java      # 分块、作用域和检索
│       │       └── *Seeder.java                   # 软件工程课程种子知识
│       └── resources/
│           ├── application.yml
│           └── knowledge/                # 随应用打包的课程知识
├── python-ai/                            # FastAPI + LangChain 智能体服务
│   ├── main.py                           # 对话、计划、评价、文件、图片和语音内部接口
│   ├── intelligence.py                   # 意图/状态/记忆/计划修订规则安全网
│   ├── question_pipeline.py              # 出题蓝图、生成、审核、校验和去重
│   ├── resource_recommender.py           # B站直达视频与官方资源推荐
│   ├── tts_demo.py                       # 共用 MiMo 配置的预置音色 TTS 客户端
│   ├── evals/                            # 无 Key 可运行的智能行为回归集
│   ├── test_*.py                         # Python 单元与回归测试
│   └── requirements.txt
├── frontend/                             # Vue 3 + TypeScript + Vite
│   ├── package.json
│   ├── vite.config.ts                    # /api → :8080
│   └── src/
│       ├── App.vue                       # 顶栏、历史文件、对话列表和工作区切换
│       ├── views/
│       │   ├── AuthView.vue              # 独立登录/注册界面
│       │   └── ChatView.vue              # 聊天、附件、右侧画像/计划/成果
│       ├── components/
│       │   ├── Live2DWidget.vue           # Live2D 与右键语音设置
│       │   ├── RadarChart.vue             # 六维画像雷达图
│       │   ├── PlanCard.vue               # 计划生成、修订、编辑和展开
│       │   ├── PracticeWorkspace.vue       # 题目生成与实时作答
│       │   ├── LearningOverview.vue        # 课程/章节进度、资源和收藏夹
│       │   └── MessageBubble.vue           # Markdown、图片和语音播放
│       ├── stores/                        # auth/chat/profile/theme/voice Pinia 状态
│       └── services/                      # REST、SSE、标题与总览数据计算
├── knowledge base/                        # 可配置的本地 Markdown 课程知识目录
├── docs/                                  # 知识库与多模态接入设计
├── .env.example                           # 环境变量模板（不要提交真实 Key）
└── README.md
```

## 快速开始

### 1. 环境准备

**前置依赖：**
- JDK 17+
- 项目自带 Maven Wrapper（也可使用 Maven 3.8+）
- Python 3.12.10（项目固定版本见 `.python-version`）
- MySQL 8.0+
- Node.js 18+ & npm 9+

### 2. 创建数据库

```sql
CREATE DATABASE edu_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 配置环境变量

可复制 `.env.example` 作为本地参考。Python 服务会读取项目根目录或 `python-ai/` 下的 `.env`；Spring Boot 仍应通过启动进程环境变量、IDE Run Configuration 或部署平台 Secret 注入数据库等配置。不要提交真实 API Key、密码或 Session 配置。

```bash
# 对话、画像、计划、出题、评价和语音共同使用的 MiMo API
export MIMO_API_KEY=sk-your-key-here
export MIMO_BASE_URL=https://api.xiaomimimo.com/v1
export MIMO_MODEL=mimo-v2.5

# 可选：语音缓存与后端语音限流
export MIMO_VOICE_CACHE_TTL_SECONDS=21600
export MIMO_VOICE_CACHE_MAX_FILES=64
export VOICE_RATE_LIMIT_PER_MINUTE=10
export VOICE_MAX_CONCURRENT_PER_USER=2

# 可选：默认使用低延迟的规则出题蓝图；设为 true 后由 MiMo 额外生成蓝图
export QUESTION_LLM_BLUEPRINT_ENABLED=false

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

# 可选：Python 服务和知识库
export PYTHON_AI_ALLOWED_HOSTS=127.0.0.1,localhost
export KNOWLEDGE_COURSE_DIRECTORY='../knowledge base'

# Spring 本地 Mock 仅在显式开启时使用
export AI_MOCK_ENABLED=false

# 生产环境必须配置：精确的前端来源、HTTPS Cookie 和数据库密码
export APP_ALLOWED_ORIGINS=https://your-frontend.example.com
export SESSION_COOKIE_SECURE=true
export DB_PASSWORD=change-this-password
```

PowerShell 使用 `$env:MIMO_API_KEY="..."`、`$env:DB_PASSWORD="..."` 等同名变量。语音模型固定为 `mimo-v2.5-tts` 和“冰糖”预置音色，不需要独立语音 Key；它会复用 `MIMO_API_KEY` 与 `MIMO_BASE_URL`。

### 4. 启动 Python AI 服务

```powershell
cd python-ai
py -3.12 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8000
```

Python AI 服务只供 Spring Boot 进行服务端调用，默认绑定回环地址、不开放浏览器 CORS，并通过 `PYTHON_AI_ALLOWED_HOSTS` 拒绝非本机 Host。不要把 8000 端口直接暴露到公网。

语音合成使用 MiMo 官方 `mimo-v2.5-tts` 非流式接口和中文女性预置音色“冰糖”，
无需配置或上传参考音频。目标文字放在 `assistant` 消息，语气风格放在可选的 `user` 消息。

也可单独运行语音合成命令：

```powershell
.\.venv\Scripts\python.exe tts_demo.py "你好，很高兴继续陪你学习。" -o output_tts.wav
```

### 5. 启动后端

```powershell
cd backend
.\mvnw.cmd spring-boot:run
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

健康检查：

```text
GET http://localhost:8080/api/health
GET http://localhost:8000/health
```

常见启动问题：

- 后端连接数据库失败：确认 MySQL 已启动、`edu_agent` 已创建，并检查 `DB_URL/DB_USER/DB_PASSWORD`。
- 对话进入 Mock：确认 Python 服务进程能读取 `MIMO_API_KEY`，再检查 `GET :8000/health` 的 `mimo_configured`。
- 语音无声：语音与对话共用 MiMo 配置；先确认对话 API 可用，再检查浏览器语音开关、自动朗读开关和 `/api/voice/welcome` 响应。
- 图片生成不可用：图片使用独立供应商配置，检查 `IMAGE_GENERATION_API_*` 和 `image_model_configured`。

## API 文档

### 通用响应格式

除 SSE 聊天和 WAV 音频外，普通 REST JSON 接口使用统一响应结构：

```json
{
  "code": 200,
  "message": "ok",
  "data": {}
}
```

`POST /api/chat` 返回 `text/event-stream`，`POST /api/voice/welcome` 成功时返回 `audio/wav`，不套用上述 JSON 外壳。

除 `/api/health`、`/api/auth/login`、`/api/auth/register` 和 `/api/auth/csrf` 外，所有 `/api/**` 接口都要求已登录的 HttpOnly Session。`POST`、`PUT`、`DELETE` 等写请求还需要会话绑定的 CSRF Token；前端统一请求层会自动获取、携带并在过期后刷新。

### 接口索引

| 模块 | 接口 | 作用 |
|------|------|------|
| 认证 | `POST /api/auth/register`、`POST /api/auth/login`、`POST /api/auth/logout` | 注册、登录和退出 |
| 认证 | `GET /api/auth/me`、`GET /api/auth/csrf` | 恢复当前账号与获取 CSRF Token |
| 账号 | `PUT /api/auth/profile`、`DELETE /api/auth/account` | 修改账号资料与删除账号 |
| 对话 | `POST /api/chat` | SSE 流式对话、图片理解、画像/计划联动 |
| 对话 | `GET /api/conversations`、`DELETE /api/conversations/{conversationId}` | 恢复或删除当前用户的对话工作区 |
| 对话 | `POST /api/conversations/title`、`GET /api/conversations/{conversationId}/intelligence` | 更新对话标题与读取智能决策状态 |
| 画像与活跃度 | `GET /api/profile`、`GET /api/activity` | 读取对话画像与真实学习活跃度 |
| 计划 | `POST /api/plan`、`GET /api/plan`、`PUT /api/plan` | 生成、读取和手动编辑当前对话计划 |
| 计划 | `GET /api/plan/history`、`GET /api/plan/task-statuses` | 读取计划版本与任务完成状态 |
| 文件 | `POST /api/parse-file`、`GET /api/files` | 解析资料并恢复文档/用户图片历史 |
| 知识库 | `GET /api/knowledge/documents`、`GET /api/knowledge/search`、`DELETE /api/knowledge/documents/{documentId}` | 文档管理与分块检索 |
| 练习 | `GET /api/practice/questions`、`POST /api/practice/questions/generate` | 读取题目与按计划任务生成题目 |
| 练习 | `PUT /api/practice/questions/{id}/answer`、`POST /api/practice/questions/{id}/submit` | 实时保存答案与提交评分 |
| 成果 | `POST /api/submissions`、`GET /api/submissions/{id}` | 创建成果版本与查询异步评价 |
| 成果 | `GET /api/conversations/{conversationId}/submissions`、`GET /api/tasks/{taskId}/submissions` | 恢复对话或任务的成果历史 |
| 收藏 | `GET/POST /api/resource-collections` | 读取或创建资源收藏夹 |
| 收藏 | `POST /api/resource-collections/{collectionId}/resources`、`DELETE /api/resource-collections/{collectionId}/resources/{favoriteId}` | 收藏或取消收藏资源 |
| 收藏 | `DELETE /api/resource-collections/{collectionId}`、`POST /api/resources/feedback` | 删除自定义收藏夹与记录资源反馈 |
| 多模态 | `POST /api/artifacts/image`、`POST /api/voice/welcome` | 生成并保存学习图片、生成 WAV 朗读音频 |

### POST /api/chat

SSE 流式聊天接口 —— 发送消息并实时接收 MiMo 原生输出块。

**请求示例：**
```json
{
  "message": "我想学习 Java 编程",
  "conversationId": "abc123",
  "imageData": "data:image/png;base64,...",
  "attachmentName": "代码截图.png",
  "attachmentType": "image"
}
```

纯文本请求只需要 `message` 和可选的 `conversationId`；后三个附件字段仅在发送图片时使用。

**SSE 事件流：**
```text
event: conversation_id
data: {"type":"conversation_id","content":"a1b2c3d4e5f6"}

event: content
data: {"type":"content","content":"你好"}   ← 模型原生输出块

event: content
data: {"type":"content","content":"好"}

... （更多 Token/文本块） ...

event: profile_update                       ← 画像更新事件
data: {"type":"profile_update","content":{"knowledgeBase":7,...}}

event: plan_update                          ← 对话触发计划修订时发送
data: {"type":"plan_update","content":{"weeks":[...]}}

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
- `GET /api/files?limit=50`：合并返回已解析文档与用户在聊天中上传的图片。响应使用 `fileKind=DOCUMENT/IMAGE` 区分类别，并保留图片所属对话，供左侧“历史文件”恢复展示。

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

通过必填的 `conversationId` 查询当前登录用户在该对话中的 6 维学习画像数据。

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

### GET /api/activity

返回最近 `days` 天（默认 112、范围 1–365）的真实学习活跃度。每天包含 `conversationCount`、`submissionCount`、`evaluatedCount`、`score` 和 `level`：用户消息每条 6 分（最多 10 条），成果提交每次 20 分（最多 3 次），当天已评价成果每次额外 5 分（最多 2 次），总分上限 100。日期由后端按自然日生成，前端只负责显示。

### POST /api/plan

触发多智能体协同（规划智能体 + 资源智能体）生成 4 周学习计划。

计划不会覆盖旧记录，而是保存为可回溯版本。对话中提出“修改第三周”“降低难度”或“更换资源”时，会走局部修订流程并通过 SSE `plan_update` 更新界面。

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
    ]
  }
}
```

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
`mimo-v2.5-tts` 非流式接口并使用“冰糖”预置音色，返回 WAV 音频。欢迎语和成长祝福共用该语音生成能力；服务端按文本与风格缓存音频，并限制缓存数量和存活时间。

语音与聊天智能体读取完全相同的 `MIMO_API_KEY` 和 `MIMO_BASE_URL`，仅模型名不同。主界面在 Live2D 右键设置卡片中提供“语音”和“自动朗读”联动开关；关闭语音会停止自动朗读，开启自动朗读会同时开启语音。

### POST /api/artifacts/image

调用独立配置的 Image Generation API 生成学习图片。必须设置 `IMAGE_GENERATION_API_KEY`；Python 内部接口未配置时返回 HTTP 503，Spring 对外接口会把图片服务不可用统一映射为 HTTP 502。该能力不会复用 `MIMO_API_KEY`，也不会用本地占位图伪装生成成功。

Python 能力状态可通过 `GET http://localhost:8000/artifacts/capabilities` 或 `GET http://localhost:8000/health` 的 `image_model_configured` 字段检查。

供应商返回 Base64 时直接转换为 Data URL；返回临时远程地址时，Python 会先下载并校验图片，再转换为 Data URL。Spring 在同一事务中保存一条 `【生成图片】提示词` 用户消息和一条带 `attachment_data` 的助手图片消息，并返回 `conversationId` 与 `messageId`。因此生成图片在刷新页面、切换对话或重启服务后仍可恢复，数据库中不会依赖可能失效的供应商临时链接。

用户上传图片同样保存在对话消息的附件字段中，并由 `/api/files` 合并到历史文件列表；已解析的 PDF/DOCX/TXT/MD 则继续以 `uploaded_file_record` 元数据和知识库分块保存。

### POST http://localhost:8000/evaluate（内部接口）

Spring Boot 调用的 Python AI 内部接口。除当前任务和成果外，还接收 `profile_json`、
`current_plan_json`、`previous_submission_content`、`previous_evaluation_json` 和
`learning_behavior_json`。这些历史内容被明确标记为“待评价数据”，不能覆盖系统评价规则。

### 智能行为回归评测

```powershell
cd python-ai
.\.venv\Scripts\python.exe evals/run_evals.py
```

评测覆盖意图路由、主动澄清、细粒度计划动作、局部计划保护和重复回复拦截，不需要 MiMo API Key。

运行全部 Python 单元与回归测试：

```powershell
cd python-ai
.\.venv\Scripts\python.exe -m unittest discover -v
```

其中包含对话决策与出题、真实 SSE 事件顺序、图片 API 独立配置、长耗时评价客户端、语音缓存及 WAV 有效性的回归用例。后端使用 `cd backend; .\mvnw.cmd test`，前端使用 `cd frontend; npm run build` 验证 TypeScript 和生产构建。

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
| 语音朗读 | 不生成语音 | 与对话智能体共用 `MIMO_API_KEY` 和 `MIMO_BASE_URL`，调用 MiMo TTS 预置音色 |
| 图片生成 | 不生成图片 | 需要独立 `IMAGE_GENERATION_API_KEY` |
| 适用场景 | 开发演示、无 API Key | 生产环境 |

**切换到真实 API：**
1. 设置 `MIMO_API_KEY=your-mimo-api-key`；如使用自定义网关，再设置 `MIMO_BASE_URL`。对话与语音会共用这套配置
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

- **conversation 表**：存储用户和助手消息、AI 完整上下文、时间戳、对话归属，以及图片的 `attachment_name/type/data`；用户上传图片和生成图片都能随历史消息恢复
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
- **uploaded_file_record 表**：保存已解析文档的名称、大小、用途和会话归属，不保存原始文档二进制；`GET /api/files` 会再合并 `conversation` 中的用户图片消息
- **resource_collection / resource_favorite / learning_resource_feedback 表**：保存课程与自定义收藏夹、具体资源卡片，以及用于后续推荐排序的用户反馈
- **app_user 表**：保存账号资料和 BCrypt 密码摘要；业务数据统一绑定服务端 Session 中的用户 ID
- 使用 Spring Data JPA + `ddl-auto: update` 自动建表

### 流式输出 (SSE)

Python `/chat/stream` 使用 LangChain `llm.astream()` 读取 MiMo 原生文本块，Spring
`ChatService` 在独立工作线程中逐块桥接到浏览器，前端通过 `ReadableStream` 实时渲染。
Python 内部流会发送 `metadata`、`content`、`done`；Spring 对浏览器统一发布 `conversation_id`、`content`、`profile_update`、可选 `plan_update`、`done`，失败时发布 `error`。
若 Python 流在任何内容产生前无法建立，后端才降级到 Mock；若已输出部分内容后失败，则返回错误并停止，避免把两份回答拼接在一起。

## 非功能性需求完成情况

| 需求 | 实现方式 |
|------|----------|
| 多智能体协同 | 10 个职责型智能体组成对话、计划、练习和成果四条协作链，Spring 服务按业务编排 |
| 画像动态更新 | SSE profile_update 事件 → Pinia Store → ECharts 雷达图响应式刷新 |
| 界面与交互 | 极简主题、三栏主界面、可收起右侧栏、Live2D、独立认证页、底部滑出的练习/总览工作区、统一圆角图标与自动隐藏滚动条 |
| 工作区布局 | 聊天区保持稳定宽度，左右功能区独立滚动；练习空间保留 Live2D，并以饼图和五列题型筛选呈现实时作答状态 |
| 对话数据隔离 | 消息、画像、计划、题目、成果、标题和智能记忆均按登录用户及 `conversationId` 双重隔离，登录后恢复最近使用的对话 |
| MySQL 持久化 | 对话、用户/生成图片、画像证据、计划版本、知识库、题目草稿、成果版本、评价、收藏和反馈均持久化，重启不丢失 |
| 图片历史可靠性 | 供应商临时 URL 会下载为 Data URL 后再事务保存；用户图片同时并入历史文件接口，刷新和切换对话可恢复 |
| 活跃度真实性 | 按后端自然日统计用户消息、成果提交和已完成评价，不依赖前端模拟日期或随机数据 |
| LLM 超时重试 | 通用调用 30 秒、出题调用 40 秒；按角色最多重试 1-2 次，Spring 出题总超时 120 秒 |
| 资源检索 | B站候选检索、BV 详情验证、简介/分区/分P内容与互动质量评分、用户反馈排序、平台去重、缓存及具体官方资源降级 |
| 出题质量 | 知识库来源白名单、课程模板、分级难度、隐藏原答案的独立盲审、跨批次历史去重、双层结构校验和选项集合去重 |
| 成果异步处理 | 专用 Spring Executor；状态支持 PENDING/RUNNING/EVALUATED/ERROR，重启后恢复未完成任务 |
| 任务完成真实性 | 计划任务默认 PENDING，仅成果评价成功后标记 COMPLETED；历史评价在启动时自动同步 |
| 版本化成长对比 | 同一具体计划任务按版本递增，只和最近一份已完成评价的版本比较 |
| 文件兼容性 | 前后端统一验证 PDF/DOCX/TXT/MD 与 10 MB 上限，避免界面允许但服务端拒绝 |
| 图片服务隔离 | 图片生成使用独立 Base URL、Key 和模型；未配置明确失败，不复用 MiMo 文本密钥 |
| 语音配置一致性 | 对话和语音复用同一 `MIMO_API_KEY` 与 `MIMO_BASE_URL`；TTS 使用独立模型名、预置音色、WAV 校验和有界缓存 |
| 历史行为隐私 | 评价只发送问题文本、附件元数据和受限摘要，不发送历史图片 Base64 |
| 评价提示词安全 | 历史成果和学习行为被标记为数据区，不能覆盖系统评价规则 |
| 会话写操作保护 | Spring Security 启用会话绑定的 CSRF Token；前端统一请求层会自动获取、携带并在令牌过期后刷新 |
| AI 服务调用边界 | Python AI 服务移除通配 CORS，仅通过 Spring Boot 转发，并建议只监听 `127.0.0.1` 或私有网络 |
| 上传内容校验 | 除扩展名和 10 MB 上限外，服务端校验 PDF/DOCX 文件签名、UTF-8 文本和 DOCX 解压后体积 |
