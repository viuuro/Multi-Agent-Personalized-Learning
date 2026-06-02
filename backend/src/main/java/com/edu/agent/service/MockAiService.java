package com.edu.agent.service;

import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.LearningPlan.PlanWeek;
import com.edu.agent.model.LearningPlan.Resource;
import com.edu.agent.model.UserProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Mock AI 服务 —— 当没有 MiMo-v2.5 API Key 时提供预设响应
 *
 * 本服务模拟三个核心能力：
 *   1. 画像提取：根据用户消息中的关键词推断 6 维画像
 *   2. 对话回复：生成包含画像分析的预设回复文本
 *   3. 计划生成：生成包含 B站/慕课链接的 4 周学习计划
 *
 * 切换到真实 MiMo-v2.5 API：
 *   设置环境变量 MIMO_API_KEY=your-key，系统自动切换到 Python AI 服务
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Service: 声明为 Spring 业务服务 Bean，由组件扫描自动注册到 IoC 容器
 *   - 被 ChatService、AgentOrchestrationService 通过构造器注入使用
 */
@Service  // 【Spring Boot】声明为业务服务 Bean
public class MockAiService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Mock 画像提取 —— 通过关键词匹配模拟 AI 分析
     *
     * 真实模式下，由 Python AI 服务的 LangChain 智能体调用 MiMo-v2.5 API 提取画像。
     *
     * @param userMessage 用户输入的原始消息
     * @return 包含 6 维画像数据的 UserProfile 对象
     */
    public UserProfile mockProfileExtraction(String userMessage) {
        UserProfile profile = new UserProfile();
        String msg = userMessage.toLowerCase();

        // 基于关键词的简单画像推断
        if (msg.contains("数学") || msg.contains("math")) {
            profile.setKnowledgeBase(6);
            profile.setWeaknessPoints(Arrays.asList("微积分", "线性代数"));
            profile.setInterestAreas(Arrays.asList("数学", "算法"));
            profile.setShortTermGoal("掌握高等数学核心概念");
        } else if (msg.contains("编程") || msg.contains("java") || msg.contains("python") || msg.contains("code")) {
            profile.setKnowledgeBase(7);
            profile.setCognitiveStyle("kinesthetic");
            profile.setWeaknessPoints(Arrays.asList("并发编程", "设计模式"));
            profile.setInterestAreas(Arrays.asList("编程", "软件工程", "后端开发"));
            profile.setShortTermGoal("成为全栈开发工程师");
        } else if (msg.contains("英语") || msg.contains("english")) {
            profile.setKnowledgeBase(5);
            profile.setCognitiveStyle("verbal");
            profile.setWeaknessPoints(Arrays.asList("听力理解", "口语表达"));
            profile.setInterestAreas(Arrays.asList("英语", "语言学"));
            profile.setShortTermGoal("通过英语六级考试");
        } else if (msg.contains("物理") || msg.contains("physics")) {
            profile.setKnowledgeBase(6);
            profile.setCognitiveStyle("visual");
            profile.setWeaknessPoints(Arrays.asList("电磁学", "热力学"));
            profile.setInterestAreas(Arrays.asList("物理学", "量子力学"));
            profile.setShortTermGoal("理解经典力学与电磁学");
        } else {
            // 默认：无法识别关键词时给通用画像
            profile.setKnowledgeBase(5);
            profile.setCognitiveStyle("visual");
            profile.setWeaknessPoints(Arrays.asList("基础概念", "实践应用"));
            profile.setInterestAreas(Arrays.asList("学习", "自我提升"));
            profile.setShortTermGoal("建立系统的知识体系");
        }

        profile.setLearningPace(5);
        profile.setUpdatedAt(java.time.LocalDateTime.now());

        // 序列化 transient 字段到 profileJson，以便存入 MySQL JSON 列
        try {
            Map<String, Object> json = Map.of(
                    "knowledgeBase", profile.getKnowledgeBase(),
                    "cognitiveStyle", profile.getCognitiveStyle(),
                    "weaknessPoints", profile.getWeaknessPoints(),
                    "learningPace", profile.getLearningPace(),
                    "interestAreas", profile.getInterestAreas(),
                    "shortTermGoal", profile.getShortTermGoal()
            );
            profile.setProfileJson(objectMapper.writeValueAsString(json));
        } catch (JsonProcessingException e) {
            profile.setProfileJson("{}");
        }

        return profile;
    }

    /**
     * Mock 对话回复 —— 生成包含画像分析结果的友好回复
     *
     * 回复内容引用用户的画像数据（知识评分、学习风格、薄弱点等），
     * 引导用户点击"生成学习计划"按钮完成后续操作。
     *
     * @param userMessage 用户输入消息
     * @param profile 当前用户画像（用于生成个性化回复）
     * @return Markdown 格式的回复文本
     */
    public String mockChatResponse(String userMessage, UserProfile profile) {
        String goal = profile.getShortTermGoal();
        if (goal == null || goal.isBlank()) {
            goal = "提升学习能力";
        }

        return "你好！我已经分析了你的学习情况。\n\n"
                + "根据我们的对话，我了解到你的学习目标是：**" + goal + "**。\n\n"
                + "你的知识基础评分为 **" + profile.getKnowledgeBase() + "/10**，"
                + "学习风格偏向 **" + (profile.getCognitiveStyle().equals("visual") ? "视觉型" :
                                        profile.getCognitiveStyle().equals("verbal") ? "语言型" : "动手实践型") + "**。\n\n"
                + "我注意到你在以下方面可以加强：\n"
                + profile.getWeaknessPoints().stream().map(w -> "- " + w + "\n").reduce("", String::concat) + "\n"
                + "为了更好地帮助你，你可以点击右侧的「生成学习计划」按钮，"
                + "我将为你制定一个为期4周的个性化学习计划。你也可以继续告诉我更多关于你学习习惯的信息！";
    }

    /**
     * Mock 计划生成 —— 基于用户画像生成包含推荐资源的 4 周学习计划
     *
     * 真实模式下，由 Python AI 服务的 LangChain 智能体调用 MiMo-v2.5 API 生成计划和资源。
     *
     * @param profile 当前用户画像
     * @return 包含 4 周完整计划结构的 LearningPlan 对象
     */
    public LearningPlan mockPlanGeneration(UserProfile profile) {
        LearningPlan plan = new LearningPlan();

        // 用用户最感兴趣的第一个领域作为学习主题
        String mainTopic = profile.getInterestAreas().isEmpty() ? "综合学习" : profile.getInterestAreas().get(0);
        // URL 编码主题关键词，防止中文字符导致链接失效
        String encodedTopic = URLEncoder.encode(mainTopic, StandardCharsets.UTF_8);

        // 第 1 周：基础入门
        PlanWeek week1 = createWeek(1, mainTopic + "基础入门",
                Arrays.asList(
                        "了解" + mainTopic + "的核心概念和知识体系",
                        "完成基础知识框架搭建",
                        "阅读入门教材前3章",
                        "整理学习笔记和思维导图"
                ),
                Arrays.asList(
                        new Resource("【B站】" + mainTopic + "零基础入门到精通教程", "https://search.bilibili.com/all?keyword=" + encodedTopic + "入门教程", "B站", "video"),
                        new Resource("【中国大学MOOC】" + mainTopic + "基础课程", "https://www.icourse163.org/search.htm?keyword=" + encodedTopic, "中国大学MOOC", "course")
                ));

        // 第 2 周：进阶学习
        PlanWeek week2 = createWeek(2, mainTopic + "进阶学习",
                Arrays.asList(
                        "深入学习" + mainTopic + "的核心原理",
                        "完成配套练习和实践项目",
                        "阅读进阶教材第4-6章",
                        "参与在线讨论和答疑"
                ),
                Arrays.asList(
                        new Resource("【B站】" + mainTopic + "进阶实战教程", "https://search.bilibili.com/all?keyword=" + encodedTopic + "进阶", "B站", "video"),
                        new Resource("【中国大学MOOC】" + mainTopic + "进阶课程", "https://www.icourse163.org/search.htm?keyword=" + encodedTopic + "进阶", "中国大学MOOC", "course")
                ));

        // 第 3 周：实战应用
        PlanWeek week3 = createWeek(3, mainTopic + "实战应用",
                Arrays.asList(
                        "完成一个综合实践项目",
                        "分析经典案例并总结规律",
                        "编写学习报告和技术总结",
                        "与同学或同行交流心得"
                ),
                Arrays.asList(
                        new Resource("【B站】" + mainTopic + "项目实战教程", "https://search.bilibili.com/all?keyword=" + encodedTopic + "项目实战", "B站", "video"),
                        new Resource("【GitHub】" + mainTopic + "学习资源合集", "https://github.com/search?q=" + encodedTopic + "+tutorial&type=repositories", "GitHub", "community")
                ));

        // 第 4 周：总结提升
        PlanWeek week4 = createWeek(4, mainTopic + "总结提升",
                Arrays.asList(
                        "回顾本月所学知识点",
                        "完成综合测评和自测",
                        "制定下一阶段学习目标",
                        "撰写学习总结和反思"
                ),
                Arrays.asList(
                        new Resource("【B站】" + mainTopic + "复习总结与面试题讲解", "https://search.bilibili.com/all?keyword=" + encodedTopic + "复习总结", "B站", "video"),
                        new Resource("【LeetCode】" + mainTopic + "相关练习题", "https://leetcode.cn/search/?query=" + encodedTopic, "LeetCode", "practice")
                ));

        plan.setWeeks(Arrays.asList(week1, week2, week3, week4));
        return plan;
    }

    /** 便捷方法：创建一个计划周 */
    private PlanWeek createWeek(int weekNum, String topic, List<String> tasks, List<Resource> resources) {
        PlanWeek week = new PlanWeek();
        week.setWeekNumber(weekNum);
        week.setTopic(topic);
        week.setTasks(tasks);
        week.setResources(resources);
        return week;
    }
}
