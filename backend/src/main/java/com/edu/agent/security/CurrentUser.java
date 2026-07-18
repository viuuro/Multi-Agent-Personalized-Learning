package com.edu.agent.security;

import org.springframework.security.core.Authentication;

public final class CurrentUser {
    private CurrentUser() {}

    public static Long id(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("用户未登录");
        }
        try {
            return Long.valueOf(authentication.getName());
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("登录会话无效", exception);
        }
    }
}
