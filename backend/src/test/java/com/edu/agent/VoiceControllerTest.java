package com.edu.agent;

import com.edu.agent.controller.VoiceController;
import com.edu.agent.service.RequestRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.core.Authentication;

import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoiceControllerTest {

    private RequestRateLimiter rateLimiter;
    private VoiceController controller;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RequestRateLimiter.class);
        when(rateLimiter.tryAcquire("voice:1", 10, 60)).thenReturn(true);
        controller = new VoiceController(new ObjectMapper(), rateLimiter);
        ReflectionTestUtils.setField(controller, "pythonAiUrl", "http://127.0.0.1:1");
        ReflectionTestUtils.setField(controller, "rateLimitPerMinute", 10);
        ReflectionTestUtils.setField(controller, "maxConcurrentPerUser", 2);
    }

    private Authentication authenticatedUser() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("1");
        return authentication;
    }

    @Test
    void requiresCurrentUserIdentity() {
        assertThatThrownBy(() -> controller.welcome(
                Map.of("username", "viuuro", "text", "你好"), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("用户未登录");
    }

    @Test
    void rejectsOversizedSpeechBeforeCallingPython() {
        assertThat(controller.welcome(Map.of(
                "username", "viuuro",
                "text", "x".repeat(2001)
        ), authenticatedUser()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void limitsRequestsPerUser() {
        when(rateLimiter.tryAcquire("voice:1", 10, 60)).thenReturn(false);
        assertThat(controller.welcome(
                Map.of("username", "viuuro", "text", "第二条"), authenticatedUser())
                .getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
