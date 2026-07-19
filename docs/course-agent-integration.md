# 数据结构与计算机组成原理：知识库及智能体接入方案

## 1. 目标边界

课程知识库不是“把教材切成文本块”即可完成。每一份内容都应同时服务于答疑、计划、出题、掌握度计算和资源推荐，因此必须让课程、章节、知识点、先修关系、学习目标和可验证题目之间可以稳定关联。

当前课程目录直接从项目 Markdown 的正式章节口径维护，并与学习总览一致：

- 数据结构：14 章，覆盖复杂度、线性结构、串、树、图、排序、查找索引、算法设计、复杂性与进阶专题。
- 计算机组成原理：12 章，覆盖系统概论、数据表示、存储、指令、CPU、总线、I/O、数字逻辑、并行、辅助存储、指令集对比与综合复盘。

## 2. 建议的数据模型

在现有 `knowledge_document`、`knowledge_chunk` 之上补充课程语义层，正文仍由现有知识库服务负责分块和全文检索。

| 数据对象 | 关键字段 | 用途 |
| --- | --- | --- |
| `course` | `course_key`, `name`, `version`, `status` | 课程版本和发布状态 |
| `course_chapter` | `chapter_key`, `course_key`, `order_no`, `title` | 总览中的章节骨架 |
| `knowledge_point` | `point_key`, `chapter_key`, `title`, `difficulty`, `cognitive_level` | 最小教学与评估单元 |
| `knowledge_prerequisite` | `from_point_key`, `to_point_key`, `weight` | 先修知识图谱 |
| `learning_objective` | `objective_key`, `point_key`, `observable_behavior`, `acceptance_rule` | 让题目和掌握度具备可验收标准 |
| `chunk_binding` | `chunk_id`, `point_key`, `content_role` | 将现有文本块绑定到概念、例题、误区或总结 |
| `assessment_blueprint` | `objective_key`, `question_type`, `difficulty`, `rubric_json` | 约束出题与审核智能体 |
| `mastery_evidence` | `user_id`, `point_key`, `evidence_type`, `score`, `occurred_at` | 汇总练习、成果和对话证据 |
| `knowledge_mastery` | `user_id`, `point_key`, `mastery`, `confidence`, `updated_at` | 可快速读取的知识点掌握快照 |

每个 Markdown 内容单元建议带机器可读元数据：

```yaml
course_key: data-structures
chapter_key: tree
point_key: binary-tree-traversal
title: 二叉树遍历
prerequisites: [tree-basic]
difficulty: 2
cognitive_levels: [UNDERSTAND, APPLY, ANALYZE]
content_role: concept
source_uri: https://...
license: CC-BY-4.0
version: 1.0.0
```

同一知识点至少准备概念讲解、 worked example、常见误区、练习蓝图和章节小结五类内容。切块时保持单块只承担一个教学意图，并把 `course_key/chapter_key/point_key/content_role` 写入检索元数据。

## 3. 智能体接入链路

### 当前离线建库

1. `CourseMarkdownKnowledgeSeeder` 在后端启动时读取 `knowledge base/Data Structure.md` 与 `knowledge base/Computer Organization Principles.md`。
2. Markdown 按标题层级与最大块长切分，分块保留“课程 > 章 > 节”的完整路径。
3. 两门课使用独立的 `COURSE_MARKDOWN` 种子组，与原有 `BUILTIN` 课程独立替换，不会互相删除。
4. 内容校验通过后写入 `knowledge_document/knowledge_chunk`；内容校验和不变时复用已有索引。
5. 可用 `KNOWLEDGE_SEED_CORE_COURSES_ENABLED` 控制启用，用 `KNOWLEDGE_COURSE_DIRECTORY` 覆盖目录。

当前两份文件共建成 2 个课程文档、166 个知识块；后续若引入课程版本发布，可在此基础上补 manifest 与 `DRAFT -> REVIEWED -> PUBLISHED -> RETIRED` 状态。

### 在线学习

1. 路由智能体识别课程、章节、知识点和用户意图。
2. 检索智能体先按课程元数据过滤，再做全文/向量召回和重排，返回带 `chunk_id` 的上下文。
3. 导学智能体依据先修掌握度、当前目标和学习节奏选择下一知识点。
4. 答疑智能体只基于允许的知识块回答，并返回引用。
5. 出题智能体读取 `assessment_blueprint` 生成候选题，审核智能体独立校验答案唯一性、难度和来源。
6. 掌握度智能体把提交结果、重试次数、认知层级和时间衰减写入 `mastery_evidence`，异步更新知识点与章节快照。
7. 资源智能体使用薄弱知识点、资源反馈和收藏行为重排推荐；收藏本身不直接提高掌握度。

## 4. 掌握度口径

学习总览当前版本使用可解释的 MVP 口径：计划覆盖作为低权重起点，练习提交率占 20%，正确率占 65%，练习覆盖量占 15%。没有作答证据时不会给出高掌握分。

知识库语义层上线后，建议改为知识点级证据模型：

```text
evidence = score
         × difficulty_weight
         × cognitive_level_weight
         × source_reliability
         × exp(-days_since / half_life)
```

章节掌握度由知识点按重要度加权，并单独返回 `confidence`。低证据量时即使答案全对，也应显示“证据不足”，不能直接标为完全掌握。

## 5. 接口建议

- `GET /api/courses`：课程与发布版本。
- `GET /api/courses/{courseKey}/chapters`：章节、知识点和先修关系。
- `GET /api/mastery/overview?courseKey=...`：课程/章节/知识点掌握度与置信度。
- `GET /api/mastery/{pointKey}/evidence`：可解释的证据明细。
- `POST /api/agent/next-step`：根据先修和掌握度选择下一学习动作。
- `POST /api/practice/questions/generate`：继续复用现有接口，但请求必须携带 `point_key` 和 `objective_key`。
- `GET/POST/DELETE /api/resource-collections`：本次已实现的用户收藏夹与资源归档接口。

## 6. 推荐实施顺序

1. 为 26 个现有课程章节冻结稳定 key，并逐步补知识点与先修关系 manifest。
2. 补 `chunk_binding` 和课程版本字段，使检索能按知识点与内容角色过滤。
3. 每章补齐可观测学习目标、三档题目蓝图和评分规则。
4. 将当前练习证据画像进一步拆成知识点级 `mastery_evidence`，前端改读 `/api/mastery/overview`。
5. 持续运行章节覆盖率、检索命中率、试题有效率和引用正确率评测后版本化发布。
