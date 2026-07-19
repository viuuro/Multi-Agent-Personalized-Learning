package com.edu.agent.service;

import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.LearningPlan.PlanWeek;
import com.edu.agent.model.LearningPlan.Resource;
import com.edu.agent.model.UserProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mock AI 服务 —— 当没有 MiMo-v2.5 API Key 时提供预设响应
 *
 * 本服务模拟三个核心能力：
 *   1. 画像提取：根据用户消息中的关键词推断 6 维画像
 *   2. 对话回复：生成包含画像分析的预设回复文本
 *   3. 计划生成：生成包含具体官方课程、教材或已验证视频的 4 周学习计划
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
        String message = userMessage == null ? "" : userMessage.trim();
        String goal = profile.getShortTermGoal();
        if (goal == null || goal.isBlank()) {
            goal = "提升学习能力";
        }

        if (message.isBlank()) {
            return "我没有收到有效的消息，请重新输入你的学习问题。";
        }

        return "我收到了你的问题：**" + message + "**\n\n"
                + "当前 AI 服务暂时不可用，系统已进入本地降级模式，因此无法可靠生成完整回答。"
                + "请确认智能服务已启动，并检查服务配置后重试。\n\n"
                + "根据本地规则初步识别到的学习目标是：**" + goal + "**。";
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

        // 占位值不是学习方向；尚无有效画像时使用项目领域内可执行的默认主题。
        String mainTopic = profile.getInterestAreas().stream()
                .filter(this::isMeaningfulTopic)
                .findFirst().orElse("软件工程基础");
        String introductoryTopic = mainTopic.endsWith("基础")
                ? mainTopic + "入门"
                : mainTopic + "基础入门";

        // 第 1 周：基础入门
        PlanWeek week1 = createWeek(1, introductoryTopic,
                Arrays.asList(
                        "了解" + mainTopic + "的核心概念和知识体系",
                        "完成基础知识框架搭建",
                        "阅读入门教材前3章",
                        "整理学习笔记和思维导图"
                ),
                fallbackResources(mainTopic));

        // 第 2 周：进阶学习
        PlanWeek week2 = createWeek(2, mainTopic + "进阶学习",
                Arrays.asList(
                        "深入学习" + mainTopic + "的核心原理",
                        "完成配套练习和实践项目",
                        "阅读进阶教材第4-6章",
                        "参与在线讨论和答疑"
                ),
                fallbackResources(mainTopic));

        // 第 3 周：实战应用
        PlanWeek week3 = createWeek(3, mainTopic + "实战应用",
                Arrays.asList(
                        "完成一个综合实践项目",
                        "分析经典案例并总结规律",
                        "编写学习报告和技术总结",
                        "与同学或同行交流心得"
                ),
                fallbackResources(mainTopic));

        // 第 4 周：总结提升
        PlanWeek week4 = createWeek(4, mainTopic + "总结提升",
                Arrays.asList(
                        "回顾本月所学知识点",
                        "完成综合测评和自测",
                        "制定下一阶段学习目标",
                        "撰写学习总结和反思"
                ),
                fallbackResources(mainTopic));

        plan.setWeeks(Arrays.asList(week1, week2, week3, week4));
        return plan;
    }

    private boolean isMeaningfulTopic(String value) {
        if (value == null || value.isBlank()) return false;
        String compact = value.replaceAll("\\s+", "");
        return List.of("待评估", "待确定", "尚未确定", "未确定", "未知", "暂无", "未设置", "综合学习")
                .stream().noneMatch(compact::contains);
    }

    /** Mock/故障降级也只返回可直接打开的具体资源，绝不拼接搜索结果页。 */
    public List<Resource> fallbackResources(String topic) {
        String normalized = topic.toLowerCase(Locale.ROOT);
        if (normalized.contains("c++") || normalized.contains("cpp")) {
            return List.of(
                    new Resource("LearnCpp：C++ 系统教程", "https://www.learncpp.com/", "LearnCpp", "course"),
                    new Resource("C++ 语言参考手册", "https://zh.cppreference.com/w/cpp/language", "cppreference", "article"));
        }
        if (normalized.equals("c") || normalized.contains("c语言")) {
            return List.of(
                    new Resource("C 语言参考手册", "https://zh.cppreference.com/w/c/language", "cppreference", "article"),
                    new Resource("Beej's Guide to C Programming", "https://beej.us/guide/bgc/", "Beej", "course"));
        }
        if (normalized.contains("java ee") || normalized.contains("javaee") || normalized.contains("jakarta")) {
            return List.of(
                    new Resource("Jakarta EE 官方教程", "https://jakarta.ee/learn/", "Jakarta EE", "course"),
                    new Resource("Spring 官方入门指南", "https://spring.io/guides", "Spring", "course"));
        }
        if (normalized.contains("java")) {
            return List.of(
                    new Resource("【B站】韩顺平 Java 集合专题", "https://www.bilibili.com/video/BV1YA411T76k", "B站", "video"),
                    new Resource("Dev.java：Java 官方学习路径", "https://dev.java/learn/", "Oracle Java", "course"));
        }
        if (normalized.contains("python")) {
            return List.of(
                    new Resource("Python 3 官方中文教程", "https://docs.python.org/zh-cn/3/tutorial/", "Python Docs", "course"),
                    new Resource("MIT 6.0001：Python 编程导论", "https://ocw.mit.edu/courses/6-0001-introduction-to-computer-science-and-programming-in-python-fall-2016/", "MIT OpenCourseWare", "course"));
        }
        if (normalized.contains("数据库") || normalized.contains("mysql") || normalized.contains("sql")) {
            return List.of(
                    new Resource("MySQL 官方入门教程", "https://dev.mysql.com/doc/refman/8.4/en/tutorial.html", "MySQL", "course"),
                    new Resource("SQLBolt 交互式 SQL 课程", "https://sqlbolt.com/", "SQLBolt", "practice"));
        }
        if (normalized.contains("数据结构") || normalized.contains("算法")) {
            return List.of(
                    new Resource("VisuAlgo：数据结构与算法可视化", "https://visualgo.net/zh", "VisuAlgo", "practice"),
                    new Resource("OI Wiki：算法与数据结构知识库", "https://oi-wiki.org/", "OI Wiki", "article"));
        }
        if (normalized.contains("操作系统") || normalized.contains("linux")) {
            return List.of(
                    new Resource("OSTEP：Operating Systems: Three Easy Pieces", "https://pages.cs.wisc.edu/~remzi/OSTEP/", "OSTEP", "course"),
                    new Resource("Linux Journey 系统学习路径", "https://linuxjourney.com/", "Linux Journey", "course"));
        }
        if (normalized.contains("网络") || normalized.contains("tcp") || normalized.contains("udp")) {
            return List.of(
                    new Resource("Computer Networking 在线讲义与视频", "https://gaia.cs.umass.edu/kurose_ross/online_lectures.htm", "UMass", "course"),
                    new Resource("Cisco Networking Basics", "https://skillsforall.com/course/networking-basics", "Cisco Skills for All", "course"));
        }
        if (normalized.contains("编译")) {
            return List.of(
                    new Resource("Crafting Interpreters 在线教材", "https://craftinginterpreters.com/contents.html", "Crafting Interpreters", "course"),
                    new Resource("LLVM Kaleidoscope 编译器教程", "https://llvm.org/docs/tutorial/", "LLVM", "course"));
        }
        if (normalized.contains("离散数学")) {
            return List.of(
                    new Resource("Discrete Mathematics: An Open Introduction", "https://discrete.openmathbooks.org/dmoi3.html", "Open Math Books", "course"),
                    new Resource("MIT Mathematics for Computer Science", "https://courses.csail.mit.edu/6.042/spring18/mcs.pdf", "MIT", "course"));
        }
        if (normalized.contains("线性代数") || normalized.contains("矩阵")) {
            return List.of(
                    new Resource("MIT 18.06 Linear Algebra", "https://ocw.mit.edu/courses/18-06-linear-algebra-spring-2010/", "MIT OpenCourseWare", "course"),
                    new Resource("Immersive Linear Algebra", "https://immersivemath.com/ila/index.html", "Immersive Math", "course"));
        }
        if (normalized.contains("概率") || normalized.contains("统计")) {
            return List.of(
                    new Resource("OpenIntro Statistics 免费教材", "https://www.openintro.org/book/os/", "OpenIntro", "course"),
                    new Resource("Seeing Theory：概率统计可视化", "https://seeing-theory.brown.edu/", "Brown University", "practice"));
        }
        if (normalized.contains("计算机组成")) {
            return List.of(
                    new Resource("Nand2Tetris 官方课程", "https://www.nand2tetris.org/course", "Nand2Tetris", "course"),
                    new Resource("RISC-V Reader 在线资料", "https://riscv.org/technical/specifications/", "RISC-V", "article"));
        }
        if (normalized.contains("移动应用") || normalized.contains("android")) {
            return List.of(
                    new Resource("Android Basics with Compose", "https://developer.android.com/courses/android-basics-compose/course", "Android Developers", "course"),
                    new Resource("Android Developers 官方指南", "https://developer.android.com/guide", "Android Developers", "article"));
        }
        if (normalized.contains("游戏") || normalized.contains("unity")) {
            return List.of(
                    new Resource("Unity Essentials 官方学习路径", "https://learn.unity.com/pathway/unity-essentials", "Unity Learn", "course"),
                    new Resource("Unity Learn：Junior Programmer", "https://learn.unity.com/pathway/junior-programmer", "Unity Learn", "course"));
        }
        return List.of(
                new Resource("SWEBOK：软件工程知识体系", "https://www.computer.org/education/bodies-of-knowledge/software-engineering", "IEEE Computer Society", "article"),
                new Resource("Software Engineering at Google 在线书籍", "https://abseil.io/resources/swe-book/html/toc.html", "Google", "course"));
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
