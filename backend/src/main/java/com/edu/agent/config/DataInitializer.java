package com.edu.agent.config;

import com.edu.agent.model.User;
import com.edu.agent.repository.UserRepository;
import com.edu.agent.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 数据初始化器 —— Spring Boot 启动时自动执行的初始化逻辑
 *
 * 实现 CommandLineRunner 接口，在 Spring IoC 容器初始化完成后自动执行 run() 方法。
 * 用于创建默认用户账号，确保系统首次运行时有可用的登录账号。
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Component: 声明为 Spring Bean，由组件扫描自动注册
 *   - CommandLineRunner: Spring Boot 提供的启动回调接口
 *     → Spring Boot 启动完成后自动调用 run() 方法
 *   - 构造器注入: UserRepository 由 Spring 自动装配
 */
@Component  // 【Spring Boot】声明为 Spring Bean
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /** 【Spring Boot/Data JPA】Spring 运行时动态生成的 JPA 仓库代理 */
    private final UserRepository userRepository;

    /** 【Spring Boot】构造器注入 —— Spring 自动装配 JPA 仓库代理 */
    public DataInitializer(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Spring Boot 启动完成后自动执行的初始化逻辑
     *
     * 检查默认用户 "viuuro" 是否已存在，不存在则创建。
     * 密码使用 SHA-256 哈希后存储（AuthService.hash()）。
     *
     * @param args 命令行参数（本方法未使用）
     */
    @Override  // 【Spring Boot】CommandLineRunner 接口的回调方法
    public void run(String... args) {
        // 检查默认用户是否已存在
        if (!userRepository.existsByUsername("viuuro")) {
            // 创建默认用户
            User user = new User();
            user.setUsername("viuuro");
            user.setPassword(AuthService.hash("529"));  // 密码哈希后存储
            userRepository.save(user);  // 【Spring Boot/Data JPA】保存到 MySQL
            log.info("默认用户 viuuro 已创建");
        }
    }
}
