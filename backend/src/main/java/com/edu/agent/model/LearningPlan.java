package com.edu.agent.model;

import java.util.List;

/**
 * 学习计划 —— 代表 4 周个性化学习计划的完整结构
 *
 * 由 Python AI 服务生成（通过 LangChain 调用 MiMo-v2.5 API），
 * Spring Boot 的 AgentOrchestrationService 将 JSON 响应转换为此对象。
 *
 * 数据流：
 *   前端 → POST /api/plan → PlanController → AgentOrchestrationService
 *     → Python AI /plan（LangChain → MiMo-v2.5 API）→ JSON → LearningPlan → 前端展示
 *
 * 前端展示方式：
 *   - 左侧面板"学习计划"标签页：以周卡片形式展示（PlanCard.vue）
 *   - 点击周卡片：Popover 气泡显示详细任务和资源
 *   - 展开模式：el-dialog 模态框显示完整计划
 *   - 编辑模式：可修改每周主题、任务、资源
 *
 * 注意：此为普通 POJO，不映射数据库（不使用 @Entity 注解），
 * 计划数据不持久化到 MySQL，每次由 AI 重新生成。
 */
public class LearningPlan {

    /** 无参构造函数（Jackson 反序列化需要） */
    public LearningPlan() {}

    /** 计划包含的周列表，共 4 周 */
    private List<PlanWeek> weeks;

    public List<PlanWeek> getWeeks() { return weeks; }
    public void setWeeks(List<PlanWeek> weeks) { this.weeks = weeks; }

    /**
     * 单周计划 —— 包含主题、任务列表和推荐资源
     *
     * 对应 Python AI 服务返回的 JSON 结构：
     * {
     *   "weekNumber": 1,
     *   "topic": "基础入门",
     *   "tasks": ["任务1", "任务2"],
     *   "resources": [{"title": "...", "url": "...", "platform": "...", "type": "..."}]
     * }
     */
    public static class PlanWeek {
        /** 无参构造函数（Jackson 反序列化需要） */
        public PlanWeek() {}

        /** 第几周（1-4） */
        private int weekNumber;
        /** 本周学习主题 */
        private String topic;
        /** 本周需要完成的具体任务列表 */
        private List<String> tasks;
        /** 本周推荐的学习资源链接 */
        private List<Resource> resources;

        public int getWeekNumber() { return weekNumber; }
        public void setWeekNumber(int weekNumber) { this.weekNumber = weekNumber; }

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }

        public List<String> getTasks() { return tasks; }
        public void setTasks(List<String> tasks) { this.tasks = tasks; }

        public List<Resource> getResources() { return resources; }
        public void setResources(List<Resource> resources) { this.resources = resources; }
    }

    /**
     * 学习资源 —— 推荐的外部链接
     *
     * 由 Python AI 服务生成，包含标题、URL、平台和类型信息。
     *
     * 资源类型说明：
     *   - video: 视频资源（如 B站视频）
     *   - course: 在线课程（如慕课网、中国大学MOOC）
     *   - article: 文章/教程（如博客、官方文档）
     *   - practice: 练习平台（如 LeetCode、GitHub）
     */
    public static class Resource {
        /** 资源标题（如 "【B站】Python 入门教程"） */
        private String title;
        /** 资源链接地址（完整的 URL） */
        private String url;
        /** 资源所在平台（如 B站、慕课网、GitHub、中国大学MOOC） */
        private String platform;
        /** 资源类型：video（视频）、course（课程）、article（文章）、practice（练习） */
        private String type;

        public Resource() {}

        public Resource(String title, String url, String platform, String type) {
            this.title = title;
            this.url = url;
            this.platform = platform;
            this.type = type;
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getPlatform() { return platform; }
        public void setPlatform(String platform) { this.platform = platform; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}
