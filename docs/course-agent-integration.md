# 数据结构与计算机组成原理：知识库及智能体接入方案

## 1. 目标边界

课程知识库不是“把教材切成文本块”即可完成。每一份内容都应同时服务于答疑、计划、出题、掌握度计算和资源推荐，因此必须让课程、章节、知识点、先修关系、学习目标和可验证题目之间可以稳定关联。

首期课程目录与学习总览保持一致：

- 数据结构：线性表、栈与队列、串/数组/广义表、树与二叉树、图、查找、排序、高级数据结构。
- 计算机组成原理：数据表示与编码、运算方法与运算器、存储系统、指令系统、中央处理器、控制器、输入输出系统、并行与性能。

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

### 离线建库

1. 课程编排智能体根据目录和先修关系生成课程 manifest，但不直接发布正文。
2. 内容质检智能体检查章节覆盖、术语一致性、公式/代码可执行性、来源与许可证。
3. 题目蓝图智能体为每个学习目标生成题型、难度、认知层级和评分规则。
4. Java 索引服务验收 manifest、保存课程语义层，并将正文写入现有 `knowledge_document/knowledge_chunk`。
5. 建立版本化发布流程：`DRAFT -> REVIEWED -> PUBLISHED -> RETIRED`，已发布版本不可原地覆盖。

当前 `SoftwareEngineeringKnowledgeSeeder` 会用 `replaceGlobalSeeds` 替换全部 `BUILTIN` 文档。正式接入两门完整课程前，应先将其改成按 `seed_namespace + version` 替换，避免一组课程种子删除另一组课程。

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

1. 先建立两门课程的 manifest、16 章目录和知识点/先修关系，冻结稳定 key。
2. 改造种子发布为按命名空间版本化，补 `chunk_binding` 与元数据过滤检索。
3. 每章先完成一个可闭环样板：内容、引用、题目蓝图、三档难度题和掌握证据。
4. 接入知识点级出题与审核，再将当前前端章节掌握度切换到 `/api/mastery/overview`。
5. 批量补齐两门课程，运行覆盖率、检索命中率、题目有效率和引用正确率评测后发布。
