package com.edu.agent.service;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/** 调用 Python 出题流水线，并在服务不可用时提供可作答的任务相关降级题目。 */
@Service
public class QuestionAiService {
    private static final Logger log = LoggerFactory.getLogger(QuestionAiService.class);
    private static final Pattern NON_SEMANTIC = Pattern.compile("[\\p{P}\\p{S}\\s]+");
    private static final Set<String> OBJECTIVE_TYPES = Set.of(
            "SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE");
    private static final long AI_COOLDOWN_MILLIS = 2 * 60 * 1000L;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private final boolean mockMode;
    private final AtomicLong aiUnavailableUntil = new AtomicLong(0);

    @Value("${python.ai.url:http://localhost:8000}")
    private String pythonAiUrl;

    public QuestionAiService(ObjectMapper objectMapper,
                             @Value("${ai.mock-enabled:false}") boolean mockMode) {
        this.objectMapper = objectMapper;
        this.mockMode = mockMode;
    }

    public List<GeneratedQuestion> generate(String profileJson, String weekTopic, String taskTitle,
                                            String questionType, String difficulty, int count,
                                            String knowledgeContext, List<Long> sourceChunkIds,
                                            List<GeneratedQuestion> historicalQuestions,
                                            int generationOffset) {
        List<Long> safeSourceIds = sourceChunkIds == null
                ? List.of() : sourceChunkIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        List<GeneratedQuestion> history = historicalQuestions == null ? List.of() : historicalQuestions;
        if (!mockMode && System.currentTimeMillis() >= aiUnavailableUntil.get()) {
            try {
                String body = objectMapper.writeValueAsString(Map.of(
                        "profile_json", profileJson == null ? "" : profileJson,
                        "week_topic", weekTopic == null ? "" : weekTopic,
                        "task_title", taskTitle == null ? "" : taskTitle,
                        "question_type", questionType,
                        "difficulty", difficulty,
                        "count", count,
                        "knowledge_context", knowledgeContext == null ? "" : knowledgeContext,
                        "source_chunk_ids", safeSourceIds,
                        "avoid_question_texts", history.stream().map(GeneratedQuestion::question).toList(),
                        "avoid_option_sets", history.stream().map(GeneratedQuestion::options).toList()));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(pythonAiUrl + "/questions/generate"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(25))
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    List<GeneratedQuestion> generated = parseAndValidate(
                            response.body(), questionType, safeSourceIds, count, history);
                    if (generated.size() >= count) {
                        aiUnavailableUntil.set(0);
                        return generated.subList(0, count);
                    }
                    if (!generated.isEmpty()) {
                        aiUnavailableUntil.set(0);
                        appendUnique(generated, fallback(weekTopic, taskTitle, questionType, difficulty,
                                10, generationOffset, knowledgeContext, safeSourceIds), count, history);
                        return generated;
                    }
                    markAiUnavailable("Python 未返回可用题目");
                } else {
                    log.warn("题目智能体返回异常状态: {}", response.statusCode());
                    markAiUnavailable("Python 返回异常状态");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                markAiUnavailable("调用被中断");
            } catch (Exception exception) {
                log.warn("题目智能体调用失败，使用本地降级题目: {}", exception.getMessage());
                markAiUnavailable("调用超时或失败");
            }
        }
        List<GeneratedQuestion> generated = new ArrayList<>();
        appendUnique(generated, fallback(weekTopic, taskTitle, questionType, difficulty, 10, generationOffset,
                knowledgeContext, safeSourceIds), count, history);
        if (generated.isEmpty()) {
            log.warn("历史去重过滤了全部快速题，启用最终可用性兜底");
            appendUnique(generated, fallback(weekTopic, taskTitle, questionType, difficulty, count,
                    generationOffset + 10_000, knowledgeContext, safeSourceIds), count, List.of());
        }
        return generated;
    }

    public List<GeneratedQuestion> generate(String profileJson, String weekTopic, String taskTitle,
                                            String questionType, String difficulty, int count,
                                            String knowledgeContext, List<Long> sourceChunkIds,
                                            List<GeneratedQuestion> historicalQuestions) {
        List<GeneratedQuestion> history = historicalQuestions == null ? List.of() : historicalQuestions;
        return generate(profileJson, weekTopic, taskTitle, questionType, difficulty, count,
                knowledgeContext, sourceChunkIds, history, history.size());
    }

