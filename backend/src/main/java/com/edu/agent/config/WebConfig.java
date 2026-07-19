package com.edu.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置类 —— 处理跨域请求（CORS）
 *
 * 前端开发服务器运行在 localhost:5173，后端在 localhost:8080，
 * 需要允许跨域访问。生产环境应缩小 allowedOriginPatterns 范围。
 *
 * ========== 【Spring Boot】在本类的使用 ==========
 *   - @Configuration: 声明为 Spring Boot 配置类
 *   - implements WebMvcConfigurer: Spring MVC 的扩展接口
 *   - addCorsMappings(): 覆盖 Spring MVC 默认的 CORS 策略
 *   - CorsRegistry: Spring 框架提供的 CORS 配置注册器
 */
@Configuration  // 【Spring Boot】配置类注解，启动时自动加载
public class WebConfig implements WebMvcConfigurer {  // 【Spring Boot】实现 Spring MVC 配置扩展接口

    @Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {  // 【Spring Boot】Spring MVC 的 CORS 配置入口
        registry.addMapping("/api/**")           // 【Spring Boot】对所有 /api/ 路径启用 CORS
                .allowedOrigins(configuredOrigins())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")  // 允许的 HTTP 方法
                .allowedHeaders("*")             // 允许所有请求头
                .allowCredentials(true)          // 允许携带 Cookie/认证信息
                .maxAge(3600);                   // 预检请求缓存时间（秒）
    }

    private String[] configuredOrigins() {
        String[] origins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
        if (java.util.Arrays.asList(origins).contains("*")) {
            throw new IllegalStateException("APP_ALLOWED_ORIGINS 不能在凭据模式下使用通配符 *");
        }
        return origins;
    }
}
