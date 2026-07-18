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
import java.util.List;
import java.util.Map;

/** 调用 Python 题目智能体，并在服务不可用时提供可作答的任务相关降级题目。 */
@Service
public class QuestionAiService {
    private static final Logger log = LoggerFactory.getLogger(QuestionAiService.class);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private final boolean mockMode;

    @Value("${python.ai.url:http://localhost:8000}")
    private String pythonAiUrl;

    public QuestionAiService(ObjectMapper objectMapper,
                             @Value("${ai.mock-enabled:false}") boolean mockMode) {
        this.objectMapper = objectMapper;
        this.mockMode = mockMode;
    }

    public List<GeneratedQuestion> generate(String profileJson, String weekTopic, String taskTitle,
                                            String questionType, String difficulty, int count) {
        if (!mockMode) {
            try {
                String body = objectMapper.writeValueAsString(Map.of(
                        "profile_json", profileJson == null ? "" : profileJson,
                        "week_topic", weekTopic,
                        "task_title", taskTitle,
                        "question_type", questionType,
                        "difficulty", difficulty,
                        "count", count));
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(pythonAiUrl + "/questions/generate"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(75))
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    Map<String, Object> result = objectMapper.readValue(
                            response.body(), new TypeReference<Map<String, Object>>() {});
                    List<Map<String, Object>> raw = objectMapper.convertValue(
                            result.getOrDefault("questions", List.of()), new TypeReference<List<Map<String, Object>>>() {});
                    List<GeneratedQuestion> generated = new ArrayList<>();
                    for (Map<String, Object> item : raw) {
                        String text = String.valueOf(item.getOrDefault("question", "")).trim();
                        String answer = String.valueOf(item.getOrDefault("correctAnswer", "")).trim();
                        if (text.isBlank() || answer.isBlank()) continue;
                        List<String> options = objectMapper.convertValue(
                                item.getOrDefault("options", List.of()), new TypeReference<List<String>>() {});
                        generated.add(new GeneratedQuestion(text, options, answer,
                                String.valueOf(item.getOrDefault("explanation", ""))));
                    }
                    if (!generated.isEmpty()) return generated.subList(0, Math.min(count, generated.size()));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("题目智能体调用失败，使用本地降级题目: {}", e.getMessage());
            }
        }
        return fallback(weekTopic, taskTitle, questionType, difficulty, count);
    }

    private List<GeneratedQuestion> fallback(String weekTopic, String taskTitle,
                                             String type, String difficulty, int count) {
        List<GeneratedQuestion> result = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            if ("TRUE_FALSE".equals(type)) {
                String statement = switch ((index - 1) % 3) {
                    case 1 -> "判断：学习“" + taskTitle + "”时，只要记住术语就能证明已经掌握。";
                    case 2 -> "判断：可以通过解释概念并完成一个小实践来检验“" + taskTitle + "”的掌握情况。";
                    default -> "判断：完成“" + taskTitle + "”时，应先理解核心概念，再通过练习验证理解。";
                };
                String answer = (index - 1) % 3 == 1 ? "B" : "A";
                result.add(new GeneratedQuestion(
                        statement, List.of("正确", "错误"), answer,
                        "概念理解、主动解释与实践验证共同构成完成学习任务的可靠证据。"));
            } else if ("SHORT_ANSWER".equals(type)) {
                String prompt = switch ((index - 1) % 3) {
                    case 1 -> "请为“" + taskTitle + "”设计一个可在 30 分钟内完成的实践步骤，并写出验收标准。";
                    case 2 -> "学习“" + taskTitle + "”时最可能遇到什么误区？请给出识别和修正方法。";
                    default -> "请结合“" + weekTopic + "”，用自己的话说明“" + taskTitle + "”的核心目标与一个实践步骤。";
                };
                result.add(new GeneratedQuestion(
                        prompt,
                        List.of(), "核心目标；关键概念；实践步骤",
                        "回答应同时覆盖学习目标、关键概念和可执行的实践步骤。"));
            } else {
                boolean multiple = "MULTIPLE_CHOICE".equals(type);
                List<String> options = multiple
                        ? List.of("梳理任务涉及的核心概念", "只复制现成答案", "完成小练习并解释结果", "脱离本周主题学习")
                        : List.of("先梳理核心概念，再完成针对性实践", "只记住术语而不验证理解", "跳过基础直接复制最终答案", "完全脱离本周主题进行练习");
                String answer = multiple ? "A,C" : "A";
                String stem = switch ((index - 1) % 3) {
                    case 1 -> "完成“" + taskTitle + "”后，哪种证据最能说明你真正理解了这个任务？";
                    case 2 -> "学习“" + taskTitle + "”时，哪种做法最有助于发现并修正理解偏差？";
                    default -> "关于“" + taskTitle + "”，下列哪项最符合本周“" + weekTopic + "”的学习要求？";
                };
                result.add(new GeneratedQuestion(
                        stem,
                        options, answer, "学习任务需要围绕本周主题形成概念理解和实践验证。"));
            }
        }
        return result;
    }

    public record GeneratedQuestion(String question, List<String> options,
                                    String correctAnswer, String explanation) {}
}
