# 图片学习资源智能体接入方案

## 当前已打通的 MVP

聊天输入框上方可选择“问答 / 图片”：

- 问答：继续使用现有 SSE 对话链路。
- 图片：Spring 结合当前用户、对话和知识库检索上下文，请求 Python `/artifacts/image`。配置图片模型时调用 OpenAI 兼容的 `/images/generations`；未配置时返回本地 SVG 学习卡片，保证交互链路可验证。

学习总览中的智能体推荐资源已按系统课程、视频、文章、练习、图片和其他资源分类折叠。

## 推荐的正式架构

```text
聊天框输出类型
      │
      ▼
输出任务路由器
      ├── TEXT ──► 导师智能体 ──► SSE Markdown
      └── IMAGE ─► 视觉策划智能体 ─► 图片生成服务 ─► 图片审核智能体
                              │
                              ▼
                    资源存储 + 收藏夹 + 学习证据
```

### 输出任务路由器

输入应包含 `outputType`、用户要求、课程/章节/知识点 key、画像摘要、知识库引用和会话上下文。路由器只决定执行链，不应让模型根据自然语言偷偷切换到高成本资源生成。

### 图片链路

1. 视觉策划智能体把用户要求转换为结构化 `visual_spec`：用途、受众、画面类型、必须出现的概念、禁止内容、尺寸和风格。
2. 对流程图、数据结构状态图、CPU 数据通路等要求精确文字和连线的内容，优先生成 Mermaid/SVG；照片式或插画式内容再交给图片模型。
3. 图片审核智能体进行 OCR、概念覆盖、文字准确性和安全检查。不合格时只重生成局部提示词，限制最多两次。
4. 产物进入对象存储，数据库保存模型、提示词摘要、课程/章节、知识块引用、审核结果和资源 URL。

## 后续数据模型

正式部署建议增加：

- `learning_artifact`：`id/user_id/conversation_id/output_type/status/title/storage_url/preview_url/model/provider/prompt_digest/created_at`。
- `artifact_binding`：关联 `course_key/chapter_key/point_key/chunk_id`。
- `artifact_revision`：保存视觉规范版本、父版本和审核结果。
- `artifact_job`：支持异步生成、进度、重试、取消和失败原因。

资源生成应改为异步任务：`POST /api/artifacts` 返回 `jobId`，前端通过 SSE 订阅进度；完成后消息卡片展示预览、下载、收藏、重新生成和局部修改。

## 质量与安全门槛

- 所有课程事实必须携带知识块引用；无知识库时明确标记为通用草稿。
- 图片中的公式、地址位、树/图边和控制信号必须经过 OCR 或结构化视觉审核。
- 外部图片模型密钥只保存在 Python 服务环境变量中，浏览器和 Spring 数据库不保存密钥。
- 对输入图片执行 MIME、大小、分辨率和恶意内容检查；产物下载使用短期签名 URL。
- 按用户和生成类型限流，记录模型、耗时、成本和审核分，方便后续评测。

## 配置

```bash
IMAGE_GENERATION_API_BASE=https://provider.example/v1
IMAGE_GENERATION_API_KEY=...
IMAGE_GENERATION_MODEL=...
```

图片服务需兼容 `POST /images/generations`，并返回 `data[0].b64_json` 或 `data[0].url`。具体供应商变化时只替换适配器配置，不改聊天、知识库和收藏业务层。
