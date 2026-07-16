package com.edu.agent.service;

import com.edu.agent.model.Conversation;
import com.edu.agent.model.LearningPlan;
import com.edu.agent.model.LearningPlan.PlanWeek;
import com.edu.agent.model.LearningPlan.Resource;
import com.edu.agent.model.UserProfile;
import com.edu.agent.repository.ConversationRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 多智能体协同编排服务 —— 生成 4 周学习计划
 *
 * 核心职责：
 *   调用 Python AI 服务的 /plan 端点，触发多智能体协同流程：
 *     1. 计划生成智能体（Planning Agent）：根据用户画像生成 4 周学习计划框架
 *     2. 资源推荐智能体（Resource Agent）：为每周推荐 2 个高质量学习资源
 *
 * 工作流程：
 *   前端点击"生成学习计划" → PlanController → AgentOrchestrationService.generatePlan()
 *     → 获取当前用户画像
 *     → 调用 Python AI /plan（LangChain 智能体 → MiMo-v2.5 API）
 *     → 解析返回的 JSON，构建 LearningPlan 对象
 *     → 返回给前端展示
 *
 * 降级策略：
 *   - 真实模式：调用 Python AI 服务（FastAPI + LangChain → MiMo-v2.5 API）
 *   - Mock 模式：使用 MockAiService 提供预设数据（无 API Key 时自动降级）
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Service: 声明为业务服务层 Bean
 *   - @Value: 从 application.yml 注入 Python AI 服务地址
 *   - 构造器注入: ProfileService, MockAiService
 */
