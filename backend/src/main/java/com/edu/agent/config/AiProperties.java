package com.edu.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 配置属性类 —— 从 application.yml 读取 OpenAI 兼容接口的配置
 *
 * 通过 @ConfigurationProperties(prefix = "ai") 自动绑定 application.yml 中 ai.* 配置项。
 * 用于配置调用 AI 大模型（如 GPT-4o、DeepSeek、通义千问等）所需的连接信息。
 *
 * ========== 配置项说明（application.yml） ==========
 *   ai.api-key:           API 密钥（必填，否则自动降级到 Mock 评价模式）
 *   ai.base-url:          API 基础地址（默认 https://api.openai.com/v1）
 *   ai.model:             模型名称（默认 gpt-4o）
 *   ai.timeout-seconds:   请求超时秒数（默认 60）
 *
 * ========== 调用入口 ==========
 *   - SubmissionService 通过构造器注入 AiProperties
 *   - callAiApi() 方法使用 aiProperties.getApiKey() / getBaseUrl() / getModel() / getTimeoutSeconds()
 *   - 未配置 API Key 时自动降级到 mockEvaluation()，不调用真实 API
 *
 * ========== 环境变量覆盖 ==========
 *   所有配置均支持通过环境变量覆盖（application.yml 中已配置 ${AI_API_KEY} 等占位符）：
 *     export AI_API_KEY=sk-your-key-here
 *     export AI_BASE_URL=https://api.deepseek.com/v1
 *     export AI_MODEL=deepseek-chat
 *     export AI_TIMEOUT=120
 */
@Component  // 【Spring Boot】声明为 Spring Bean，由组件扫描自动注册
@ConfigurationProperties(prefix = "ai")  // 【Spring Boot】绑定 application.yml 中 ai.* 配置
public class AiProperties {

    /** API 密钥（必填），如 sk-xxxxxxxxxxxx，未配置时使用 Mock 降级 */
    private String apiKey;

    /** API 基础地址，默认 OpenAI，可改为 DeepSeek / 通义千问 / 本地模型等兼容接口 */
    private String baseUrl = "https://api.openai.com/v1";

    /** 模型名称，默认 gpt-4o，可根据需要改为 deepseek-chat / qwen-plus 等 */
    private String model = "gpt-4o";

    /** 请求超时秒数，AI 响应较慢时可适当调大（如 120 秒） */
    private int timeoutSeconds = 60;

    // ===== Getters & Setters =====

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}