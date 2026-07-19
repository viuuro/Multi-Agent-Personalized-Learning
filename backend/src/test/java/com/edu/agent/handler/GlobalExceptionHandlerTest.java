package com.edu.agent.handler;

import com.edu.agent.model.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

class GlobalExceptionHandlerTest {

    @Test
    void preservesResponseStatusExceptionReason() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<Void>> response = handler.handleResponseStatus(
                new ResponseStatusException(BAD_GATEWAY, "图片生成服务暂时不可用"));

        assertEquals(502, response.getStatusCode().value());
        assertEquals(502, response.getBody().getCode());
        assertEquals("图片生成服务暂时不可用", response.getBody().getMessage());
    }
}
