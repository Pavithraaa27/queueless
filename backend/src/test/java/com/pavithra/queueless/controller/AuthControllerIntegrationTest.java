package com.pavithra.queueless.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavithra.queueless.dto.auth.LoginRequest;
import com.pavithra.queueless.dto.auth.RegisterRequest;
import com.pavithra.queueless.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end check that registration + login actually work through the full
 * Spring context (real security filter chain, real H2 database) rather than
 * mocked collaborators.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void registerThenLogin_succeeds() throws Exception {
        RegisterRequest register = new RegisterRequest(
                "Pavithra Nair", "integration@test.com", "secret123", "9999999999", Role.CUSTOMER
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("integration@test.com"))
                .andExpect(jsonPath("$.token").isNotEmpty());

        LoginRequest login = new LoginRequest("integration@test.com", "secret123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void register_rejectsWeakPasswordWithValidationError() throws Exception {
        RegisterRequest weak = new RegisterRequest(
                "Someone", "weak@test.com", "123", null, Role.CUSTOMER
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(weak)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    void login_rejectsWrongPassword() throws Exception {
        RegisterRequest register = new RegisterRequest(
                "Another User", "another@test.com", "secret123", null, Role.CUSTOMER
        );
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        LoginRequest wrongPassword = new LoginRequest("another@test.com", "totally-wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(wrongPassword)))
                .andExpect(status().isUnauthorized());
    }
}
