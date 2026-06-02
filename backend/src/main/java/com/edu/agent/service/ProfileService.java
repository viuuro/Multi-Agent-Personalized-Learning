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
    public UserProfile getCurrentProfile(Long userId) {
        var opt = profileRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId);
        if (opt.isPresent()) {
            UserProfile profile = opt.get();
            loadTransientFields(profile);
            return profile;
        }
        return createDefaultProfile(userId);
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
    public UserProfile updateProfile(UserProfile extracted, Long userId) {
        UserProfile saved = new UserProfile();
        saved.setUserId(userId);
        saved.setProfileJson(extracted.getProfileJson());
        saved.setUpdatedAt(LocalDateTime.now());

        // 复制 transient 字段值（确保返回的对象中这些字段可用）
        copyTransientFields(extracted, saved);

        saved = profileRepository.save(saved);  // 【Spring Boot/Data JPA】自动生成的保存方法
        log.info(">>> 用户画像已更新并存入 MySQL。id={}, userId={}", saved.getId(), userId);
        return saved;
    }

    /**
     * 创建默认画像 —— 用于用户首次使用时初始化
     * 所有维度设为中性值，等待后续对话逐步修正
     *
     * @param userId 所属用户 ID
     */
    public UserProfile createDefaultProfile(Long userId) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
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
        try {
            var map = objectMapper.readValue(json, new TypeReference<java.util.Map<String, Object>>() {});
            profile.setKnowledgeBase(getInt(map, "knowledgeBase", 5));
            profile.setCognitiveStyle(getString(map, "cognitiveStyle", "visual"));
            profile.setWeaknessPoints(getStringList(map, "weaknessPoints"));
            profile.setLearningPace(getInt(map, "learningPace", 5));
            profile.setInterestAreas(getStringList(map, "interestAreas"));
            profile.setShortTermGoal(getString(map, "shortTermGoal", ""));
        } catch (JsonProcessingException e) {
            log.warn("画像 JSON 解析失败: {}", e.getMessage());
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
