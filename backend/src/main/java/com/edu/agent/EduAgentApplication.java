package com.edu.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 个性化学习多智能体系统 —— 主启动类
 *
 * 架构说明：
 *   - Spring Boot 后端：REST API、用户认证、SSE 流式输出、MySQL 持久化
 *   - Python AI 服务：LangChain 智能体（画像提取、对话生成、计划生成、资源推荐）
 *   - Vue 3 前端：UI 展示、状态管理、SSE 接收
 *
 * 通信流程：
 *   前端 → Spring Boot (8080) → Python AI (8000) → MiMo-v2.5 API
 *
 * ========== 【Spring Boot】使用清单 ==========
 *   - @SpringBootApplication: Spring Boot 自动配置入口
 *   - SpringApplication.run(): 启动内嵌 Tomcat、初始化 IoC 容器、自动装配
 *   - spring-boot-starter-web: Web 层（REST 控制器 + SSE 支持）
 *   - spring-boot-starter-data-jpa: 数据库访问层（JPA + Hibernate）
 *   - application.yml: Spring Boot 外部化配置
 */
@SpringBootApplication  // 【Spring Boot】开启自动配置、组件扫描、属性绑定
public class EduAgentApplication {

    public static void main(String[] args) {
        // 【Spring Boot】启动内嵌 Web 服务器（Tomcat）、初始化 Spring IoC 容器
        SpringApplication.run(EduAgentApplication.class, args);
    }
}
