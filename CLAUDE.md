# 项目：个性化学习多智能体系统 (Personalized Learning Multi-Agent System)

## 技术栈
- **后端**: Spring Boot 3.2 + Java 17 + Maven + MySQL 8.0 + JDBC
- **AI 服务**: Python 3.12 + FastAPI + LangChain + MiMo-v2.5 API
- **前端**: Vue 3 + TypeScript + Vite + Pinia + Element Plus + ECharts + Markdown-it
- **AI 模型**: 仅使用 MiMo-v2.5 API（兼容 OpenAI 接口格式）
- **通信**: RESTful API + SSE (流式输出)

## 项目结构

```
edu-multi-agent/
├── backend/                          # Spring Boot 后端（REST API + 数据库）
│   ├── src/main/java/com/edu/agent/
│   │   ├── controller/               # ChatController, PlanController
│   │   ├── service/                  # ChatService, ProfileService, AgentOrchestrationService
│   │   ├── model/                    # UserProfile, LearningPlan, ChatMessage
│   │   ├── repository/               # JPA Repository 接口
│   │   └── config/                   # WebConfig, DatabaseConfig
│   ├── src/main/resources/
│   │   └── application.yml
│   └── pom.xml
├── python-ai/                        # Python AI 服务（LangChain 智能体）
│   ├── main.py                       # FastAPI 服务 + LangChain 智能体实现
│   └── requirements.txt              # Python 依赖
├── frontend/                         # Vue 3 前端
│   ├── src/
│   │   ├── views/                    # ChatView.vue
│   │   ├── components/               # RadarChart.vue, PlanCard.vue, MessageBubble.vue
│   │   ├── stores/                   # chatStore.ts, profileStore.ts
│   │   ├── services/                 # api.ts, sse.ts
│   │   └── App.vue
│   ├── index.html
│   └── package.json
└── README.md
```

## 架构说明

### 整体架构：Spring Boot + Python LangChain 混合架构

```
┌─────────────────┐     HTTP      ┌─────────────────┐     HTTP      ┌─────────────────┐
│   Vue 3 前端    │ ──────────── → │  Spring Boot    │ ──────────── → │  Python AI      │
│   (端口 5173)   │ ← ────────── │  后端 (8080)    │ ← ────────── │  服务 (8000)    │
└─────────────────┘   REST/SSE    └─────────────────┘    JSON       └─────────────────┘
                                          │                                    │
                                          │ MySQL                              │ MiMo-v2.5 API
                                          ▼                                    ▼
                                   ┌─────────────┐                    ┌─────────────────┐
                                   │   MySQL     │                    │  MiMo-v2.5 API   │
                                   │   数据库    │                    │  (LLM 服务)    │
                                   └─────────────┘                    └─────────────────┘
```

**职责划分：**
- **Spring Boot**：REST API、用户认证、SSE 流式输出、MySQL 持久化（对话记录、用户画像）
- **Python AI**：LangChain 智能体（画像提取、对话生成、计划生成、资源推荐）
- **前端**：UI 展示、状态管理、SSE 接收

## 后端详细要求（Spring Boot）

### 数据库配置（MySQL）
- 数据库名：`edu_agent`
- 字符集：`utf8mb4`
- 连接 URL：`jdbc:mysql://localhost:3306/edu_agent?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true`
- 用户名/密码：通过环境变量或 `application.yml` 配置（示例：`MYSQL_USER=root`, `MYSQL_PASSWORD=123456`）
- 使用 Spring Data JPA + Hibernate，自动建表（`ddl-auto: update`）

### 数据表结构（自动生成）
- `conversation`：id (bigint), content (text), role (varchar), timestamp (datetime), conversation_id (varchar)
- `user_profile`：id (bigint), profile_json (json), updated_at (datetime)

### API 接口规范
所有接口返回统一格式：`{ code: number, message: string, data: any }`

1. **POST /api/chat**
   - 输入：`{ message: string, conversationId?: string }`
   - 输出：SSE 流式响应，每个 chunk 格式 `data: { type: "content"|"profile_update", content: string }`
   - 功能：接收用户消息，调用 Python AI 服务提取画像并生成回复，流式返回给前端，同时保存对话到 MySQL

2. **GET /api/profile**
   - 返回当前用户的画像 `UserProfile`
   - 画像维度：`knowledgeBase(int 1-10)`, `cognitiveStyle(string: visual/verbal/kinesthetic)`, `weaknessPoints(string[])`, `learningPace(int 1-10)`, `interestAreas(string[])`, `shortTermGoal(string)`

3. **POST /api/plan**
   - 触发多智能体协同：调用 Python AI 服务生成 4 周学习计划（结构化 JSON），包含每周推荐的 2 个学习资源
   - 返回：`LearningPlan` 对象（包含 weeks 数组，每个 week 有 topic, tasks[], resources[]）

### Spring Boot 配置示例（application.yml）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/edu_agent
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:123456}
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect

