package com.edu.agent.service;

import com.edu.agent.model.User;
import com.edu.agent.repository.UserRepository;
import com.edu.agent.repository.ConversationRepository;
import com.edu.agent.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 用户认证服务 —— 处理登录、注册、账号管理
 *
 * 核心职责：
 *   1. 用户登录：验证用户名+密码（SHA-256 哈希比对）
 *   2. 用户注册：创建新用户（密码哈希存储，不明文保存）
 *   3. 账号管理：修改用户名、密码、头像
 *
 * 安全设计：
 *   - 密码使用 SHA-256 哈希后存储，数据库中不保存明文密码
 *   - 登录时将用户输入的密码哈希后与数据库中的哈希值比对
 *   - 修改密码时需要验证原密码
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Service: 声明为业务服务层 Bean，由 Spring 组件扫描自动注册到 IoC 容器
 *   - UserRepository: Spring Data JPA 在运行时动态生成的代理实现
 *   - 构造器注入: Spring 自动装配 UserRepository
 */
@Service  // 【Spring Boot】声明为业务服务 Bean
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 【Spring Boot/Data JPA】Spring 运行时动态生成的 JPA 仓库代理 */
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final UserProfileRepository userProfileRepository;

    /** 【Spring Boot】构造器注入 —— Spring 自动装配 JPA 仓库代理 */
    public AuthService(UserRepository userRepository,
                       ConversationRepository conversationRepository,
                       UserProfileRepository userProfileRepository) {
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.userProfileRepository = userProfileRepository;
    }

    /**
     * 用户登录 —— 验证用户名和密码
     *
     * 流程：
     *   1. 根据用户名查找用户记录
     *   2. 将输入密码哈希后与数据库中的哈希值比对
     *   3. 匹配成功返回 User 对象，失败抛出异常
     *
     * @param username 用户名
     * @param password 明文密码（方法内部会哈希后比对）
     * @return 验证通过的 User 对象
     * @throws RuntimeException 用户不存在或密码错误
     */
    public User login(String username, String password) {
        // 【Spring Boot/Data JPA】findByUsername() 由 Spring Data JPA 方法命名规则自动生成
        // 自动生成 SQL: SELECT * FROM app_user WHERE username = ?
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            throw new RuntimeException("用户不存在");
        }
        User user = userOpt.get();
        // 将用户输入的密码哈希后与数据库中存储的哈希值比对
        if (!user.getPassword().equals(hash(password))) {
            throw new RuntimeException("密码错误");
        }
        log.info("用户 {} 登录成功", username);
        return user;
    }

    /**
     * 用户注册 —— 创建新用户
     *
     * 流程：
     *   1. 校验用户名和密码的合法性
     *   2. 检查用户名是否已存在
     *   3. 密码哈希后存储到数据库
     *
     * @param username 用户名（不能为空，不能重复）
     * @param password 明文密码（不能为空，存储前会哈希）
     * @return 创建成功的 User 对象
     * @throws RuntimeException 用户名为空、密码为空、用户名已存在
     */
    public User register(String username, String password) {
        // 参数校验
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (password == null || password.length() < 1) {
            throw new RuntimeException("密码不能为空");
        }
        // 【Spring Boot/Data JPA】existsByUsername() 由方法名自动生成
        // 自动生成 SQL: SELECT COUNT(*) > 0 FROM app_user WHERE username = ?
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("用户名已存在");
        }
        // 创建用户实体
        User user = new User();
        user.setUsername(username.trim());
        user.setPassword(hash(password));  // 密码哈希后存储
        // 【Spring Boot/Data JPA】save() 由 JpaRepository 自动提供
        // 自动生成 SQL: INSERT INTO app_user (username, password, ...) VALUES (?, ?, ...)
        User saved = userRepository.save(user);
        log.info("新用户 {} 注册成功", username);
        return saved;
    }

    /**
     * 更新用户资料 —— 修改用户名、密码、头像
     *
     * 流程：
     *   1. 根据 userId 查找用户
     *   2. 如果修改了用户名，检查新用户名是否已存在
     *   3. 如果修改了密码，验证原密码是否正确
     *   4. 更新头像（Base64 编码的图片数据）
     *   5. 保存更新后的用户记录
     *
     * @param userId          用户 ID
     * @param newUsername     新用户名（可选，null 表示不修改）
     * @param currentPassword 当前密码（修改密码时必须提供）
     * @param newPassword     新密码（可选，null 表示不修改）
     * @param avatar          新头像 Base64 数据（可选，null 表示不修改）
     * @return 更新后的 User 对象
     * @throws RuntimeException 用户不存在、用户名已存在、原密码错误
     */
    public User updateProfile(Long userId, String newUsername, String currentPassword, String newPassword, String avatar) {
        // 【Spring Boot/Data JPA】findById() 由 JpaRepository 自动提供
        // 自动生成 SQL: SELECT * FROM app_user WHERE id = ?
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 修改用户名（如果新用户名不为空且与当前用户名不同）
        if (newUsername != null && !newUsername.trim().isEmpty() && !newUsername.trim().equals(user.getUsername())) {
            if (userRepository.existsByUsername(newUsername.trim())) {
                throw new RuntimeException("用户名已存在");
            }
            user.setUsername(newUsername.trim());
        }
        // 修改密码（需要验证原密码）
        if (newPassword != null && !newPassword.isEmpty()) {
            if (currentPassword == null || !user.getPassword().equals(hash(currentPassword))) {
                throw new RuntimeException("原密码错误");
            }
            user.setPassword(hash(newPassword));
        }
        // 修改头像（Base64 编码的图片数据）
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        // 【Spring Boot/Data JPA】save() 既可用于新增也可用于更新
        // 当实体已有 ID 时，自动生成 SQL: UPDATE app_user SET ... WHERE id = ?
        return userRepository.save(user);
    }

    /**
     * SHA-256 哈希函数 —— 将输入文本转换为 64 位十六进制哈希值
     *
     * 用于密码安全存储：
     *   - 原始密码 "123456" → 哈希后 "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92"
     *   - 不可逆：无法从哈希值反推出原始密码
     *   - 相同输入始终产生相同输出（用于登录验证）
     *
     * @param input 待哈希的文本（通常是密码）
     * @return 64 位十六进制哈希字符串
     */
    public static String hash(String input) {
        try {
            // 获取 SHA-256 消息摘要实例
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // 计算哈希值（输入为 UTF-8 编码的字节数组）
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            // 将字节数组转换为十六进制字符串（Java 17+ 的 HexFormat 工具）
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 Java 标准算法，理论上不会抛出此异常
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    /**
     * 注销账号 —— 删除用户及其所有关联数据
     *
     * 流程：
     *   1. 验证用户存在
     *   2. 验证密码正确（防止误操作）
     *   3. 删除该用户的所有对话记录
     *   4. 删除该用户的所有画像数据
     *   5. 删除用户账户
     *
     * @param userId   用户 ID
     * @param password 用户密码（需验证后才执行删除）
     * @throws RuntimeException 用户不存在或密码错误
     */
    @Transactional
    public void deleteUser(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 验证密码，防止误删
        if (!user.getPassword().equals(hash(password))) {
            throw new RuntimeException("密码错误");
        }

        log.info(">>> 开始注销账号: userId={}, username={}", userId, user.getUsername());

        // 删除关联数据（按依赖顺序）
        conversationRepository.deleteByUserId(userId);
        log.info(">>> 已删除用户 {} 的对话记录", userId);

        userProfileRepository.deleteByUserId(userId);
        log.info(">>> 已删除用户 {} 的画像数据", userId);

        // 删除用户账户
        userRepository.delete(user);
        log.info(">>> 用户账号 {} 已注销", user.getUsername());
    }
}
