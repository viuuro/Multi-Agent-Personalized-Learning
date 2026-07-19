# 智能行为回归评测

本目录用于验证学习智能体的离线决策行为；成果提交、版本对比和成长档案由后端集成测试与在线验收共同覆盖。

## 1. 对话与计划离线评测

在 `python-ai` 目录运行：

```powershell
python evals/run_evals.py
```

该评测不调用 MiMo API，覆盖意图路由、计划动作、主动澄清、局部计划保护和重复回复拦截。模型在线评测可以在此基础上继续加入真实对话用例。

使用技术：Python 测试用例 + 本地规则/Mock 决策链。该层适合在没有 `MIMO_API_KEY` 时快速发现对话编排回归。

## 2. 成长档案集成测试

在项目根目录运行：

```powershell
cd backend
.\mvnw.cmd test
```

`ApplicationContextTest` 使用 Spring Boot、JUnit 5、H2 MySQL 兼容模式和显式 Mock 评价验证：

- 成果必须绑定学习计划中的具体 `weekNumber + taskIndex`；
- 首次提交建立版本 1 和成长基线；
- 同一任务再次提交形成版本 2，并记录上一版和实际对比版本；
- 评价结果保存五维分数、掌握点、进步证据、下一步挑战和个性化祝福；
- `scoreDelta` 与 `growthOutcome` 能反映相较上一版的变化；
- 评价薄弱点会反馈到会话记忆和学习画像证据。

该测试不消耗 MiMo 额度，但会完整经过 Spring Data JPA、事务提交后异步调度、专用 Executor 和评价持久化链路。

## 3. Python 评价服务静态检查

```powershell
cd python-ai
python -m py_compile main.py voice_clone_demo.py
```

该检查用于发现 `/evaluate` 结构化评价提示词和 MiMo 非流式语音克隆客户端的 Python 语法错误。

## 4. 真实 MiMo 在线验收

配置 `MIMO_API_KEY` 和已授权的 `MIMO_VOICE_REFERENCE_AUDIO`，依次启动 Python AI、Spring Boot 和前端，然后：

1. 生成至少包含一个具体任务的学习计划。
2. 点击“提交学习成果”，选择任务并上传 PDF、DOCX 或 TXT。
3. 确认成长档案显示综合分、五维分数、掌握点、历史行为联动和首次基线。
4. 对同一任务上传改进版，确认出现上一版分数、分差和具体进步证据。
5. 点击“播放祝福”，确认非流式 WAV 祝福可以播放，语气自然轻柔且不会自报姓名。

在线评价会将学习画像、当前计划、近期问题、附件元数据/文本摘要和上一版成果作为上下文，但不会发送历史图片的 Base64 原始数据。
