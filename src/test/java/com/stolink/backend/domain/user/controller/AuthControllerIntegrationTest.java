package com.stolink.backend.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.user.dto.LoginRequest;
import com.stolink.backend.domain.user.dto.RegisterRequest;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.integration.AbstractIntegrationTest;
import com.stolink.backend.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AuthControllerIntegrationTest extends AbstractIntegrationTest {

        @Autowired
        private TestDataFactory testDataFactory;

        @Autowired
        private ObjectMapper objectMapper;

        @Test
        @DisplayName("TC-STO-001: 일반 회원가입 성공")
        void register_Success() throws Exception {
                // given
                RegisterRequest request = new RegisterRequest();
                request.setEmail("newuser@example.com");
                request.setNickname("NewUser");
                request.setPassword("password123!");

                // when
                ResultActions result = mockMvc.perform(post("/api/auth/register")
                                .header("Origin", "http://localhost:3000")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)));

                // then
                result.andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.user.email").value("newuser@example.com"))
                                .andExpect(header().exists("Set-Cookie"));
        }

        @Test
        @DisplayName("TC-STO-002: 일반 로그인 성공")
        void login_Success() throws Exception {
                // given
                testDataFactory.createUser("login@example.com", "LoginUser", "password123!");
                LoginRequest request = new LoginRequest();
                request.setEmail("login@example.com");
                request.setPassword("password123!");

                // when
                ResultActions result = mockMvc.perform(post("/api/auth/login")
                                .header("Origin", "http://localhost:3000")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)));

                // then
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.user.email").value("login@example.com"))
                                .andExpect(header().exists("Set-Cookie"));
        }

        @Test
        @DisplayName("TC-STO-006: 내 정보 조회 성공 (인증 쿠키 포함 시)")
        void getMe_Success() throws Exception {
                // given
                User user = testDataFactory.createDefaultUser();

                // 1. 로그인하여 쿠키 획득
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail(user.getEmail());
                loginRequest.setPassword("password123!");

                String accessCookie = mockMvc.perform(post("/api/auth/login")
                                .header("Origin", "http://localhost:3000")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn().getResponse().getCookie("access_token").getValue();

                // when
                ResultActions result = mockMvc.perform(get("/api/auth/me")
                                .cookie(new jakarta.servlet.http.Cookie("access_token", accessCookie)));

                // then
                result.andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.email").value(user.getEmail()));
        }
}