    public List<GeneratedQuestion> generate(String profileJson, String weekTopic, String taskTitle,
                                            String questionType, String difficulty, int count,
                                            String knowledgeContext, List<Long> sourceChunkIds) {
        return generate(profileJson, weekTopic, taskTitle, questionType, difficulty, count,
                knowledgeContext, sourceChunkIds, List.of());
    }

    /** 保留旧调用方式，避免其他模块升级期间发生不兼容。 */
    public List<GeneratedQuestion> generate(String profileJson, String weekTopic, String taskTitle,
                                            String questionType, String difficulty, int count) {
        return generate(profileJson, weekTopic, taskTitle, questionType, difficulty, count, "", List.of());
    }

    private void markAiUnavailable(String reason) {
        aiUnavailableUntil.set(System.currentTimeMillis() + AI_COOLDOWN_MILLIS);
        log.warn("题目 AI 暂时不可用，未来 {} 秒直接使用快速本地生成: {}",
                AI_COOLDOWN_MILLIS / 1000, reason);
    }

    private List<GeneratedQuestion> parseAndValidate(String responseBody, String type,
                                                     List<Long> allowedSourceIds, int limit,
                                                     List<GeneratedQuestion> history) throws Exception {
        Map<String, Object> result = objectMapper.readValue(
                responseBody, new TypeReference<Map<String, Object>>() {});
        List<Map<String, Object>> raw = objectMapper.convertValue(
                result.getOrDefault("questions", List.of()), new TypeReference<List<Map<String, Object>>>() {});
        List<GeneratedQuestion> generated = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            GeneratedQuestion question = normalize(item, type, allowedSourceIds);
            if (question == null || generated.stream()
                    .anyMatch(existing -> isDuplicateQuestion(existing, question))
                    || history.stream().anyMatch(existing -> isDuplicateQuestion(existing, question))) continue;
            generated.add(question);
            if (generated.size() >= limit) break;
        }
        return generated;
    }

    private GeneratedQuestion normalize(Map<String, Object> item, String type, List<Long> allowedSourceIds) {
        String text = stringValue(item.get("question"));
        String answer = normalizedAnswer(stringValue(item.get("correctAnswer")));
        String explanation = stringValue(item.get("explanation"));
        if (text.length() < 6 || answer.isBlank() || explanation.length() < 6
                || containsOpaqueSourceReference(text)) return null;

        List<String> options;
        try {
            options = objectMapper.convertValue(item.getOrDefault("options", List.of()),
                    new TypeReference<List<String>>() {}).stream().map(this::stringValue)
                    .filter(value -> !value.isBlank()).toList();
        } catch (Exception exception) {
            return null;
        }

        if ("SHORT_ANSWER".equals(type)) {
            options = List.of();
            List<String> keywords = java.util.Arrays.stream(answer.split("[;；,，、\\s]+"))
                    .map(String::trim).filter(value -> value.length() >= 2).distinct().toList();
            if (keywords.size() < 3 || keywords.size() > 5) return null;
            answer = String.join("；", keywords);
        } else if ("TRUE_FALSE".equals(type)) {
            if (!Set.of("A", "B").contains(answer)) return null;
            options = List.of("正确", "错误");
        } else {
            if (!OBJECTIVE_TYPES.contains(type) || options.size() != 4
                    || options.stream().map(this::normalizedStem).distinct().count() != 4
                    || hasQuestionOptionConflict(text, options)
                    || options.stream().anyMatch(this::containsOpaqueSourceReference)) return null;
            List<String> letters = List.of(answer.split(","));
            if (letters.stream().anyMatch(letter -> !Set.of("A", "B", "C", "D").contains(letter))) return null;
            if ("SINGLE_CHOICE".equals(type) && letters.size() != 1) return null;
            if ("MULTIPLE_CHOICE".equals(type) && letters.size() < 2) return null;
        }

        List<Long> sourceIds = List.of();
        try {
            List<Long> proposed = objectMapper.convertValue(item.getOrDefault("sourceChunkIds", List.of()),
                    new TypeReference<List<Long>>() {});
            if (!proposed.isEmpty()) {
                Set<Long> allowed = Set.copyOf(allowedSourceIds);
                sourceIds = proposed.stream().filter(allowed::contains).distinct().toList();
            }
        } catch (Exception ignored) {
            // 使用检索阶段提供的来源，禁止模型伪造知识块 ID。
        }
        if (!allowedSourceIds.isEmpty() && sourceIds.isEmpty()) return null;
        String knowledgePoint = stringValue(item.get("knowledgePoint"));
        String learningObjective = stringValue(item.get("learningObjective"));
        String cognitiveLevel = stringValue(item.get("cognitiveLevel"));
        if (knowledgePoint.isBlank() || learningObjective.isBlank() || cognitiveLevel.isBlank()) return null;
        int qualityScore = Math.max(0, Math.min(100, integerValue(item.get("qualityScore"), 70)));
        return new GeneratedQuestion(text, options, answer, explanation,
                knowledgePoint, learningObjective, cognitiveLevel, sourceIds, qualityScore);
    }

    private List<GeneratedQuestion> fallback(String weekTopic, String taskTitle, String type,
                                             String difficulty, int count, int offset, String knowledgeContext,
                                             List<Long> sourceChunkIds) {
        String topic = blankToDefault(weekTopic, "本周主题");
        String task = blankToDefault(taskTitle, "当前任务");
        String evidence = compactEvidence(knowledgeContext);
        String level = switch (difficulty) {
            case "EASY" -> "理解";
            case "HARD" -> "评价与迁移";
            default -> "应用与分析";
        };
        List<GeneratedQuestion> result = new ArrayList<>(specializedFallback(
                topic, task, type, level, sourceChunkIds, offset, count));
        for (int position = result.size() + 1; position <= count; position++) {
            int index = offset + position;
            String[] focuses = {"概念理解", "实践验证", "边界条件", "错误诊断", "方案比较",
                    "过程记录", "结果验收", "知识迁移", "复盘改进", "后续规划"};
            String[] stages = {"基础核对", "条件辨析", "过程追踪", "反例验证", "综合迁移"};
            String focus = stages[((index - 1) / focuses.length) % stages.length]
                    + "中的" + focuses[(index - 1) % focuses.length];
            if ("TRUE_FALSE".equals(type)) {
                boolean correct = index % 2 == 1;
                String statement = correct
                        ? "判断：从“" + focus + "”角度学习“" + task + "”时，应结合核心概念与实际验证确认掌握情况。"
                        : "判断：从“" + focus + "”角度学习“" + task + "”时，只需记忆结论，无需解释或验证。";
                result.add(question(statement, List.of("正确", "错误"), correct ? "A" : "B",
                        "可靠的掌握证据应同时包含概念理解、解释能力和实践验证。" + evidence,
                        task, "判断学习方法是否能形成可靠掌握证据", level, sourceChunkIds, 62));
            } else if ("SHORT_ANSWER".equals(type)) {
                String prompt = "结合“" + topic + "”，从“" + focus + "”角度说明“" + task
                        + "”的核心概念、一个实践步骤与验收标准。";
                result.add(question(prompt, List.of(), "核心概念；实践步骤；验收标准",
                        "回答需完整覆盖概念、可执行步骤和可检验的完成标准。" + evidence,
                        task, "解释并应用当前任务的核心知识", level, sourceChunkIds, 60));
            } else {
                boolean multiple = "MULTIPLE_CHOICE".equals(type);
                List<String> options = fallbackOptions(topic, task, index, multiple, focus);
                result.add(question(
                        "围绕“" + task + "”的" + focus + "，以下" + (multiple ? "哪些" : "哪项")
                                + "做法能够形成有效的学习证据？",
                        options, multiple ? "A,C" : "A",
                        "有效学习证据应围绕“" + topic + "”体现概念理解与实践验证。" + evidence,
                        task, "识别能证明任务达成的有效证据", level, sourceChunkIds, 60));
            }
        }
        return result;
    }

    /** 在模型不可用时，数据结构与计组仍优先返回可计算的专业题，而不是元学习方法题。 */
    private List<GeneratedQuestion> specializedFallback(String topic, String task, String type,
                                                        String level, List<Long> sourceChunkIds,
                                                        int offset, int limit) {
        if (offset >= 10 || limit <= 0) return List.of();
        String text = (topic + " " + task).toLowerCase(Locale.ROOT);
        List<GeneratedQuestion> result = new ArrayList<>();
        boolean dataStructure = containsAny(text, "数据结构", "链表", "队列", "栈", "二叉树", "图", "排序", "哈希", "查找", "堆");
        boolean organization = containsAny(text, "计算机组成", "组成原理", "补码", "cache", "指令", "流水线", "控制器", "dma", "中断", "运算器");
        if (dataStructure) {
            addDataStructureFallback(result, text, type, level, sourceChunkIds);
        } else if (organization) {
            addOrganizationFallback(result, text, type, level, sourceChunkIds);
        }
        return result.subList(0, Math.min(limit, result.size()));
    }

    private void addDataStructureFallback(List<GeneratedQuestion> target, String context, String type,
                                          String level, List<Long> sources) {
        String point = containsAny(context, "树", "二叉树", "堆") ? "树与二叉树"
                : containsAny(context, "图", "最短路径", "拓扑") ? "图"
                : containsAny(context, "排序", "快排", "归并") ? "排序"
                : containsAny(context, "栈", "队列") ? "栈与队列" : "线性表与链表";
        if ("TRUE_FALSE".equals(type)) {
            target.add(question("在单链表中，如果已经持有待插入位置前驱结点的引用，则插入一个新结点的核心指针修改可在 O(1) 时间内完成。",
                    List.of("正确", "错误"), "A",
                    "已知前驱后无需从头查找，只需修改常数个 next 引用，因此核心插入操作为 O(1)；若还要先定位前驱，定位过程可能是 O(n)。",
                    point, "区分定位成本与链表指针修改成本", level, sources, 78));
            target.add(question("对含 n 个元素的顺序表，在下标 0 处插入新元素时，最坏情况下不需要移动原有元素。",
                    List.of("正确", "错误"), "B",
                    "在表头插入要把原有 n 个元素整体后移一个位置，因此移动次数为 n，时间复杂度为 O(n)。",
                    point, "计算顺序表边界位置插入的移动代价", level, sources, 78));
        } else if ("SHORT_ANSWER".equals(type)) {
            target.add(question("设 p 指向非空单链表中的结点，要把新结点 s 插入到 p 之后。写出两条关键赋值语句，并说明为什么赋值顺序不能交换。",
                    List.of(), "s.next=p.next；p.next=s；避免丢失后继链",
                    "应先令 s.next 指向 p 原来的后继，再令 p.next 指向 s。若先覆盖 p.next，就会失去原后继结点的引用，造成后续链断开。",
                    point, "正确完成单链表结点插入并解释指针更新不变量", level, sources, 80));
            target.add(question("比较顺序表与单链表在“按下标随机访问”和“已知前驱结点后插入”两种操作上的时间复杂度。",
                    List.of(), "顺序表随机访问O(1)；链表随机访问O(n)；顺序表插入O(n)；链表插入O(1)",
                    "顺序表地址连续，可按下标 O(1) 定位，但插入通常需要移动元素；单链表必须沿 next 访问下标，而已知前驱后的指针修改是常数次。",
                    point, "依据操作类型比较线性结构表示的复杂度", level, sources, 82));
        } else if ("MULTIPLE_CHOICE".equals(type)) {
            target.add(question("关于单链表结点插入与删除，下列哪些说法在题设条件下正确？",
                    List.of("已知前驱结点时，在其后插入新结点只需修改常数个引用", "删除某结点的直接后继前必须先判断该后继是否存在", "仅持有头结点时可在 O(1) 时间访问任意下标", "删除结点后无需处理任何被移除结点的引用"),
                    "A,B", "A 的指针修改次数为常数；B 是避免空引用的必要边界检查。按下标访问仍需遍历，删除后也应按语言和实现要求断开或释放结点。",
                    point, "识别链表操作的复杂度与边界条件", level, sources, 82));
        } else {
            target.add(question("长度为 8 的顺序表采用 0 起始下标。若在下标 3 处插入一个新元素，使原下标 3 及其后的元素右移，需要移动多少个原有元素？",
                    List.of("3", "4", "5", "8"), "C",
                    "原下标 3、4、5、6、7 的 5 个元素都要右移，因此移动次数为 8-3=5。",
                    point, "根据插入位置计算顺序表元素移动次数", level, sources, 84));
            target.add(question("p 指向单链表结点 A，A 的后继为 B。要把新结点 X 插入到 A 与 B 之间，哪组赋值顺序正确？",
                    List.of("p.next=X；X.next=p.next", "X.next=p.next；p.next=X", "X.next=p；p.next=X", "p=X；X.next=p.next"), "B",
                    "先保存原后继关系 X.next=p.next，使 X 指向 B；再执行 p.next=X，使 A 指向 X。反向执行会让 X.next 指向自身。",
                    point, "跟踪链表插入时的引用变化", level, sources, 86));
        }
    }

    private void addOrganizationFallback(List<GeneratedQuestion> target, String context, String type,
                                         String level, List<Long> sources) {
        String point = containsAny(context, "cache", "存储", "主存") ? "Cache 与存储层次"
                : containsAny(context, "流水线", "cpi", "数据通路") ? "CPU 数据通路与流水线"
                : containsAny(context, "中断", "dma", "输入输出") ? "输入输出系统"
                : "数据表示与运算";
        if ("TRUE_FALSE".equals(type)) {
            target.add(question("在 8 位二进制补码表示中，数值范围是 -128 到 127。",
                    List.of("正确", "错误"), "A",
                    "n 位补码的范围为 -2^(n-1) 到 2^(n-1)-1；代入 n=8 得 -128 到 127。",
                    point, "判断给定字长补码的表示范围", level, sources, 82));
            target.add(question("在直接映射 Cache 中，一个主存块可以放入任意一个 Cache 行。",
                    List.of("正确", "错误"), "B",
                    "直接映射由主存块号对 Cache 行数取模确定唯一行；可以放入任意行的是全相联映射的特征。",
                    point, "区分直接映射与全相联映射", level, sources, 82));
        } else if ("SHORT_ANSWER".equals(type)) {
            target.add(question("某直接映射 Cache 有 16 行，每块 16 B，主存按字节编址。说明 32 位地址中的块内偏移位数和行索引位数如何确定。",
                    List.of(), "块内偏移4位；行索引4位；其余为标记位",
                    "每块 16 B，需要 log2(16)=4 位选择块内字节；16 行需要 log2(16)=4 位选择 Cache 行；剩余 24 位为标记。",
                    point, "拆分直接映射 Cache 的地址字段", level, sources, 86));
            target.add(question("说明 CPU 执行时间公式中指令数、CPI 和时钟周期三者的关系，并指出只提高主频为什么不一定等比例提升性能。",
                    List.of(), "执行时间=指令数×CPI×时钟周期；主频与时钟周期互为倒数；CPI可能变化",
                    "执行时间由三项乘积共同决定。提高主频会缩短时钟周期，但若同时引入更深流水线、更多停顿或改变 CPI，整体加速不会只由频率决定。",
                    point, "使用 CPU 性能公式分析体系结构权衡", level, sources, 86));
        } else if ("MULTIPLE_CHOICE".equals(type)) {
            target.add(question("关于直接映射 Cache 的地址划分，下列哪些说法正确？",
                    List.of("块内偏移由每块包含的字节数决定", "行索引由 Cache 行数决定", "标记字段用于区分映射到同一行的不同主存块", "替换时必须在所有 Cache 行中执行全局 LRU"),
                    "A,B,C", "偏移、索引和标记分别完成块内定位、选行和身份校验；直接映射每块只有唯一候选行，不需要全局 LRU 选择。",
                    point, "解释直接映射 Cache 地址字段的作用", level, sources, 86));
        } else {
            target.add(question("采用 8 位补码表示时，十进制 -18 的机器数是哪一项？",
                    List.of("00010010", "11101101", "11101110", "10010010"), "C",
                    "+18 为 00010010，逐位取反得到 11101101，再加 1 得 11101110。",
                    point, "计算定长补码机器数", level, sources, 88));
            target.add(question("某直接映射 Cache 有 16 行，每块 16 B，主存按字节编址。地址 0x012C 的块内偏移是多少？",
                    List.of("0x0", "0xC", "0x12", "0x2C"), "B",
                    "块大小为 16 B，块内偏移由地址最低 4 位给出；0x012C 的最低十六进制位为 C，因此偏移为 0xC。",
                    point, "根据块大小计算 Cache 块内偏移", level, sources, 88));
        }
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private GeneratedQuestion question(String text, List<String> options, String answer, String explanation,
                                       String knowledgePoint, String learningObjective, String cognitiveLevel,
                                       List<Long> sourceIds, int qualityScore) {
        return new GeneratedQuestion(text, options, answer, explanation, knowledgePoint,
                learningObjective, cognitiveLevel, sourceIds, qualityScore);
    }

    private List<String> fallbackOptions(String topic, String task, int index, boolean multiple, String focus) {
        int variant = (index - 1) % 10;
        String[][] choices = {
                {"梳理“%s”的核心概念并用示例验证", "只背诵术语而不检查理解", "解释验证结果与预期之间的偏差", "脱离“%s”直接复制现成结论"},
                {"为“%s”设计一个最小可运行实践", "跳过实践并根据标题猜测结果", "补充正常输入之外的边界用例", "遇到错误时删除全部测试记录"},
                {"列出“%s”的适用条件并逐项核对", "默认所有场景都满足相同前提", "构造一个反例检查结论的边界", "用无关课程的结论代替当前分析"},
                {"复现“%s”中的问题并记录触发步骤", "未复现问题就随机修改多个位置", "修复后执行针对性的回归验证", "只要程序启动就认定问题已解决"},
                {"比较完成“%s”的两种方案及其代价", "只选择名称更熟悉的方案", "根据“%s”的约束解释最终取舍", "忽略约束并宣称所有方案等价"},
                {"记录“%s”的输入、步骤和可观察输出", "只保留最终结论且删除过程信息", "把关键决策与对应依据关联起来", "用模糊感受替代可检查的记录"},
                {"依据明确标准验收“%s”的完成结果", "没有标准时直接标记为已掌握", "保留能复核结果的输出或测试证据", "只依据完成耗时判断答案正确"},
                {"用新的小场景重新应用“%s”的核心方法", "只重复原例中的文字而不改变条件", "说明迁移后哪些条件保持或发生变化", "忽略新场景与原任务的差异"},
                {"根据错误记录定位“%s”的薄弱环节", "把所有错误归因于题目本身", "针对薄弱点安排一次小范围再练习", "重复阅读答案但不再次作答"},
                {"结合本次结果制定“%s”的下一项可检验目标", "使用无法判断是否完成的宽泛目标", "为下一次练习设定具体输入和验收条件", "同时堆叠大量无关任务以追求数量"}
        };
        String[] selected = choices[variant];
        String correctThird = selected[2].formatted(task);
        String third = multiple ? correctThird : switch (variant) {
            case 0 -> "只抄写验证结果而不分析产生偏差的原因";
            case 1 -> "只测试一次正常输入并忽略所有边界情况";
            case 2 -> "发现反例后仍坚持原结论适用于全部场景";
            case 3 -> "修复后不运行任何测试就直接提交结果";
            case 4 -> "不说明依据便选择看起来步骤最少的方案";
            case 5 -> "记录大量过程但不标明关键决策的依据";
            case 6 -> "只保存截图而不核对截图是否满足验收标准";
            case 7 -> "更换场景名称但保持原答案且不检查条件";
            case 8 -> "只统计错误数量而不定位对应的薄弱知识点";
            default -> "制定很多目标但不给出任何验收条件";
        };
        String prefix = "围绕“" + focus + "”，";
        return List.of(
                prefix + selected[0].formatted(task),
                prefix + selected[1],
                prefix + third,
                prefix + selected[3].formatted(topic));
    }

    private void appendUnique(List<GeneratedQuestion> target, List<GeneratedQuestion> additions, int limit,
                              List<GeneratedQuestion> blocked) {
        for (GeneratedQuestion item : additions) {
            boolean duplicate = target.stream().anyMatch(existing -> isDuplicateQuestion(existing, item))
                    || blocked.stream().anyMatch(existing -> isDuplicateQuestion(existing, item));
            if (!duplicate) target.add(item);
            if (target.size() >= limit) return;
        }
    }

    boolean hasQuestionOptionConflict(String question, List<String> options) {
        String questionKey = normalizedStem(question);
        for (String option : options) {
            String optionKey = normalizedStem(option);
            if (optionKey.length() < 4 || questionKey.isBlank()) continue;
            double lengthRatio = (double) Math.min(questionKey.length(), optionKey.length())
                    / Math.max(questionKey.length(), optionKey.length());
            if (questionKey.equals(optionKey)
                    || (lengthRatio >= 0.65 && diceSimilarity(questionKey, optionKey) >= 0.86)
                    || (lengthRatio >= 0.78
                    && (questionKey.contains(optionKey) || optionKey.contains(questionKey)))) return true;
        }
        return false;
    }

    boolean containsOpaqueSourceReference(String text) {
        if (text == null || text.isBlank()) return false;
        String compact = text.replaceAll("\\s+", "");
        return compact.matches(".*(?:资料|材料|文档|知识片段)(?:编号)?[一二三四五六七八九十0-9]+.*")
                || compact.matches(".*(?:上述|下列|给定|前述)(?:资料|材料|文档|知识片段).*");
    }

    boolean isDuplicateQuestion(GeneratedQuestion left, GeneratedQuestion right) {
        String leftStem = normalizedStem(left.question());
        String rightStem = normalizedStem(right.question());
        if (leftStem.equals(rightStem) || diceSimilarity(leftStem, rightStem) >= 0.88) return true;
        Set<String> leftOptions = normalizedOptionSet(left.options());
        Set<String> rightOptions = normalizedOptionSet(right.options());
        if (leftOptions.size() < 3 || rightOptions.size() < 3) return false;
        long overlap = leftOptions.stream().filter(rightOptions::contains).count();
        return leftOptions.equals(rightOptions)
                || (double) overlap / Math.min(leftOptions.size(), rightOptions.size()) >= 0.75;
    }

    private Set<String> normalizedOptionSet(List<String> options) {
        if (options == null) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        options.stream().map(this::normalizedStem).filter(value -> !value.isBlank()).forEach(result::add);
        return result;
    }

    private double diceSimilarity(String left, String right) {
        if (left.equals(right)) return 1.0;
        if (left.length() < 2 || right.length() < 2) return 0.0;
        Set<String> leftPairs = new LinkedHashSet<>();
        Set<String> rightPairs = new LinkedHashSet<>();
        for (int index = 0; index < left.length() - 1; index++) leftPairs.add(left.substring(index, index + 2));
        for (int index = 0; index < right.length() - 1; index++) rightPairs.add(right.substring(index, index + 2));
        long overlap = leftPairs.stream().filter(rightPairs::contains).count();
        return (2.0 * overlap) / (leftPairs.size() + rightPairs.size());
    }

    private String normalizedStem(String text) {
        return NON_SEMANTIC.matcher(text.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private String normalizedAnswer(String answer) {
        return java.util.Arrays.stream(answer.toUpperCase(Locale.ROOT).split("[,，、\\s]+"))
                .map(String::trim).filter(value -> !value.isBlank()).distinct().sorted()
                .reduce((left, right) -> left + "," + right).orElse("");
    }

    private String compactEvidence(String context) {
        if (context == null || context.isBlank()) return "";
        String compact = context.replaceAll("\\[[^\\]]*chunkId[^\\]]*]", " ")
                .replaceAll("\\s+", " ").trim();
        if (compact.length() > 100) compact = compact.substring(0, 100) + "…";
        return " 参考依据：" + compact;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int integerValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(stringValue(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public record GeneratedQuestion(
            String question,
            List<String> options,
            String correctAnswer,
            String explanation,
            String knowledgePoint,
            String learningObjective,
            String cognitiveLevel,
            List<Long> sourceChunkIds,
            Integer qualityScore) {}
}