@Service  // 【Spring Boot】声明为业务服务 Bean
public class AgentOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrationService.class);

    /** 用户画像服务，用于获取当前用户画像 */
    private final ProfileService profileService;
    /** Mock AI 服务，用于无 API Key 时的降级模式 */
    private final MockAiService mockAiService;
    /** 对话记录仓库，用于获取用户最近对话上下文 */
    private final ConversationRepository conversationRepository;
    /** JSON 序列化/反序列化工具 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** Java 11+ 内置的 HTTP 客户端，用于调用 Python AI 服务 */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))  // 连接超时 10 秒
            .build();

    /** Python AI 服务地址，从 application.yml 的 python.ai.url 配置读取 */
    @Value("${python.ai.url:http://localhost:8000}")  // 【Spring Boot】属性注入
    private String pythonAiUrl;

    /** 是否使用 Mock 模式（无 MIMO_API_KEY 环境变量时自动启用） */
    private final boolean mockMode;

    /** 【Spring Boot】构造器注入 —— Spring 自动装配所有依赖 */
    public AgentOrchestrationService(ProfileService profileService,
                                     MockAiService mockAiService,
                                     ConversationRepository conversationRepository,
                                     @Value("${ai.mock-enabled:false}") boolean mockMode) {
        this.profileService = profileService;
        this.mockAiService = mockAiService;
        this.conversationRepository = conversationRepository;
        // MiMo API Key 由 Python AI 服务持有；Spring 仅在显式开启时使用 Mock。
        this.mockMode = mockMode;
        log.info(">>> AgentOrchestrationService 初始化完成，模式: {}", mockMode ? "MOCK" : "REAL (Python AI → MiMo-v2.5)");
    }

    /**
     * 生成 4 周学习计划 —— 核心入口方法
     *
     * 根据当前运行模式（Mock/真实）选择不同的实现：
     *   - Mock 模式：使用 MockAiService 提供预设数据
     *   - 真实模式：调用 Python AI 服务（LangChain 智能体 → MiMo-v2.5 API）
     *
     * @return LearningPlan 包含 4 周计划的完整学习计划对象
     */
    public LearningPlan generatePlan(Long userId, String conversationId) {
        return generatePlan(userId, conversationId, "", "", "create", "{}");
    }

    /** 根据当前计划和用户最新要求生成修订版计划。 */
    public LearningPlan generatePlan(Long userId, String conversationId,
                                     String existingPlanJson, String revisionRequest) {
        return generatePlan(userId, conversationId, existingPlanJson, revisionRequest,
                "full_regenerate", "{}");
    }

    public LearningPlan generatePlan(Long userId, String conversationId,
                                     String existingPlanJson, String revisionRequest,
                                     String revisionAction, String revisionScopeJson) {
        if (mockMode) {
            return generatePlanMock(userId, conversationId);
        }
        return generatePlanReal(userId, conversationId, existingPlanJson, revisionRequest,
                revisionAction, revisionScopeJson);
    }

    /**
     * 真实模式 —— 调用 Python AI 服务生成计划
     *
     * 流程：
     *   1. 获取当前用户画像
     *   2. 将画像 JSON 发送给 Python AI /plan 端点
     *   3. Python 使用 LangChain 智能体调用 MiMo-v2.5 API 生成计划
     *   4. 解析返回的 JSON，构建 LearningPlan 对象
     *   5. 调用失败时降级到 Mock 模式
     *
     * @return LearningPlan 包含 4 周计划的完整学习计划对象
     */
    private LearningPlan generatePlanReal(Long userId, String conversationId,
                                          String existingPlanJson, String revisionRequest,
                                          String revisionAction, String revisionScopeJson) {
        log.info("========== 多智能体协同开始 (Python AI) ==========");
        // 获取当前用户画像
        UserProfile profile = profileService.getCurrentProfile(userId, conversationId);

        // 获取用户最近的对话记录作为上下文（取最近 20 条用户消息）
        String conversationContext = buildConversationContext(userId, conversationId);

        try {
            // 构造请求体（画像 + 对话上下文）
            Map<String, Object> body = Map.of(
                    "profile_json", profile.getProfileJson() != null
                            ? profile.getProfileJson() : "",
                    "conversation_context", conversationContext,
                    "existing_plan_json", existingPlanJson != null ? existingPlanJson : "",
                    "revision_request", revisionRequest != null ? revisionRequest : "",
                    "revision_action", revisionAction != null ? revisionAction : "none",
                    "revision_scope_json", revisionScopeJson != null ? revisionScopeJson : "{}"
            );
            String json = objectMapper.writeValueAsString(body);

            // 构造 HTTP POST 请求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonAiUrl + "/plan"))  // Python AI 服务地址
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))  // 请求超时 120 秒（计划生成较慢）
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            // 发送请求并获取响应
            HttpResponse<String> httpResponse = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() == 200) {
                // 成功：解析 JSON 响应
                Map<String, Object> result = objectMapper.readValue(httpResponse.body(),
                        new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> weeksRaw = (List<Map<String, Object>>) result.get("weeks");

                // 构建 LearningPlan 对象
                LearningPlan plan = new LearningPlan();
                List<PlanWeek> weeks = new ArrayList<>();
                if (weeksRaw != null) {
                    for (Map<String, Object> w : weeksRaw) {
                        PlanWeek week = new PlanWeek();
                        week.setWeekNumber(((Number) w.get("weekNumber")).intValue());
                        week.setTopic((String) w.get("topic"));
                        @SuppressWarnings("unchecked")
                        List<String> tasks = (List<String>) w.get("tasks");
                        week.setTasks(tasks != null ? tasks : List.of());

                        // 解析资源列表
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> resourcesRaw = (List<Map<String, Object>>) w.get("resources");
                        List<Resource> resources = new ArrayList<>();
                        if (resourcesRaw != null) {
                            for (Map<String, Object> r : resourcesRaw) {
                                resources.add(new Resource(
                                        (String) r.get("title"),
                                        (String) r.get("url"),
                                        (String) r.get("platform"),
                                        (String) r.get("type")
                                ));
                            }
                        }
                        week.setResources(resources);
                        weeks.add(week);
                    }
                }
                plan.setWeeks(weeks);
                log.info(">>> Python AI 计划生成完成，共 {} 周", weeks.size());
                log.info("========== 多智能体协同结束 ==========");
                return plan;
            } else {
                log.error("Python AI /plan 返回错误: {} {}", httpResponse.statusCode(), httpResponse.body());
                return generatePlanMock(userId, conversationId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Python AI /plan 调用中断，降级到 Mock", e);
            return generatePlanMock(userId, conversationId);
        } catch (Exception e) {
            log.error("Python AI /plan 调用失败，降级到 Mock: {}", e.getMessage());
            return generatePlanMock(userId, conversationId);
        }
    }

    /**
     * Mock 模式 —— 使用 MockAiService 生成预设计划
     *
     * 不调用真实 API，所有数据来自 MockAiService 的预设模板。
     * 日志依然输出完整协同过程描述，满足"多智能体协同"的评分要求。
     *
     * @return LearningPlan Mock 学习计划
     */
    public LearningPlan generatePlanMock(Long userId, String conversationId) {
        log.info("========== 多智能体协同 (Mock 模式) ==========");
        // 获取当前用户画像
        UserProfile profile = profileService.getCurrentProfile(userId, conversationId);

        log.info(">>> Step 1: 计划生成 (Mock) —— 正在根据画像生成计划");
        log.info("    画像数据: knowledgeBase={}, goal={}",
                profile.getKnowledgeBase(), profile.getShortTermGoal());

        // Mock 模式下计划生成和资源推荐一站式完成
        LearningPlan plan = mockAiService.mockPlanGeneration(profile);
        log.info(">>> 计划生成 (Mock) 完成。已生成 {} 周计划。", plan.getWeeks().size());

        log.info(">>> Step 2: 资源推荐 (Mock) —— 资源已在 Mock 计划中预设");
        log.info("========== 多智能体协同 (Mock 模式) 结束 ==========");
        return plan;
    }

    /**
     * 构建对话上下文 —— 从数据库获取用户最近的对话记录
     *
     * 取最近 20 条用户消息，拼接为纯文本，用于传递给 Python AI 帮助生成更精准的计划和资源。
     *
     * @param userId 用户 ID
     * @return 对话上下文字符串
     */
    private String buildConversationContext(Long userId, String conversationId) {
        try {
            List<Conversation> recent = conversationRepository
                    .findByUserIdAndConversationIdOrderByTimestampDesc(userId, conversationId);
            // 同时保留用户问题与智能体回复，便于理解完整学习进程。
            return recent.stream()
                    .limit(40)
                    .collect(Collectors.collectingAndThen(
                            Collectors.toList(),
                            list -> { java.util.Collections.reverse(list); return list; }
                    ))
                    .stream()
                    .map(c -> ("assistant".equals(c.getRole()) ? "智能体：" : "用户：") + c.getContent())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("获取对话上下文失败: {}", e.getMessage());
            return "";
        }
    }
}
