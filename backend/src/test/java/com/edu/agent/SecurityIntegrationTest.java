package com.edu.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:security-test;MODE=MYSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "ai.mock-enabled=true"
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedApisRequireSessionAndRegistrationCreatesOne() throws Exception {
        mockMvc.perform(get("/api/profile").param("conversationId", "security-test"))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"security-user\",\"password\":\"safe-pass-123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("security-user"))
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("security-user"));
    }

    @Test
    void weakPasswordsAreRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("{\"username\":\"weak-user\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }
}
