package com.edu.agent;

import com.edu.agent.controller.VoiceController;
import com.edu.agent.model.User;
import com.edu.agent.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VoiceControllerTest {

    private UserRepository userRepository;
    private VoiceController controller;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        controller = new VoiceController(new ObjectMapper(), userRepository);
        ReflectionTestUtils.setField(controller, "pythonAiUrl", "http://127.0.0.1:1");
        ReflectionTestUtils.setField(controller, "rateLimitPerMinute", 20);
        ReflectionTestUtils.setField(controller, "maxConcurrentPerUser", 2);
    }

    @Test
    void requiresCurrentUserIdentity() {
        assertThat(controller.synthesize(null, Map.of("username", "viuuro", "text", "你好"))
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        User stored = new User();
        stored.setId(1L);
        stored.setUsername("viuuro");
        when(userRepository.findById(1L)).thenReturn(Optional.of(stored));
        assertThat(controller.synthesize(1L, Map.of("username", "other", "text", "你好"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsOversizedSpeechBeforeCallingPython() {
        assertThat(controller.synthesize(1L, Map.of(
                "username", "viuuro",
                "text", "x".repeat(2001)
        )).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void limitsRequestsPerUser() {
        User stored = new User();
        stored.setId(1L);
        stored.setUsername("viuuro");
        when(userRepository.findById(1L)).thenReturn(Optional.of(stored));
        ReflectionTestUtils.setField(controller, "rateLimitPerMinute", 1);

        controller.synthesize(1L, Map.of("username", "viuuro", "text", "第一条"));
        assertThat(controller.synthesize(1L, Map.of("username", "viuuro", "text", "第二条"))
                .getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
