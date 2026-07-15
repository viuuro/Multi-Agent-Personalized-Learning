# 个性化学习多智能体系统

> Personalized Learning Multi-Agent System —— 基于 MiMo-v2.5 API 的智能学习分析与规划平台

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.2 + Java 17 + Maven |
| AI 集成 | Python LangChain + MiMo-v2.5 API |
| 数据库 | MySQL 8.0 + Spring Data JPA + Hibernate |
| 前端框架 | Vue 3 + TypeScript + Vite |
| 状态管理 | Pinia |
| UI 组件 | Element Plus + ECharts |
| 通信方式 | RESTful API + SSE (流式输出) |

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
│       │   │   └── UserProfile.java            # 用户画像实体 (MySQL JSON)
│       │   ├── repository/
│       │   │   ├── ConversationRepository.java # 对话数据访问层
│       │   │   └── UserProfileRepository.java  # 画像数据访问层
│       │   ├── service/
│       │   │   ├── AgentOrchestrationService.java  # 多智能体协同编排
│       │   │   ├── ChatService.java                # 聊天 + SSE 流式输出
│       │   │   ├── MockAiService.java              # Mock 模式 AI 服务
│       │   │   └── ProfileService.java             # 画像管理
│       │   └── controller/
│       │       ├── ChatController.java             # SSE 流式聊天 API
│       │       ├── ProfileController.java          # 画像查询 API
│       │       └── PlanController.java             # 计划生成 API
│       └── resources/
│           └── application.yml           # Spring Boot 配置
├── python-ai/                        # Python AI 服务（LangChain 智能体）
│   ├── main.py                       # FastAPI + LangChain 智能体实现
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
- Python 3.12+
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

# MySQL 连接信息（可根据实际情况修改 application.yml 中的默认值）
export MYSQL_USER=root
export MYSQL_PASSWORD=123456

# Mock 模式开关（默认 true：无需 API Key 即可运行演示）
export MIMO_MOCK_MODE=true
```

### 4. 启动 Python AI 服务

```bash
cd python-ai
python -m pip install -r requirements.txt
python -m uvicorn main:app --port 8000
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

### 6. 访问系统

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
| 适用场景 | 开发演示、无 API Key | 生产环境 |

**切换到真实 API：**
1. 在环境变量中设置 `MIMO_API_KEY=your-mimo-api-key`
2. 重启后端服务和 Python AI 服务

## 设计思路

### 多智能体协同

系统实现了两个专职智能体的协同工作（通过 `AgentOrchestrationService` 显式编排）：

1. **规划智能体 (PlanningAgent)**：
   根据用户 6 维画像（知识基础、学习风格、薄弱点、学习节奏、兴趣领域、短期目标）
   生成结构化的 4 周学习计划。Mock 模式返回预设模板，真实模式通过 Python LangChain
   调用 MiMo-v2.5 API 生成个性化计划 JSON。

2. **资源智能体**：
   为每周学习主题推荐 2 个高质量学习资源链接。Mock 模式返回 B站/中国大学MOOC
   等平台的固定链接，真实模式通过 Python LangChain 调用 MiMo-v2.5 API 生成推荐。

两个智能体通过 Python AI 服务的 LangChain 框架协同工作，`AgentOrchestrationService` 负责编排调用。

### 画像动态更新

每次对话后，系统自动从用户消息中提取 6 维画像信息并更新到 MySQL 的
`user_profile` 表。前端通过 SSE 接收到 `profile_update` 事件后，
Pinia Store 更新 → ECharts 雷达图实时刷新。每次更新创建新记录（不覆盖），
便于追溯画像变化历史。

### 数据持久化

- **conversation 表**：存储所有对话记录（role, content, timestamp, conversation_id），
  通过 `conversation_id` 关联同一轮对话
- **user_profile 表**：以 MySQL JSON 类型存储画像数据，支持版本追溯
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