# Python AI 服务地址
python:
  ai:
    url: ${PYTHON_AI_URL:http://localhost:8000}
```

## AI 服务详细要求（Python LangChain）

### 技术栈
- **LangChain**：Python 原生 LangChain 框架（替代原 Java LangChain4j）
- **LangChain 核心组件**：
  - `ChatOpenAI`：LLM 调用客户端（兼容 MiMo-v2.5 API）
  - `ChatPromptTemplate`：提示词模板管理
  - `SystemMessage` / `HumanMessage`：消息类型
- **FastAPI**：轻量级 Web 框架，提供 HTTP 接口给 Spring Boot 调用

### 智能体实现

本服务包含两个核心智能体，对应原 LangChain4j 的 PlanningAgent 和 ResourceAgent：

#### 1. 画像提取智能体（Profile Extraction Agent）
- **功能**：从用户对话中自动抽取 6 维学习画像
- **实现**：使用 LangChain Chain 模式（`ChatPromptTemplate | ChatOpenAI`）
- **输入**：用户消息文本
- **输出**：6 维画像 JSON（knowledgeBase, cognitiveStyle, weaknessPoints, learningPace, interestAreas, shortTermGoal）

#### 2. 对话生成智能体（Chat Response Agent）
- **功能**：根据用户画像生成个性化回复
- **实现**：使用 LangChain Chain 模式，将画像注入到 Prompt 中
- **输入**：用户消息 + 画像数据
- **输出**：Markdown 格式的回复文本

#### 3. 计划生成智能体（Planning Agent）
- **功能**：根据用户画像生成 4 周学习计划
- **实现**：使用 LangChain Chain 模式，对应原 Java PlanningAgent
- **输入**：用户画像数据
- **输出**：4 周计划 JSON 数组（每周包含 weekNumber, topic, tasks）

#### 4. 资源推荐智能体（Resource Agent）
- **功能**：为每周学习主题推荐 2 个高质量学习资源
- **实现**：使用 LangChain Chain 模式，对应原 Java ResourceAgent
- **输入**：每周学习主题
- **输出**：资源列表 JSON（包含 title, url, platform, type）

### Python AI 服务接口

1. **POST /chat**
   - 输入：`{ message: string, profile_json: string }`
   - 输出：`{ response: string, profile_json: string }`
   - 功能：提取画像 + 生成回复

2. **POST /plan**
   - 输入：`{ profile_json: string }`
   - 输出：`{ weeks: [...] }`
   - 功能：生成 4 周学习计划（含资源推荐）

3. **GET /health**
   - 输出：`{ status: "ok", service: "edu-ai-python" }`
   - 功能：健康检查

### Python 依赖（requirements.txt）

```txt
fastapi==0.115.0
uvicorn==0.30.6
langchain==0.3.7
langchain-openai==0.2.9
langchain-core==0.3.21
```

## 前端详细要求

### 页面布局 (ChatView.vue)

- **左侧面板** (宽度 35%)：
  - 顶部标签切换：学习画像 / 学习计划
  - 学习画像：雷达图 (ECharts) + 摘要信息
  - 学习计划：4 周计划卡片（可折叠），支持编辑/展开
  - 生成学习计划按钮

- **右侧面板** (宽度 65%)：
  - 聊天区域：消息列表 + 输入框
  - 消息列表支持 Markdown 渲染（代码高亮）
  - 用户消息右对齐，AI 消息左对齐，头像区分
  - 流式输出效果：收到 SSE 时逐字追加到最新 AI 消息

- **底部输入框**：支持 Enter 发送，多行输入（Shift+Enter 换行）

### 状态管理

- `chatStore`：管理消息数组、SSE 连接状态
- `profileStore`：管理画像数据，提供更新方法
- `authStore`：管理用户认证状态、登录信息

### 样式要求

- 整体背景浅灰/白色，卡片圆角，阴影柔和
- 按钮使用渐变色（#D4916F → #B87858），字体使用系统默认无衬线
- 流式输出时显示打字光标动画（闪烁竖线）
- 响应式：在小屏幕下左右面板垂直堆叠

### 环境配置

- 前端开发服务器代理：`/api` 代理到 `http://localhost:8080`

## 运行指南

### 1. 准备 MySQL
```sql
CREATE DATABASE edu_agent CHARACTER SET utf8mb4;
```

### 2. 配置 API Key
```bash
# Windows
set MIMO_API_KEY=your_key_here

# Linux/Mac
export MIMO_API_KEY=your_key_here
```

### 3. 启动 Python AI 服务
```bash
cd python-ai
pip install -r requirements.txt
uvicorn main:app --port 8000
```

### 4. 启动 Spring Boot 后端
```bash
cd backend
./mvnw.cmd spring-boot:run    # Windows
# 或
./mvnw spring-boot:run        # Linux/Mac
```

### 5. 启动前端
```bash
cd frontend
npm install
npm run dev
```

### 6. 访问应用
打开浏览器访问 `http://localhost:5173`

## 非功能性需求（满足评分点）

- **多智能体协同**：Python AI 服务中实现 4 个 LangChain 智能体（画像提取、对话生成、计划生成、资源推荐），通过日志打印协同过程
- **画像动态更新**：每次对话后自动更新右侧雷达图
- **界面美观**：符合现代 AI 产品（类似 ChatGPT 风格）
- **配套文档**：提供 `README.md` 包含部署步骤、API 文档、设计思路
- **MySQL 持久化**：所有对话记录和画像数据存入 MySQL，重启不丢失

## 开发注意事项

- Python AI 服务使用 LangChain 框架，所有 LLM 调用通过 `ChatOpenAI` 客户端
- 所有 LLM 调用已设置超时（30秒）和重试机制（max_retries=2）
- 前后端启动时无报错，能成功进行一轮对话并生成计划即满足"可运行 demo"
- 确保 MySQL 驱动依赖已加入 `pom.xml`（`mysql-connector-j`）
