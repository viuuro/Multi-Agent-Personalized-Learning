# 个性化学习多智能体系统 — 项目说明书

## 目录

- [1. 项目概述](#1-项目概述)
  - [1.1 项目背景](#11-项目背景)
  - [1.2 项目目标](#12-项目目标)
  - [1.3 核心价值](#13-核心价值)
- [2. 系统架构](#2-系统架构)
  - [2.1 整体架构](#21-整体架构)
  - [2.2 技术栈总览](#22-技术栈总览)
  - [2.3 数据流设计](#23-数据流设计)
  - [2.4 多智能体协同机制](#24-多智能体协同机制)
- [3. 项目目录结构](#3-项目目录结构)
- [4. 后端设计（Spring Boot）](#4-后端设计spring-boot)
  - [4.1 工程结构](#41-工程结构)
  - [4.2 数据库设计](#42-数据库设计)
  - [4.3 API 接口规范](#43-api-接口规范)
  - [4.4 核心服务说明](#44-核心服务说明)
  - [4.5 配置说明](#45-配置说明)
- [5. AI 服务设计（Python LangChain）](#5-ai-服务设计python-langchain)
  - [5.1 工程结构](#51-工程结构)
  - [5.2 智能体定义](#52-智能体定义)
  - [5.3 API 接口规范](#53-api-接口规范)
  - [5.4 LangChain 组件使用](#54-langchain-组件使用)
- [6. 前端设计（Vue 3）](#6-前端设计vue-3)
  - [6.1 工程结构](#61-工程结构)
  - [6.2 页面布局](#62-页面布局)
  - [6.3 组件清单](#63-组件清单)
  - [6.4 状态管理](#64-状态管理)
  - [6.5 样式与交互规范](#65-样式与交互规范)
- [7. 已实现功能清单](#7-已实现功能清单)
- [8. 已知限制与待改进项](#8-已知限制与待改进项)
- [9. 后续规划功能](#9-后续规划功能)
  - [9.1 软件工程专业知识库](#91-软件工程专业知识库)
  - [9.2 智能题目生成与练习系统](#92-智能题目生成与练习系统)
  - [9.3 学习任务提交与分析答疑](#93-学习任务提交与分析答疑)
  - [9.4 知识点 PPT 与视频生成](#94-知识点-ppt-与视频生成)
  - [9.5 Live2D 接入语音模型](#95-live2d-接入语音模型)
- [10. 部署与运行指南](#10-部署与运行指南)
- [11. 附录](#11-附录)

---

## 1. 项目概述

### 1.1 项目背景

在数字化与智能化深度融合的时代，高等教育的个性化变革成为核心发展方向，同时也面临传统教学模式适配性不足的挑战。不同学生在知识基础、学习能力、兴趣方向上的显著差异，使得标准化教学难以满足个性化学习需求，部分学生存在知识吸收效率低的问题。当前大模型技术迎来高速发展新阶段，以通用大模型、多模态生成大模型(如SeeDance等)、 AI辅助编程工具(如Claude Code等)为代表的技术体系，具备强大的自然语言理解、多模态内容生成、代码辅助开发及实时推理能力，为高等教育领域的创新升级带来全新契机。本赛题旨在借助大模型技术体系，融合前沿AI技术，突破传统教育的技术与模式局限，要求参赛团队构建高等教育**个性化学习**资源体系，开发智能学习智能体系统，切实满足学生的**个性化、多模态**学习需求。

### 1.2 项目目标

| 目标 | 说明 |
|------|------|
| **个性化画像** | 从对话中自动提取学生的 6 维学习画像（知识基础、认知风格、薄弱环节、学习节奏、兴趣领域、短期目标） |
| **自适应计划** | 基于画像自动生成 4 周学习计划，每周包含学习主题、任务列表、推荐资源 |
| **智能对话辅导** | 提供基于画像的个性化 AI 对话，支持 SSE 流式输出，实现类 ChatGPT 体验 |
| **多智能体协同** | 4 个专职智能体（画像提取、对话生成、计划生成、资源推荐）协作完成复杂任务 |
| **数据持久化** | 对话记录、用户画像、用户账户全部持久化至 MySQL，重启不丢失 |

### 1.3 核心价值

- **因材施教**：每个学生获得量身定制的学习路径，而非统一模板
- **实时反馈**：对话即学习，AI 实时解答疑问并动态更新画像
- **数据驱动**：学习行为数据持续沉淀，画像越用越精准
- **低门槛接入**：基于浏览器即可使用，无需安装任何客户端

---

## 2. 系统架构

### 2.1 整体架构

系统采用**三层分离架构**，前端、后端、AI 服务各司其职，通过 HTTP 协议松耦合通信：

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              用户浏览器                                   │
│                     Vue 3 前端应用 (端口 5173)                            │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ HTTP / SSE
                               ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      Spring Boot 后端 (端口 8080)                         │
│  ┌──────────┐ ┌──────────┐ ┌───────────┐ ┌──────────┐ ┌──────────┐      │
│  │ ChatCtrl │ │ PlanCtrl │ │ProfileCtrl│ │ AuthCtrl │ │ FileCtrl │      │
│  └────┬─────┘ └────┬─────┘ └───────────┘ └──────────┘ └────┬─────┘      │
│       │             │                                       │           │
│  ┌────▼─────────────▼───────────────────────────────────────▼────┐      │
│  │                    Service Layer                              │      │
│  │  ChatService │ AgentOrchestrationService │ ProfileService     │      │
│  │  AuthService │ MockAiService             │                    │      │
│  └────┬─────────┴───────────────────────────┴────────────────────┘      │
│       │                          │                                      │
│       ▼                          ▼                                      │
│  ┌─────────┐            ┌──────────────┐                                │
│  │  MySQL  │            │  Python AI   │                                │
│  │  数据库  │            │  服务调用     │                                 │
│  └─────────┘            └───────┬──────┘                                │
└─────────────────────────────────┼───────────────────────────────────────┘
                                  │ HTTP
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Python AI 服务 (端口 8000)                            │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐    │
│  │ 画像提取智能体 │ │ 对话生成智能体  │ │ 计划生成智能体 │ │ 资源推荐智能体  │   │
│  └──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘    │
│         └────────────────┴────────────────┴────────────────┘            │
│                                  │                                      │
│                          LangChain (ChatOpenAI)                         │
└──────────────────────────────────┼──────────────────────────────────────┘
                                   │ HTTPS
                                   ▼
                          ┌─────────────────┐
                          │  MiMo-v2.5 API  │
                          │  (LLM 大模型)    │
                          └─────────────────┘
```

### 2.2 技术栈总览

| 层级 | 技术选型 | 版本 | 用途 |
|------|---------|------|------|
| **前端框架** | Vue 3 | 3.5+ | 响应式 UI 框架 |
| **构建工具** | Vite | 5.x | 开发服务器 + 生产构建 |
| **状态管理** | Pinia | 2.x | 全局状态（聊天、画像、认证） |
| **UI 组件库** | Element Plus | 2.x | 表单、对话框、按钮等 |
| **图表库** | ECharts | 5.x | 6 维雷达图可视化 |
| **Markdown 渲染** | markdown-it | — | AI 回复的 Markdown 渲染 + 代码高亮 |
| **Live2D** | oh-my-live2d | — | 页面 Live2D 看板娘 |
| **后端框架** | Spring Boot | 3.2 | REST API + 业务逻辑 |
| **JDK** | OpenJDK | 17 | Java 运行环境 |
| **构建工具** | Maven | 3.9 | 依赖管理 + 构建 |
| **ORM** | Spring Data JPA | — | 数据库访问 + 自动建表 |
| **数据库** | MySQL | 8.0 | 持久化存储 |
| **AI 框架** | LangChain | 0.3.x | LLM 编排 + Chain 管道 |
| **Web 框架** | FastAPI | 0.115 | Python HTTP 服务 |
| **LLM 模型** | MiMo-v2.5 | — | 大语言模型推理（兼容 OpenAI 接口） |
| **通信协议** | REST + SSE | — | 请求-响应 + 服务端推送流 |

### 2.3 数据流设计

#### 对话流程（核心链路）

```
用户输入消息
    │
    ▼
前端 chatStore.sendMessage()
    │
    ▼ POST /api/chat (SSE)
后端 ChatController
    │
    ├──→ 保存用户消息到 MySQL (conversation 表)
    │
    ▼
ChatService 调用 Python AI /chat
    │
    ├──→ Profile Extraction Agent 提取 6 维画像
    │
    ├──→ Chat Response Agent 生成个性化回复
    │
    ▼
SSE 流式返回给前端
    │
    ├── event: conversation_id  → 会话 ID
    ├── event: content          → 逐字输出 AI 回复
    ├── event: profile_update   → 更新画像数据
    └── event: done             → 流结束
    │
    ▼
前端实时更新：
    ├── chatStore 追加消息到列表
    ├── profileStore 更新画像
    └── RadarChart 雷达图重绘
```

#### 计划生成流程

```
用户点击"生成学习计划"
    │
    ▼ POST /api/plan
后端 PlanController → AgentOrchestrationService
    │
    ▼ POST /plan
Python AI 服务
    │
    ├──→ Plan Generation Agent 生成 4 周计划
    │
    ├──→ Resource Agent 为每周推荐 2 个资源
    │
    ▼
返回结构化 JSON
    │
    ▼
前端 PlanCard 组件渲染计划卡片
```

### 2.4 多智能体协同机制

系统核心由 4 个专职 LangChain 智能体组成，通过 Python AI 服务统一编排：

| 智能体 | 职责 | 输入 | 输出 |
|--------|------|------|------|
| **Profile Extraction Agent** | 从对话中提取学习画像 | 用户消息文本 | 6 维画像 JSON |
| **Chat Response Agent** | 生成个性化对话回复 | 用户消息 + 画像 | Markdown 回复文本 |
| **Plan Generation Agent** | 生成结构化学习计划 | 画像数据 | 4 周计划 JSON |
| **Resource Recommendation Agent** | 为每周主题推荐学习资源 | 每周主题 | 资源列表 JSON |

**协同流程**：在对话场景中，Profile Agent 和 Chat Agent **串行协作**——先提取画像，再将画像注入 Chat Agent 的 Prompt 中生成回复。在计划生成场景中，Plan Agent 先生成计划框架，Resource Agent 再为每周**并行推荐**资源。

---

## 3. 项目目录结构

```
edu-multi-agent/
│
├── backend/                              # Spring Boot 后端
│   ├── pom.xml                           # Maven 依赖配置
│   ├── mvnw.cmd                          # Maven Wrapper (Windows)
│   ├── .mvn/wrapper/                     # Maven Wrapper 配置
│   └── src/main/
│       ├── java/com/edu/agent/
│       │   ├── EduAgentApplication.java  # Spring Boot 启动入口
│       │   ├── config/
│       │   │   ├── DataInitializer.java  # 默认用户初始化
│       │   │   └── WebConfig.java        # CORS 跨域配置
│       │   ├── controller/               # REST API 控制器
│       │   │   ├── AuthController.java   #   用户认证（登录/注册/更新）
│       │   │   ├── ChatController.java   #   对话接口（SSE 流式）
│       │   │   ├── FileController.java   #   文件上传解析
│       │   │   ├── PlanController.java   #   学习计划生成
│       │   │   └── ProfileController.java#   用户画像查询
│       │   ├── model/                    # 数据模型
│       │   │   ├── ApiResponse.java      #   统一响应封装
│       │   │   ├── ChatMessage.java      #   聊天消息 DTO
│       │   │   ├── Conversation.java     #   对话记录实体
│       │   │   ├── LearningPlan.java     #   学习计划 POJO
│       │   │   ├── User.java             #   用户账户实体
│       │   │   └── UserProfile.java      #   用户画像实体
│       │   ├── repository/               # 数据访问层
│       │   │   ├── ConversationRepository.java
│       │   │   ├── UserProfileRepository.java
│       │   │   └── UserRepository.java
│       │   └── service/                  # 业务逻辑层
│       │       ├── AgentOrchestrationService.java  # 多智能体编排
│       │       ├── AuthService.java      #   认证服务
│       │       ├── ChatService.java      #   核心对话服务
│       │       ├── MockAiService.java    #   Mock 降级服务
│       │       └── ProfileService.java   #   画像管理服务
│       └── resources/
│           └── application.yml           # Spring Boot 配置
│
├── python-ai/                            # Python AI 服务
│   ├── main.py                           # FastAPI 应用 + 4 个智能体
│   └── requirements.txt                  # Python 依赖清单
│
├── frontend/                             # Vue 3 前端
│   ├── index.html                        # HTML 入口
│   ├── package.json                      # Node.js 依赖配置
│   ├── vite.config.ts                    # Vite 构建配置
│   ├── dist/                             # 生产构建产物
│   └── src/
│       ├── main.ts                       # Vue 应用入口
│       ├── App.vue                       # 根组件（布局 + 全局样式）
│       ├── views/
│       │   └── ChatView.vue              # 主页面（左右分栏布局）
│       ├── components/
│       │   ├── AccountModal.vue          # 账户管理弹窗
│       │   ├── AuthModal.vue             # 登录/注册弹窗
│       │   ├── Live2DWidget.vue          # Live2D 看板娘组件
│       │   ├── MessageBubble.vue         # 聊天气泡组件
│       │   ├── PlanCard.vue              # 学习计划卡片组件
│       │   ├── RadarChart.vue            # ECharts 雷达图组件
│       │   └── UserMenu.vue              # 用户菜单组件
│       ├── stores/                       # Pinia 状态管理
│       │   ├── authStore.ts              #   认证状态
│       │   ├── chatStore.ts              #   聊天状态
│       │   └── profileStore.ts           #   画像状态
│       └── services/                     # API 调用封装
│           ├── api.ts                    #   REST API 封装
│           └── sse.ts                    #   SSE 流处理封装
│
├── CLAUDE.md                             
├── README.md                             
└── PROJECT_SPEC.md                       # 项目说明书
```

---

## 4. 后端设计（Spring Boot）

### 4.1 工程结构

后端采用经典的**分层架构**设计：

```
Controller（控制器层）
    │  接收 HTTP 请求，参数校验，返回统一响应
    ▼
Service（服务层）
    │  业务逻辑处理，事务管理，智能体编排
    ▼
Repository（数据访问层）
    │  Spring Data JPA，自动生成 SQL
    ▼
MySQL（数据库）
```

### 4.2 数据库设计

**数据库名**：`edu_agent`  
**字符集**：`utf8mb4`  
**表管理策略**：Hibernate `ddl-auto: update`（自动建表与更新）

#### 表结构详情

**conversation 表 — 对话记录**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| content | TEXT | NOT NULL | 消息内容 |
| role | VARCHAR(255) | NOT NULL | 角色（user / assistant） |
| conversation_id | VARCHAR(255) | — | 会话 ID（多轮对话分组） |
| timestamp | DATETIME | — | 消息时间戳 |

**user_profile 表 — 用户画像**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| profile_json | JSON | NOT NULL | 6 维画像 JSON 数据 |
| updated_at | DATETIME | — | 最后更新时间 |

**app_user 表 — 用户账户**

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 主键 |
| username | VARCHAR(255) | UNIQUE, NOT NULL | 用户名 |
| password | VARCHAR(255) | NOT NULL | 密码（SHA-256 哈希） |
| avatar | TEXT | — | 头像（Base64 编码） |
| created_at | DATETIME | — | 创建时间 |
| updated_at | DATETIME | — | 更新时间 |

### 4.3 API 接口规范

所有接口返回统一格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

#### 接口列表

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| `POST` | `/api/chat` | AI 对话（SSE 流式） | `{ message, conversationId?, imageData? }` | SSE 事件流 |
| `GET` | `/api/profile` | 获取用户画像 | — | `UserProfile` 对象 |
| `POST` | `/api/plan` | 生成学习计划 | — | `LearningPlan` 对象 |
| `POST` | `/api/auth/login` | 用户登录 | `{ username, password }` | 用户信息 + Token |
| `POST` | `/api/auth/register` | 用户注册 | `{ username, password }` | 注册结果 |
| `PUT` | `/api/auth/profile` | 更新用户信息 | `{ username?, password?, avatar? }` | 更新结果 |
| `POST` | `/api/parse-file` | 解析上传文件 | `multipart/form-data` | 提取的文本内容 |
| `GET` | `/api/health` | 健康检查 | — | `{ status: "ok" }` |

#### SSE 事件类型（`/api/chat`）

| 事件名 | 数据格式 | 说明 |
|--------|---------|------|
| `conversation_id` | `{ "conversationId": "uuid" }` | 会话 ID |
| `content` | `{ "type": "content", "content": "字符" }` | AI 回复逐字输出 |
| `profile_update` | `{ "type": "profile_update", "profile": {...} }` | 画像更新 |
| `done` | `{ "message": "done" }` | 流结束 |
| `error` | `{ "message": "错误信息" }` | 错误 |

### 4.4 核心服务说明

#### ChatService（核心对话服务）

对话服务是系统的核心枢纽，负责：

1. 接收用户消息，保存到 MySQL
2. 调用 Python AI 服务的 `/chat` 接口
3. 通过 SSE 逐字符流式返回 AI 回复（15ms/字符）
4. 解析画像更新事件，推送到前端
5. 保存 AI 回复到 MySQL

当 Python AI 服务不可用时，自动降级到 `MockAiService` 提供基于关键词的模拟回复。

#### AgentOrchestrationService（多智能体编排服务）

负责协调多个 AI 智能体完成复杂任务：

1. 接收计划生成请求
2. 从 `ProfileService` 获取当前用户画像
3. 调用 Python AI 服务的 `/plan` 接口
4. 解析返回的结构化计划数据
5. 返回 `LearningPlan` 对象给前端

#### AuthService（认证服务）

提供用户认证与账户管理：

- 登录验证（SHA-256 密码哈希比对）
- 用户注册（用户名唯一性校验）
- 账户信息更新（用户名、密码、头像）

### 4.5 配置说明

**application.yml 核心配置**：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/edu_agent?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
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

python:
  ai:
    url: ${PYTHON_AI_URL:http://localhost:8000}
```

---

## 5. AI 服务设计（Python LangChain）

### 5.1 工程结构

```
python-ai/
├── main.py              # FastAPI 应用 + 全部智能体实现
└── requirements.txt     # Python 依赖
```

AI 服务采用**单文件架构**，所有智能体定义、API 路由、数据模型均在 `main.py` 中实现，便于部署和维护。

### 5.2 智能体定义

#### Agent 1：画像提取智能体（Profile Extraction Agent）

**功能**：从用户对话文本中自动抽取 6 维学习画像

**画像维度**：

| 维度 | 字段名 | 类型 | 取值范围 | 说明 |
|------|--------|------|---------|------|
| 知识基础 | `knowledgeBase` | int | 1-10 | 学生当前知识水平 |
| 认知风格 | `cognitiveStyle` | string | visual / verbal / kinesthetic | 偏好的学习方式 |
| 薄弱环节 | `weaknessPoints` | string[] | — | 需要加强的知识点 |
| 学习节奏 | `learningPace` | int | 1-10 | 学习速度偏好 |
| 兴趣领域 | `interestAreas` | string[] | — | 感兴趣的学习方向 |
| 短期目标 | `shortTermGoal` | string | — | 当前学习目标 |

**实现**：LangChain `ChatPromptTemplate | ChatOpenAI` Chain，使用 JSON Mode 确保输出结构化。

#### Agent 2：对话生成智能体（Chat Response Agent）

**功能**：根据用户画像生成个性化回复

**实现**：将画像数据注入 System Prompt，引导 LLM 根据学生的认知风格和知识水平调整回复方式。例如，对视觉型学习者多用类比和图表描述，对动觉型学习者多用实践案例。

#### Agent 3：计划生成智能体（Plan Generation Agent）

**功能**：根据用户画像生成 4 周学习计划

**输出结构**：

```json
{
  "weeks": [
    {
      "weekNumber": 1,
      "topic": "学习主题",
      "tasks": ["任务1", "任务2", "任务3"]
    }
  ]
}
```

#### Agent 4：资源推荐智能体（Resource Agent）

**功能**：为每周学习主题推荐 2 个高质量学习资源

**输出结构**：

```json
{
  "resources": [
    {
      "title": "资源标题",
      "url": "https://...",
      "platform": "Bilibili / MOOC / ...",
      "type": "video / article / course"
    }
  ]
}
```

### 5.3 API 接口规范

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| `POST` | `/chat` | 对话 + 画像提取 | `{ message, profile_json?, image_data? }` | `{ response, profile_json }` |
| `POST` | `/plan` | 生成学习计划 | `{ profile_json }` | `{ weeks: [...] }` |
| `POST` | `/parse-file` | 文件解析 | `multipart/form-data` | `{ text: "..." }` |
| `GET` | `/health` | 健康检查 | — | `{ status: "ok", service: "edu-ai-python" }` |

### 5.4 LangChain 组件使用

| 组件 | 用途 |
|------|------|
| `ChatOpenAI` | LLM 调用客户端，兼容 MiMo-v2.5 API（OpenAI 格式） |
| `ChatPromptTemplate` | 提示词模板管理，支持变量注入 |
| `SystemMessage` / `HumanMessage` | 消息角色定义 |
| LCEL (`\|` 管道语法) | Chain 编排，`prompt \| llm` 模式 |

**多模态支持**：`/chat` 接口支持 `image_data` 参数（Base64 编码图片），通过 `requests` 库直接调用 MiMo 多模态 API，绕过 LangChain 的 `ChatOpenAI`（不支持多模态）。

---

## 6. 前端设计（Vue 3）

### 6.1 工程结构

```
frontend/src/
├── main.ts              # 应用入口，注册 Pinia + Element Plus
├── App.vue              # 根组件（Header + 主内容 + Live2D + 弹窗）
├── views/
│   └── ChatView.vue     # 主页面（左右分栏布局）
├── components/          # UI 组件
│   ├── RadarChart.vue   #   ECharts 雷达图
│   ├── PlanCard.vue     #   学习计划卡片
│   ├── MessageBubble.vue#   聊天气泡
│   ├── Live2DWidget.vue #   Live2D 看板娘
│   ├── AuthModal.vue    #   登录/注册弹窗
│   ├── UserMenu.vue     #   用户菜单
│   └── AccountModal.vue #   账户管理弹窗
├── stores/              # Pinia 状态管理
│   ├── chatStore.ts     #   聊天消息、SSE 状态
│   ├── profileStore.ts  #   6 维画像数据
│   └── authStore.ts     #   用户认证状态
└── services/            # API 调用封装
    ├── api.ts           #   REST API 封装
    └── sse.ts           #   SSE 流处理
```

### 6.2 页面布局

```
┌─────────────────────────────────────────────────────────────────┐
│  Header: 个性化学习多智能体系统                  [用户菜单 ☰]        │
├────────────────────────┬────────────────────────────────────────┤
│                        │                                        │
│   ┌────────────────┐   │   ┌─────────────────────────────────┐  │
│   │ [学习画像][计划] │   │   │  AI: 你好！我是你的学习助手...      │  │
│   ├────────────────┤   │   │                                 │  │
│   │                │   │   │            ┌───────────────┐    │  │
│   │   雷达图        │   │   │            │ 用户: 帮我...  │    │   │
│   │   (ECharts)    │   │   │            └───────────────┘    │  │
│   │                │   │   │                                 │  │
│   │  知识基础: 7     │  │   │  AI: 好的，根据你的画像...         │   │
│   │  认知风格: 视觉  │   │   │                                 │  │
│   │  薄弱: 算法      │  │   └─────────────────────────────────┘   │
│   │  ...           │   │   ┌─────────────────────────────────┐  │
│   │                │   │   │ [+] [📷] [📎]  输入消息... [发送] │  │
│   └────────────────┘   │   └─────────────────────────────────┘  │
│      左面板 35%         │            右面板 65%                   │
├────────────────────────┴────────────────────────────────────────┤
│  [Live2D 看板娘](左下角浮动)                                       │
└─────────────────────────────────────────────────────────────────┘
```

### 6.3 组件清单

| 组件 | 文件 | 功能 |
|------|------|------|
| **RadarChart** | `RadarChart.vue` | ECharts 6 维雷达图，响应式绑定 `profileStore`，画像更新时自动重绘 |
| **PlanCard** | `PlanCard.vue` | 学习计划展示，支持 3 种模式：预览（Popover）、展开（Modal）、编辑（内联） |
| **MessageBubble** | `MessageBubble.vue` | 聊天气泡，用户消息右对齐（橙色背景），AI 消息左对齐（Markdown 渲染 + 代码高亮） |
| **Live2DWidget** | `Live2DWidget.vue` | Live2D 看板娘（iromari 角色），支持眼球追踪、点击表情、背景文字 |
| **AuthModal** | `AuthModal.vue` | 登录/注册弹窗，Tab 切换 |
| **UserMenu** | `UserMenu.vue` | 汉堡菜单，包含账户管理、退出登录 |
| **AccountModal** | `AccountModal.vue` | 编辑用户名、密码、头像 |

### 6.4 状态管理

#### chatStore — 聊天状态

```typescript
interface ChatState {
  messages: Message[]           // 消息列表
  conversationId: string | null // 当前会话 ID
  isStreaming: boolean          // 是否正在流式输出
  streamingContent: string      // 流式输出缓冲区
}
```

#### profileStore — 画像状态

```typescript
interface ProfileState {
  knowledgeBase: number        // 知识基础 1-10
  cognitiveStyle: string       // 认知风格
  weaknessPoints: string[]     // 薄弱环节
  learningPace: number         // 学习节奏 1-10
  interestAreas: string[]      // 兴趣领域
  shortTermGoal: string        // 短期目标
}
```

#### authStore — 认证状态

```typescript
interface AuthState {
  isLoggedIn: boolean          // 是否已登录
  user: UserInfo | null        // 用户信息
  token: string | null         // 认证令牌
}
```

### 6.5 样式与交互规范

| 项目 | 规范 |
|------|------|
| 整体背景 | 浅灰/白色，卡片圆角 + 柔和阴影 |
| 主色调 | 渐变色 `#D4916F → #B87858`（暖橙色系） |
| 字体 | 系统默认无衬线字体 |
| 流式输出 | 打字光标动画（闪烁竖线 `\|`） |
| 响应式断点 | 900px 以下左右面板垂直堆叠 |
| 消息气泡 | 用户右对齐橙色，AI 左对齐白色 |
| Markdown | 支持代码高亮、表格、列表等 |

---

## 7. 已实现功能清单

| # | 功能模块 | 功能描述 | 状态 |
|---|---------|---------|------|
| 1 | 用户认证 | 登录、注册、账户管理（SHA-256 密码哈希） | ✅ 完成 |
| 2 | SSE 流式对话 | 实时逐字输出 AI 回复（15ms/字符） | ✅ 完成 |
| 3 | 6 维学习画像 | 自动从对话中提取 6 维画像 | ✅ 完成 |
| 4 | 雷达图可视化 | ECharts 雷达图实时反映画像变化 | ✅ 完成 |
| 5 | 4 周学习计划 | AI 生成个性化 4 周计划 + 资源推荐 | ✅ 完成 |
| 6 | 计划编辑 | 计划卡片支持展开/预览/编辑 3 种模式 | ✅ 完成 |
| 7 | 文件解析 | 支持 PDF/DOCX/TXT 文件上传与文本提取 | ✅ 完成 |
| 8 | 图片多模态 | 支持图片上传，调用 MiMo 多模态 API 分析 | ✅ 完成 |
| 9 | Live2D 看板娘 | iromari 角色，支持眼球追踪和点击表情 | ✅ 完成 |
| 10 | MySQL 持久化 | 对话记录、用户画像、用户账户全部持久化 | ✅ 完成 |
| 11 | Mock 降级 | 无 API Key 或 AI 服务不可用时自动降级 | ✅ 完成 |
| 12 | 响应式布局 | 小屏幕自适应垂直堆叠 | ✅ 完成 |
| 13 | 多智能体协同 | 4 个 LangChain 智能体协作完成任务 | ✅ 完成 |

---

## 8. 已知限制与待改进项

| # | 问题 | 严重程度 | 说明 |
|---|------|---------|------|
| 1 | 学习计划未持久化 | 中 | `LearningPlan` 是 POJO，不是 JPA 实体，编辑后的计划刷新丢失 |
| 2 | 画像未关联用户 | 中 | `user_profile` 表无 `user_id` 外键，所有用户共享画像 |
| 3 | 对话历史不可回溯 | 中 | 对话保存到 MySQL 但前端无法加载历史会话 |
| 4 | 密码安全较弱 | 低 | 使用 SHA-256 无盐值，生产环境应使用 bcrypt |
| 5 | 硬编码 API Key | 低 | Python 服务中存在默认 API Key，应改为环境变量 |
| 6 | 无测试覆盖 | 低 | 项目无单元测试、集成测试、E2E 测试 |
| 7 | 无容器化部署 | 低 | 缺少 Dockerfile 和 docker-compose |
| 8 | 资源推荐为 LLM 生成 | 中 | 资源 URL 由 LLM 生成，可能失效，未接入真实搜索 API |

---

## 9. 后续规划功能

### 9.1 软件工程专业知识库

#### 9.1.1 功能描述

构建一个**结构化的软件工程专业知识库**，覆盖软件工程专业的核心课程体系。知识库不仅作为学习内容的数据源，还为题目生成、计划推荐、答疑系统提供底层知识支撑。

#### 9.1.2 知识库架构

```
知识库 (Knowledge Base)
│
├── 课程体系 (Course)
│   ├── 数据结构与算法
│   ├── 操作系统
│   ├── 计算机网络
│   ├── 数据库原理
│   ├── 软件工程导论
│   ├── 设计模式
│   ├── 编译原理
│   ├── 计算机组成原理
│   ├── Web 开发
│   └── ...
│
├── 知识点 (Knowledge Point)
│   ├── 所属课程
│   ├── 知识点名称
│   ├── 难度等级 (1-5)
│   ├── 前置知识点（依赖关系）
│   ├── 详细内容（Markdown）
│   ├── 代码示例
│   └── 常见误区
│
├── 知识图谱 (Knowledge Graph)
│   ├── 知识点之间的前置依赖关系
│   ├── 课程之间的关联关系
│   └── 学习路径推荐
│
└── 题库 (Question Bank)
    ├── 选择题
    ├── 填空题
    ├── 编程题
    ├── 简答题
    └── 关联知识点标签
```

#### 9.1.3 技术方案

| 层级 | 方案 | 说明 |
|------|------|------|
| **存储** | MySQL + JSON | 课程/知识点结构化存储，知识图谱用邻接表 |
| **索引** | 全文索引 | MySQL FULLTEXT 或 Elasticsearch 支持知识点搜索 |
| **导入** | Markdown 批量导入 | 支持从 Markdown 文件批量导入知识点 |
| **API** | RESTful | 提供知识库的 CRUD 接口和查询接口 |
| **管理界面** | Vue 后台页面 | 知识库管理后台（课程管理、知识点编辑、题库管理） |

#### 9.1.4 数据模型设计

```sql
-- 课程表
CREATE TABLE course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,        -- 课程名称
    category VARCHAR(50),              -- 分类（基础/核心/进阶）
    description TEXT,                  -- 课程描述
    sort_order INT DEFAULT 0,          -- 排序权重
    created_at DATETIME DEFAULT NOW()
);

-- 知识点表
CREATE TABLE knowledge_point (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,         -- 所属课程
    title VARCHAR(200) NOT NULL,       -- 知识点标题
    content TEXT,                      -- 详细内容（Markdown）
    difficulty INT DEFAULT 1,          -- 难度 1-5
    code_examples TEXT,                -- 代码示例（JSON 数组）
    common_mistakes TEXT,              -- 常见误区
    tags VARCHAR(500),                 -- 标签（逗号分隔）
    created_at DATETIME DEFAULT NOW(),
    updated_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (course_id) REFERENCES course(id)
);

-- 知识点依赖关系表
CREATE TABLE knowledge_dependency (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_id BIGINT NOT NULL,         -- 源知识点
    target_id BIGINT NOT NULL,         -- 前置知识点
    dependency_type VARCHAR(20),       -- 类型：required / recommended
    FOREIGN KEY (source_id) REFERENCES knowledge_point(id),
    FOREIGN KEY (target_id) REFERENCES knowledge_point(id)
);
```

#### 9.1.5 新增接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/knowledge/courses` | 获取课程列表 |
| `GET` | `/api/knowledge/courses/{id}/points` | 获取课程下的知识点 |
| `GET` | `/api/knowledge/points/{id}` | 获取知识点详情 |
| `GET` | `/api/knowledge/search?q=` | 全文搜索知识点 |
| `GET` | `/api/knowledge/graph` | 获取知识图谱数据 |
| `POST` | `/api/knowledge/points` | 创建知识点（管理） |
| `PUT` | `/api/knowledge/points/{id}` | 更新知识点（管理） |

---

### 9.2 智能题目生成与练习系统

#### 9.2.1 功能描述

基于知识库和用户画像，**智能生成针对性练习题目**。系统根据学生的薄弱环节、当前学习计划的进度、知识掌握程度，动态生成不同难度的题目，并提供自动批改和详细解析。

#### 9.2.2 功能详情

**题目类型**：

| 类型 | 说明 | 自动批改 |
|------|------|---------|
| 单选题 | 4 选 1，考查概念理解 | ✅ 自动 |
| 多选题 | 5 选 N，考查综合理解 | ✅ 自动 |
| 填空题 | 关键概念填空 | ✅ 模糊匹配 |
| 编程题 | 在线编码，支持多语言 | ✅ 测试用例 |
| 简答题 | 开放性问题 | 🤖 AI 评分 |

**智能出题策略**：

```
用户画像 + 学习计划 + 知识库
    │
    ▼
题目生成智能体（新增）
    │
    ├── 分析用户薄弱环节 → 针对性出题
    ├── 根据学习进度 → 匹配当前阶段难度
    ├── 避免重复 → 查询历史答题记录
    └── 难度递增 → 根据正确率自适应调整
    │
    ▼
返回题目列表（含选项、正确答案、解析）
```

**答题流程**：

```
用户进入练习页面
    │
    ▼
选择练习模式：
    ├── 每日练习（系统推荐 10 题）
    ├── 专项练习（按课程/知识点筛选）
    ├── 错题重练（历史错题回顾）
    └── 模拟考试（限时综合测试）
    │
    ▼
逐题作答 → 实时提交
    │
    ▼
自动批改 → 返回正确答案 + 详细解析
    │
    ▼
更新用户画像（知识掌握度变化）
    │
    ▼
生成答题报告（正确率、薄弱点、建议）
```

#### 9.2.3 数据模型设计

```sql
-- 题目表
CREATE TABLE question (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    knowledge_point_id BIGINT,         -- 关联知识点
    type VARCHAR(20) NOT NULL,         -- single_choice / multi_choice / fill_blank / coding / essay
    difficulty INT DEFAULT 1,          -- 难度 1-5
    content TEXT NOT NULL,             -- 题目内容（Markdown）
    options JSON,                      -- 选项（选择题）
    answer TEXT NOT NULL,              -- 正确答案
    explanation TEXT,                  -- 解析
    test_cases JSON,                   -- 测试用例（编程题）
    scoring_rubric TEXT,               -- 评分标准（简答题）
    ai_generated BOOLEAN DEFAULT TRUE, -- 是否 AI 生成
    created_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (knowledge_point_id) REFERENCES knowledge_point(id)
);

-- 答题记录表
CREATE TABLE answer_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    user_answer TEXT,                  -- 用户作答
    is_correct BOOLEAN,               -- 是否正确
    score DECIMAL(5,2),               -- 得分
    time_spent INT,                    -- 用时（秒）
    practice_mode VARCHAR(20),         -- 练习模式
    answered_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES app_user(id),
    FOREIGN KEY (question_id) REFERENCES question(id)
);
```

#### 9.2.4 新增接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/quiz/generate` | 智能生成题目 |
| `POST` | `/api/quiz/submit` | 提交答案 |
| `GET` | `/api/quiz/history` | 答题历史 |
| `GET` | `/api/quiz/report` | 答题报告 |
| `GET` | `/api/quiz/wrong-questions` | 错题本 |
| `POST` | `/api/quiz/code/run` | 运行编程题代码 |

#### 9.2.5 新增智能体

| 智能体 | 职责 |
|--------|------|
| **Question Generation Agent** | 根据知识点和难度要求生成题目 |
| **Answer Evaluation Agent** | 对简答题和编程题进行 AI 评分 |
| **Learning Analysis Agent** | 分析答题数据，生成学习建议 |

---

### 9.3 学习任务提交与分析答疑

#### 9.3.1 功能描述

学生可以**提交学习任务**（代码、文档、截图等），系统自动分析任务完成情况，提供详细的反馈和答疑。支持 AI 自动分析 + 教师人工审核的混合模式。

#### 9.3.2 功能详情

**任务类型**：

| 类型 | 提交形式 | 分析方式 |
|------|---------|---------|
| 代码作业 | 代码文件 / 在线编辑器 | AI 代码审查 + 测试用例运行 |
| 文档作业 | PDF/DOCX/Markdown | AI 内容分析 + 关键点检查 |
| 截图作业 | 图片（实验截图等） | 多模态 AI 图像分析 |
| 思维导图 | JSON / 图片 | AI 结构分析 |

**任务流程**：

```
教师/系统发布学习任务
    │
    ▼
学生查看任务要求
    │
    ▼
学生提交作业（文件/代码/截图）
    │
    ▼
系统自动分析
    ├── 代码类：运行测试用例 + AI 代码审查
    ├── 文档类：AI 内容分析 + 关键点覆盖检查
    └── 图片类：多模态 AI 图像理解
    │
    ▼
生成分析报告
    ├── 完成度评分（0-100）
    ├── 优点列表
    ├── 改进建议
    └── 相关知识点链接
    │
    ▼
学生查看报告 → 可发起追问答疑
    │
    ▼
AI 答疑对话（上下文包含任务内容 + 分析报告）
```

**答疑系统**：

```
学生提问（支持文字 + 图片 + 代码）
    │
    ▼
答疑智能体
    │
    ├── 检索知识库相关知识点
    ├── 分析用户画像（认知风格、知识水平）
    ├── 结合当前任务上下文
    └── 生成个性化解答
    │
    ▼
返回解答（Markdown，含代码示例、图示建议）
```

#### 9.3.3 数据模型设计

```sql
-- 学习任务表
CREATE TABLE learning_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    description TEXT,                  -- 任务要求（Markdown）
    task_type VARCHAR(20),             -- code / document / screenshot / mindmap
    related_course_id BIGINT,          -- 关联课程
    related_points JSON,               -- 关联知识点 ID 列表
    deadline DATETIME,                 -- 截止时间
    max_score INT DEFAULT 100,         -- 满分
    created_at DATETIME DEFAULT NOW()
);

-- 任务提交表
CREATE TABLE task_submission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content TEXT,                      -- 提交内容（代码/文本）
    file_paths JSON,                   -- 附件路径列表
    submitted_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (task_id) REFERENCES learning_task(id),
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);

-- 分析报告表
CREATE TABLE analysis_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    score DECIMAL(5,2),               -- 得分
    strengths JSON,                   -- 优点列表
    weaknesses JSON,                  -- 不足列表
    suggestions JSON,                 -- 改进建议
    related_points JSON,              -- 相关知识点
    ai_analysis TEXT,                 -- AI 详细分析
    created_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (submission_id) REFERENCES task_submission(id)
);

-- 答疑记录表
CREATE TABLE qa_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    submission_id BIGINT,             -- 关联的任务提交（可选）
    context TEXT,                     -- 上下文信息
    created_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);

CREATE TABLE qa_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,        -- user / assistant
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (session_id) REFERENCES qa_session(id)
);
```

#### 9.3.4 新增接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/tasks` | 获取任务列表 |
| `GET` | `/api/tasks/{id}` | 获取任务详情 |
| `POST` | `/api/tasks/{id}/submit` | 提交任务 |
| `GET` | `/api/tasks/submissions/{id}/report` | 获取分析报告 |
| `POST` | `/api/qa/ask` | 提交答疑问题 |
| `GET` | `/api/qa/history` | 答疑历史 |
| `POST` | `/api/qa/sessions` | 创建答疑会话 |

#### 9.3.5 新增智能体

| 智能体 | 职识 |
|--------|------|
| **Code Review Agent** | 分析代码质量、风格、正确性 |
| **Document Analysis Agent** | 分析文档内容的完整性和准确性 |
| **Q&A Agent** | 基于知识库和上下文的智能答疑 |

---

### 9.4 知识点 PPT 与视频生成

#### 9.4.1 功能描述

基于知识库内容，利用 AI 自动生成**知识点讲解 PPT** 和**教学视频**，降低教师备课成本，为学生提供多媒体学习资源。

#### 9.4.2 PPT 自动生成

**生成流程**：

```
用户选择知识点 / 学习计划中的主题
    │
    ▼
PPT 生成智能体
    │
    ├── 从知识库提取知识点内容
    ├── 设计 PPT 大纲结构
    │   ├── 封面页
    │   ├── 目录页
    │   ├── 知识点讲解页（每页一个要点）
    │   ├── 代码示例页
    │   ├── 练习题页
    │   └── 总结页
    ├── 为每页生成详细内容
    └── 选择模板风格
    │
    ▼
PPT 渲染引擎
    ├── python-pptx 生成 .pptx 文件
    └── 支持自定义模板、配色、字体
    │
    ▼
返回 PPT 文件下载链接
```

#### 9.4.3 教学视频生成

**技术方案**：

```
知识点内容
    │
    ▼
脚本生成智能体
    │
    ├── 生成视频脚本（旁白文本 + 时间轴）
    ├── 设计每帧画面内容
    └── 规划动画效果
    │
    ▼
视频合成引擎
    │
    ├── TTS 语音合成（旁白音频）
    │   ├── MiMo TTS API / Edge TTS
    │   └── 支持多种语音风格
    │
    ├── 画面渲染
    │   ├── PPT 页面 → 图片序列
    │   ├── 代码高亮动画
    │   └── 知识图谱可视化动画
    │
    ├── 字幕生成
    │   └── 旁白文本 → SRT 字幕文件
    │
    └── 视频合成
        ├── FFmpeg 合成音视频
        └── 输出 MP4 文件
    │
    ▼
返回视频文件下载链接 + 在线播放
```

**视频参数**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| 分辨率 | 1920×1080 | 支持 720p/1080p/4K |
| 帧率 | 30fps | — |
| 语音 | 普通话女声 | 支持多种音色选择 |
| 语速 | 1.0x | 支持 0.75x - 2.0x |
| 字幕 | 中文硬字幕 | 可选关闭 |
| 时长 | 1-5 分钟/知识点 | 根据内容自动调整 |

#### 9.4.4 数据模型设计

```sql
-- PPT 资源表
CREATE TABLE ppt_resource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    knowledge_point_ids JSON,          -- 关联知识点列表
    template_name VARCHAR(50),         -- 使用的模板
    file_path VARCHAR(500),            -- 文件存储路径
    slide_count INT,                   -- 页数
    user_id BIGINT,                    -- 创建者
    created_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);

-- 视频资源表
CREATE TABLE video_resource (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    knowledge_point_ids JSON,          -- 关联知识点列表
    script TEXT,                       -- 视频脚本
    file_path VARCHAR(500),            -- 视频文件路径
    thumbnail_path VARCHAR(500),       -- 封面图路径
    subtitle_path VARCHAR(500),        -- 字幕文件路径
    duration INT,                      -- 时长（秒）
    resolution VARCHAR(20),            -- 分辨率
    voice_style VARCHAR(50),           -- 语音风格
    user_id BIGINT,                    -- 创建者
    created_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);
```

#### 9.4.5 新增接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/media/ppt/generate` | 生成 PPT |
| `GET` | `/api/media/ppt/{id}/download` | 下载 PPT |
| `POST` | `/api/media/video/generate` | 生成视频 |
| `GET` | `/api/media/video/{id}/stream` | 在线播放视频 |
| `GET` | `/api/media/video/{id}/download` | 下载视频 |
| `GET` | `/api/media/templates` | 获取 PPT 模板列表 |
| `GET` | `/api/media/voices` | 获取语音风格列表 |

#### 9.4.6 新增智能体

| 智能体 | 职责 |
|--------|------|
| **PPT Outline Agent** | 根据知识点设计 PPT 大纲和每页内容 |
| **Video Script Agent** | 生成视频脚本（旁白 + 画面描述 + 时间轴） |
| **TTS Agent** | 调用语音合成 API 生成旁白音频 |

#### 9.4.7 技术依赖

| 组件 | 技术选型 | 用途 |
|------|---------|------|
| PPT 生成 | `python-pptx` | Python 生成 .pptx 文件 |
| 语音合成 | Edge TTS / MiMo TTS | 文本转语音 |
| 视频合成 | FFmpeg | 音视频合成 |
| 代码高亮 | Pygments | 代码截图渲染 |
| 图表渲染 | Matplotlib / Pillow | 静态图表生成 |

---

### 9.5 Live2D 接入语音模型

#### 9.5.1 功能描述

将现有的 Live2D 看板娘升级为**语音交互助手**，实现"看板娘说话"的效果。用户可以通过语音与 AI 对话，Live2D 角色根据语音内容同步口型和表情，提供沉浸式的学习体验。

#### 9.5.2 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3)                              │
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│  │ Live2D 渲染   │◄── │ 口型驱动模块   │◄── │ 音频播放模块   │       │
│  │(oh-my-live2d)│    │ (LipSync)    │    │ (Web Audio)  │       │
│  └──────────────┘    └──────────────┘    └──────┬───────┘       │
│                                                 │               │
│  ┌──────────────┐    ┌──────────────┐           │               │
│  │ 语音识别模块   │───→│  语音交互控制  │───────────┘               │
│  │ (Web Speech) │    │              │                           │
│  └──────┬───────┘    └──────────────┘                           │
│         │                                                       │
└─────────┼───────────────────────────────────────────────────────┘
          │ 音频流 / 文本
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                    后端 / AI 服务                                 │
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐       │
│  │ 语音识别 ASR  │    │ AI 对话智能体  │    │ 语音合成 TTS  │        │
│  │ (Whisper API)│    │ (Chat Agent) │    │ (Edge TTS)   │       │
│  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘       │
│         │                   │                   │               │
│         └───────────────────┴───────────────────┘               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

#### 9.5.3 功能模块详解

##### 9.5.3.1 语音识别（ASR）

**方案选择**：

| 方案 | 优势 | 劣势 |
|------|------|------|
| Web Speech API | 浏览器原生，零延迟 | 中文识别率一般，Chrome 专属 |
| Whisper API | 识别率高，多语言 | 需要上传音频，有延迟 |
| 实时流式 ASR | 低延迟，边说边识别 | 实现复杂度高 |

**推荐方案**：Web Speech API（快速体验）+ Whisper API（高精度模式）

**实现流程**：

```
用户点击麦克风按钮
    │
    ▼
浏览器 Web Speech API 开始录音
    │
    ├── 实时显示识别文本（ interim results ）
    │
    ▼
用户说完 → 识别完成
    │
    ▼
发送识别文本到 AI 对话接口
    │
    ▼
接收 AI 回复文本
    │
    ▼
调用 TTS 生成语音
    │
    ▼
播放语音 + Live2D 口型同步
```

##### 9.5.3.2 语音合成（TTS）

**方案选择**：

| 方案 | 优势 | 劣势 |
|------|------|------|
| Edge TTS | 免费、中文效果好、多音色 | 需要网络 |
| MiMo TTS API | 与项目统一 API，暂时免费 | 可能收费 |
| 浏览器 SpeechSynthesis | 零延迟、离线可用 | 音质差、不自然 |

**推荐方案**：Edge TTS（主力）+ 浏览器 SpeechSynthesis（降级）

##### 9.5.3.3 Live2D 口型同步（LipSync）

**原理**：通过分析音频的音量（振幅）或音素信息，驱动 Live2D 模型的嘴部参数。

```
音频播放
    │
    ▼
Web Audio API → AnalyserNode
    │
    ├── 提取音量数据（byteFrequencyData）
    │
    ▼
映射到 Live2D 口型参数
    │
    ├── ParamMouthOpenY: 音量 → 嘴巴张开程度
    ├── ParamMouthOpenX: 音量 → 嘴巴宽度
    └── ParamMouthForm: 音素 → 嘴型（O/A/I 等）
    │
    ▼
每帧更新 Live2D 模型参数
    │
    ▼
实现口型同步效果
```

**高级方案**：使用音素级别的口型映射（viseme），实现更自然的口型效果：

| 音素 | 口型 | Live2D 参数 |
|------|------|------------|
| A (啊) | 大张嘴 | MouthOpenY: 1.0 |
| I (衣) | 嘴角拉伸 | MouthForm: 1.0 |
| U (乌) | 嘟嘴 | MouthOpenY: 0.5, MouthForm: -0.5 |
| O (哦) | 圆嘴 | MouthOpenY: 0.8 |
| E (额) | 中等张嘴 | MouthOpenY: 0.6 |
| 闭嘴 | 闭合 | MouthOpenY: 0.0 |

##### 9.5.3.4 表情与动作联动

除了口型同步，Live2D 角色还可以根据对话内容展示不同表情：

| 场景 | 表情/动作 | 触发条件 |
|------|----------|---------|
| 思考中 | 微微歪头 + 眨眼 | AI 正在生成回复 |
| 开心 | 微笑 + 眼睛弯弯 | 用户答对题目 |
| 鼓励 | 握拳动作 | 用户遇到困难 |
| 讲解 | 正常说话表情 | AI 正在讲解知识 |
| 惊讶 | 睁大眼睛 | 用户提出有趣问题 |

**实现方式**：在 AI 回复文本中嵌入表情标签（如 `[emotion:happy]`），前端解析后切换 Live2D 表情预设。

#### 9.5.4 技术实现方案

##### 前端新增模块

```typescript
// services/voiceService.ts — 语音交互服务
class VoiceService {
  // ASR：语音识别
  startListening(): void              // 开始录音识别
  stopListening(): Promise<string>    // 停止录音，返回识别文本

  // TTS：语音合成
  synthesize(text: string): Promise<ArrayBuffer>  // 文本转语音音频
  playAudio(audioBuffer: ArrayBuffer): void        // 播放音频

  // LipSync：口型同步
  startLipSync(audioElement: HTMLAudioElement): void  // 开始口型驱动
  stopLipSync(): void                                  // 停止口型驱动
}

// services/lipSyncService.ts — 口型同步服务
class LipSyncService {
  private audioContext: AudioContext
  private analyser: AnalyserNode

  // 分析音频振幅，映射到 Live2D 参数
  getMouthOpenValue(): number    // 返回 0.0 - 1.0
  getMouthFormValue(): number   // 返回 -1.0 - 1.0
}
```

##### 后端新增接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/voice/tts` | 文本转语音（调用 Edge TTS） |
| `POST` | `/api/voice/asr` | 语音转文本（调用 Whisper API） |
| `GET` | `/api/voice/voices` | 获取可用音色列表 |

##### Python AI 服务新增

| 端点 | 说明 |
|------|------|
| `POST /tts` | 调用 Edge TTS 生成语音文件 |
| `POST /asr` | 调用 Whisper API 识别语音 |

#### 9.5.5 数据模型

```sql
-- 语音配置表
CREATE TABLE voice_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    voice_id VARCHAR(100) DEFAULT 'zh-CN-XiaoxiaoNeural',  -- 音色
    speech_rate FLOAT DEFAULT 1.0,                          -- 语速
    pitch FLOAT DEFAULT 1.0,                                -- 音调
    auto_speak BOOLEAN DEFAULT TRUE,                        -- 自动朗读回复
    lip_sync_enabled BOOLEAN DEFAULT TRUE,                  -- 口型同步
    created_at DATETIME DEFAULT NOW(),
    updated_at DATETIME DEFAULT NOW(),
    FOREIGN KEY (user_id) REFERENCES app_user(id)
);
```

#### 9.5.6 开发计划

| 阶段 | 内容 | 预计工期 |
|------|------|---------|
| **Phase 1** | TTS 基础能力（Edge TTS 后端 + 前端音频播放） | 1 周 |
| **Phase 2** | Live2D 口型同步（Web Audio API + 振幅分析） | 1 周 |
| **Phase 3** | ASR 语音输入（Web Speech API 快速实现） | 1 周 |
| **Phase 4** | 表情联动 + 高级口型（音素映射） | 1 周 |
| **Phase 5** | 语音配置页面 + 用户偏好持久化 | 3 天 |
| **Phase 6** | 优化与测试（延迟、准确率、体验） | 1 周 |

---

## 10. 部署与运行指南

### 10.1 环境准备

| 依赖 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 17+ | OpenJDK 或 Oracle JDK |
| Maven | 3.8+ | 项目内置 Maven Wrapper |
| Python | 3.10+ | 推荐 3.12 |
| Node.js | 18+ | 推荐 20 LTS |
| MySQL | 8.0+ | 需提前创建数据库 |

### 10.2 快速启动

#### Step 1：准备数据库

```sql
CREATE DATABASE edu_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### Step 2：配置 API Key

```bash
# Windows
set MIMO_API_KEY=your_api_key_here

# Linux / macOS
export MIMO_API_KEY=your_api_key_here
```

#### Step 3：启动 Python AI 服务

```bash
cd python-ai
pip install -r requirements.txt
uvicorn main:app --port 8000
```

#### Step 4：启动 Spring Boot 后端

```bash
cd backend
# Windows
./mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

#### Step 5：启动前端

```bash
cd frontend
npm install
npm run dev
```

#### Step 6：访问应用

打开浏览器访问 `http://localhost:5173`

默认账户：用户名 `viuuro`，密码 `529`

### 10.3 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端开发服务器 | 5173 | Vite Dev Server |
| Spring Boot 后端 | 8080 | REST API |
| Python AI 服务 | 8000 | LangChain 智能体 |
| MySQL | 3306 | 数据库 |

---

## 11. 附录

### 11.1 项目依赖清单

#### 后端核心依赖（pom.xml）

| 依赖 | 版本 | 用途 |
|------|------|------|
| spring-boot-starter-web | 3.2.x | Web 框架 |
| spring-boot-starter-data-jpa | 3.2.x | ORM 框架 |
| mysql-connector-j | 8.x | MySQL 驱动 |
| lombok | — | 代码简化 |
| spring-boot-starter-webflux | 3.2.x | WebClient HTTP 调用 |

#### Python 核心依赖（requirements.txt）

| 依赖 | 版本 | 用途 |
|------|------|------|
| fastapi | 0.115.0 | Web 框架 |
| uvicorn | 0.30.6 | ASGI 服务器 |
| langchain | 0.3.7 | LLM 编排框架 |
| langchain-openai | 0.2.9 | OpenAI 兼容客户端 |
| langchain-core | 0.3.21 | LangChain 核心 |
| pypdf | — | PDF 解析 |
| python-docx | — | DOCX 解析 |
| python-multipart | — | 文件上传支持 |

#### 前端核心依赖（package.json）

| 依赖 | 版本 | 用途 |
|------|------|------|
| vue | 3.x | UI 框架 |
| pinia | 2.x | 状态管理 |
| element-plus | 2.x | UI 组件库 |
| echarts | 5.x | 图表库 |
| markdown-it | — | Markdown 渲染 |
| oh-my-live2d | — | Live2D 组件 |
| axios | — | HTTP 客户端 |
| vite | 5.x | 构建工具 |

### 11.2 画像 JSON 格式示例

```json
{
  "knowledgeBase": 6,
  "cognitiveStyle": "visual",
  "weaknessPoints": ["算法复杂度分析", "动态规划", "图论"],
  "learningPace": 7,
  "interestAreas": ["Web 开发", "机器学习", "系统设计"],
  "shortTermGoal": "掌握数据结构与算法，准备秋招面试"
}
```

### 11.3 学习计划 JSON 格式示例

```json
{
  "weeks": [
    {
      "weekNumber": 1,
      "topic": "数组与链表基础",
      "tasks": [
        "学习数组的内存结构与随机访问原理",
        "掌握单链表、双链表的实现与操作",
        "完成 10 道数组/链表基础题目"
      ],
      "resources": [
        {
          "title": "【数据结构】数组与链表详解",
          "url": "https://www.bilibili.com/video/...",
          "platform": "Bilibili",
          "type": "video"
        },
        {
          "title": "LeetCode 数组入门题单",
          "url": "https://leetcode.cn/study-plan/",
          "platform": "LeetCode",
          "type": "exercise"
        }
      ]
    }
  ]
}
```

### 11.4 SSE 事件流示例

```
event: conversation_id
data: {"conversationId": "550e8400-e29b-41d4-a716-446655440000"}

event: content
data: {"type": "content", "content": "你"}

event: content
data: {"type": "content", "content": "好"}

event: content
data: {"type": "content", "content": "！"}

event: content
data: {"type": "content", "content": "根据"}

event: content
data: {"type": "content", "content": "你的"}

event: profile_update
data: {"type": "profile_update", "profile": {"knowledgeBase": 6, "cognitiveStyle": "visual", ...}}

event: done
data: {"message": "done"}
```

---

> **最后更新**：2026 年 5 月 31 日  
> **维护者**：李轩，杨晋，潘哲浩
