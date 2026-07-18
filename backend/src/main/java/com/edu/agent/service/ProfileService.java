package com.edu.agent.service;

import com.edu.agent.model.UserProfile;
import com.edu.agent.repository.UserProfileRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用户画像服务 —— 管理 6 维学习画像的存取与更新
 *
 * 核心职责：
 *   1. 从 MySQL 读取最新画像记录，将 JSON 反序列化为 transient 字段
 *   2. 每次对话后将提取的画像更新存入 MySQL（保留历史版本）
 *   3. 首次使用时自动创建默认画像
 *
 * 画像 JSON 格式示例：
 *   {
 *     "knowledgeBase": 7,
 *     "cognitiveStyle": "kinesthetic",
 *     "weaknessPoints": ["并发编程"],
 *     "learningPace": 5,
 *     "interestAreas": ["编程"],
 *     "shortTermGoal": "成为全栈工程师"
 *   }
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Service: 声明为业务服务层 Bean，由 Spring 组件扫描自动注册
 *   - UserProfileRepository: Spring Data JPA 在运行时动态生成的代理实现，
 *     继承 JpaRepository 后自动获得 findAll()、save() 等 CRUD 方法
 *   - 构造器注入: Spring 自动装配 UserProfileRepository
 */
@Service  // 【Spring Boot】声明为业务服务 Bean
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    /** 【Spring Boot/Data JPA】Spring 运行时动态生成的 JPA 仓库代理 */
    private final UserProfileRepository profileRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 【Spring Boot】构造器注入 —— Spring 自动装配 JPA 仓库代理 */
    public ProfileService(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * 获取指定用户的画像
     * 取该用户数据库中最新的画像记录；若无记录则创建默认画像
     *
     * @param userId 用户 ID
     */
    public UserProfile getCurrentProfile(Long userId, String conversationId) {
        var opt = profileRepository.findFirstByUserIdAndConversationIdOrderByUpdatedAtDescIdDesc(
                userId, conversationId);
        if (opt.isPresent()) {
            UserProfile profile = opt.get();
            loadTransientFields(profile);
            return profile;
        }
        return createDefaultProfile(userId, conversationId);
    }

    /**
     * 从 JSON 字符串解析并创建 UserProfile 对象（含 transient 字段填充）
     * 用于 Python AI 服务返回的 profile_json 反序列化
     */
    public UserProfile parseProfileJson(String profileJson) {
        UserProfile profile = new UserProfile();
        profile.setProfileJson(profileJson);
        loadTransientFields(profile);
        return profile;
    }

    /**
     * 更新画像 —— 将新的画像数据持久化到 MySQL
     *
     * 每次更新创建一条新记录（而非覆盖），方便追溯画像变化历史。
     * profileJson 字段存储完整 JSON，transient 字段仅用于 Java 内存中的便捷访问。
     *
     * @param extracted 提取的画像数据
     * @param userId    所属用户 ID
     */
    public UserProfile updateProfile(UserProfile extracted, Long userId, String conversationId) {
        UserProfile saved = new UserProfile();
        saved.setUserId(userId);
        saved.setConversationId(conversationId);
        saved.setProfileJson(extracted.getProfileJson());
        saved.setUpdatedAt(LocalDateTime.now());

        // 复制 transient 字段值（确保返回的对象中这些字段可用）
        copyTransientFields(extracted, saved);

        saved = profileRepository.save(saved);  // 【Spring Boot/Data JPA】自动生成的保存方法
        log.info(">>> 对话画像已更新并存入 MySQL。id={}, userId={}, conversationId={}",
                saved.getId(), userId, conversationId);
        return saved;
    }

    /**
     * 只允许有足够长期证据支持的字段发生变化，避免模型在闲聊中漂移画像。
     */
    public UserProfile updateProfileWithEvidence(UserProfile extracted,
                                                 Long userId,
                                                 String conversationId,
                                                 String evidenceJson) {
        java.util.Set<String> supported = new java.util.HashSet<>();
        try {
            List<Map<String, Object>> items = objectMapper.readValue(
                    evidenceJson == null ? "[]" : evidenceJson,
                    new TypeReference<List<Map<String, Object>>>() {});
            for (Map<String, Object> item : items) {
                String scope = String.valueOf(item.getOrDefault("scope", "LONG_TERM"));
                Object rawConfidence = item.get("confidence");
                double confidence = rawConfidence instanceof Number number
                        ? number.doubleValue() : Double.parseDouble(String.valueOf(rawConfidence));
                if ("LONG_TERM".equalsIgnoreCase(scope) && confidence >= 0.60) {
                    supported.add(String.valueOf(item.get("dimension")));
                }
            }
        } catch (Exception e) {
            log.warn("画像证据解析失败，本轮保留原画像: {}", e.getMessage());
        }

        return updateProfileWithSupportedDimensions(extracted, userId, conversationId, supported);
    }

    public UserProfile updateProfileWithSupportedDimensions(UserProfile extracted,
                                                            Long userId,
                                                            String conversationId,
                                                            java.util.Set<String> supported) {
        UserProfile current = getCurrentProfile(userId, conversationId);
        if (supported == null || supported.isEmpty()) return current;
        UserProfile reconciled = new UserProfile();
        reconciled.setKnowledgeBase(supported.contains("knowledgeBase")
                ? extracted.getKnowledgeBase() : current.getKnowledgeBase());
        reconciled.setCognitiveStyle(supported.contains("cognitiveStyle")
                ? extracted.getCognitiveStyle() : current.getCognitiveStyle());
        reconciled.setWeaknessPoints(supported.contains("weaknessPoints")
                ? extracted.getWeaknessPoints() : current.getWeaknessPoints());
        reconciled.setLearningPace(supported.contains("learningPace")
                ? extracted.getLearningPace() : current.getLearningPace());
        reconciled.setInterestAreas(supported.contains("interestAreas")
                ? extracted.getInterestAreas() : current.getInterestAreas());
        reconciled.setShortTermGoal(supported.contains("shortTermGoal")
                ? extracted.getShortTermGoal() : current.getShortTermGoal());
        try {
            reconciled.setProfileJson(objectMapper.writeValueAsString(Map.of(
                    "knowledgeBase", reconciled.getKnowledgeBase(),
                    "cognitiveStyle", reconciled.getCognitiveStyle(),
                    "weaknessPoints", reconciled.getWeaknessPoints(),
                    "learningPace", reconciled.getLearningPace(),
                    "interestAreas", reconciled.getInterestAreas(),
                    "shortTermGoal", reconciled.getShortTermGoal()
            )));
        } catch (JsonProcessingException e) {
            reconciled.setProfileJson(current.getProfileJson());
        }
        return updateProfile(reconciled, userId, conversationId);
    }

    /** 将成果中有证据的薄弱点并入画像，供后续回答和计划修订使用。 */
    public UserProfile applySubmissionWeaknesses(Long userId,
                                                 String conversationId,
                                                 List<String> weaknesses) {
        UserProfile current = getCurrentProfile(userId, conversationId);
        if (weaknesses == null || weaknesses.isEmpty()) return current;
        java.util.LinkedHashSet<String> merged = new java.util.LinkedHashSet<>();
        if (current.getWeaknessPoints() != null) {
            current.getWeaknessPoints().stream()
                    .filter(item -> item != null && !item.isBlank() && !"待评估".equals(item))
                    .forEach(merged::add);
        }
        weaknesses.stream().filter(item -> item != null && !item.isBlank()).forEach(merged::add);
        UserProfile extracted = new UserProfile();
        copyTransientFields(current, extracted);
        extracted.setWeaknessPoints(merged.stream().limit(6).toList());
        return updateProfileWithSupportedDimensions(
                extracted, userId, conversationId, java.util.Set.of("weaknessPoints"));
    }

    /**
     * 创建默认画像 —— 用于用户首次使用时初始化
     * 所有维度设为中性值，等待后续对话逐步修正
     *
     * @param userId 所属用户 ID
     */
    public UserProfile createDefaultProfile(Long userId, String conversationId) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setConversationId(conversationId);
        profile.setKnowledgeBase(5);
        profile.setCognitiveStyle("visual");
        profile.setWeaknessPoints(List.of("待评估"));
        profile.setLearningPace(5);
        profile.setInterestAreas(List.of("待评估"));
        profile.setShortTermGoal("探索学习方向");
        profile.setUpdatedAt(LocalDateTime.now());

        try {
            profile.setProfileJson(objectMapper.writeValueAsString(Map.of(
                    "knowledgeBase", 5,
                    "cognitiveStyle", "visual",
                    "weaknessPoints", List.of("待评估"),
                    "learningPace", 5,
                    "interestAreas", List.of("待评估"),
                    "shortTermGoal", "探索学习方向"
            )));
        } catch (JsonProcessingException e) {
            profile.setProfileJson("{}");
        }

        return profileRepository.save(profile);  // 【Spring Boot/Data JPA】保存默认画像到 MySQL
    }

    /**
     * 删除指定用户的所有画像数据
     *
     * @param userId 用户 ID
     */
    public void deleteAllByUserId(Long userId) {
        profileRepository.deleteByUserId(userId);
        log.info(">>> 已删除用户 {} 的所有画像数据", userId);
    }

    /**
     * 从 MySQL JSON 字段中还原 transient 字段值
     * 使 Java 代码可以通过 getKnowledgeBase() 等方法直接访问画像数据
     */
    private void loadTransientFields(UserProfile profile) {
        String json = profile.getProfileJson();
        if (json == null || json.isBlank()) return;

        // 第一次尝试：直接解析 JSON
        Map<String, Object> map = tryParseJson(json);
        if (map == null) {
            // 第二次尝试：JSON 可能被双重编码（外层是字符串，内层是真正的 JSON）
            // 例如: ""{\\"knowledgeBase\\":7}"" → 先反序列化得到内层 JSON 字符串 → 再解析
            try {
                String innerJson = objectMapper.readValue(json, String.class);
                map = tryParseJson(innerJson);
                if (map != null) {
                    log.info("画像 JSON 经过双重解码后成功解析");
                }
            } catch (JsonProcessingException ignored) {
                // 双重解码也失败，放弃
            }
        }

        if (map == null) {
            log.warn("画像 JSON 解析失败（直接解析和双重解码均未成功），JSON 前 100 字符: {}",
                    json.substring(0, Math.min(100, json.length())));
            return;
        }

        profile.setKnowledgeBase(getInt(map, "knowledgeBase", 5));
        profile.setCognitiveStyle(getString(map, "cognitiveStyle", "visual"));
        profile.setWeaknessPoints(getStringList(map, "weaknessPoints"));
        profile.setLearningPace(getInt(map, "learningPace", 5));
        profile.setInterestAreas(getStringList(map, "interestAreas"));
        profile.setShortTermGoal(getString(map, "shortTermGoal", ""));
    }

    /**
     * 尝试将 JSON 字符串解析为 Map
     * @return 解析成功返回 Map，失败返回 null
     */
    private Map<String, Object> tryParseJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** 复制 transient 字段从一个对象到另一个 */
    private void copyTransientFields(UserProfile from, UserProfile to) {
        to.setKnowledgeBase(from.getKnowledgeBase());
        to.setCognitiveStyle(from.getCognitiveStyle());
        to.setWeaknessPoints(from.getWeaknessPoints());
        to.setLearningPace(from.getLearningPace());
        to.setInterestAreas(from.getInterestAreas());
        to.setShortTermGoal(from.getShortTermGoal());
    }

    // ========== JSON 解析工具方法 ==========

    /** 从 Map 中安全获取 int 值，若不存在或类型不对则返回默认值 */
    @SuppressWarnings("unchecked")
    private int getInt(java.util.Map<String, Object> map, String key, int defaultVal) {
        Object v = map.get(key);
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return defaultVal;
    }

    /** 从 Map 中安全获取字符串值 */
    private String getString(java.util.Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        return v != null ? v.toString() : defaultVal;
    }

    /** 从 Map 中安全获取字符串列表 */
    @SuppressWarnings("unchecked")
    private List<String> getStringList(java.util.Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}
