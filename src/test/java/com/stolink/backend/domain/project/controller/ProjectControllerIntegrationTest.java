package com.stolink.backend.domain.project.controller;

import com.stolink.backend.domain.project.dto.CreateProjectRequest;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.user.dto.LoginRequest;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.integration.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProjectControllerIntegrationTest extends AbstractIntegrationTest {

        @org.springframework.beans.factory.annotation.Autowired
        private com.stolink.backend.support.TestDataFactory testDataFactory;

        @org.springframework.beans.factory.annotation.Autowired
        private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

        private String getAccessToken(User user) throws Exception {
                LoginRequest loginRequest = new LoginRequest();
                loginRequest.setEmail(user.getEmail());
                loginRequest.setPassword("password123!");

                return mockMvc.perform(post("/api/auth/login")
                                .header("Origin", "http://localhost:3000")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andReturn().getResponse().getCookie("access_token").getValue();
        }

        @Test
        @DisplayName("TC-STO-101: 프로젝트 생성 성공")
        void createProject_Success() throws Exception {
                // given
                User user = testDataFactory.createUser("project-owner@example.com", "Writer", "password123!");
                String accessToken = getAccessToken(user);

                CreateProjectRequest request = CreateProjectRequest.builder()
                                .title("New Fantasy Novel")
                                .description("Epic fantasy story")
                                .genre("FANTASY")
                                .build();

                // when
                ResultActions result = mockMvc.perform(post("/api/projects")
                                .header("Origin", "http://localhost:3000")
                                .cookie(new Cookie("access_token", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)));

                // then
                result.andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.title").value("New Fantasy Novel"))
                                .andExpect(jsonPath("$.data.genre").value("FANTASY"))
                                .andExpect(jsonPath("$.data.author").value("Writer"));
        }

        @Test
        @DisplayName("TC-STO-102: 내 프로젝트 목록 조회 성공")
        void getProjects_Success() throws Exception {
                // given
                User user = testDataFactory.createUser("list-owner@example.com", "Collector", "password123!");
                String accessToken = getAccessToken(user);

                testDataFactory.createProject(user, "Project 1");
                testDataFactory.createProject(user, "Project 2");

                // when
                ResultActions result = mockMvc.perform(get("/api/projects")
                                .cookie(new Cookie("access_token", accessToken))
                                .param("page", "1")
                                .param("limit", "10"));

                // then
                result.andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.projects.length()").value(2))
                                .andExpect(jsonPath("$.data.pagination.total").value(2));
        }

        @Test
        @DisplayName("TC-STO-103: 단일 프로젝트 조회 성공")
        void getProject_Success() throws Exception {
                // given
                User user = testDataFactory.createUser("viewer@example.com", "Viewer", "password123!");
                String accessToken = getAccessToken(user);
                Project project = testDataFactory.createProject(user, "My Masterpiece");

                // when
                ResultActions result = mockMvc.perform(get("/api/projects/" + project.getId())
                                .cookie(new Cookie("access_token", accessToken)));

                // then
                result.andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.title").value("My Masterpiece"))
                                .andExpect(jsonPath("$.data.id").value(project.getId().toString()));
        }

        @Test
        @DisplayName("TC-STO-104: 프로젝트 수정 성공")
        void updateProject_Success() throws Exception {
                // given
                User user = testDataFactory.createUser("editor@example.com", "Editor", "password123!");
                String accessToken = getAccessToken(user);
                Project project = testDataFactory.createProject(user, "Old Title");

                CreateProjectRequest updateRequest = CreateProjectRequest.builder()
                                .title("New Title")
                                .description("Updated description")
                                .build();

                // when
                ResultActions result = mockMvc.perform(patch("/api/projects/" + project.getId())
                                .header("Origin", "http://localhost:3000")
                                .cookie(new Cookie("access_token", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest)));

                // then
                result.andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.title").value("New Title"))
                                .andExpect(jsonPath("$.data.description").value("Updated description"));
        }

        @Test
        @DisplayName("TC-STO-105: 프로젝트 삭제 성공")
        void deleteProject_Success() throws Exception {
                // given
                User user = testDataFactory.createUser("deleter@example.com", "Deleter", "password123!");
                String accessToken = getAccessToken(user);
                Project project = testDataFactory.createProject(user, "To Be Deleted");

                // when
                ResultActions result = mockMvc.perform(delete("/api/projects/" + project.getId())
                                .header("Origin", "http://localhost:3000")
                                .cookie(new Cookie("access_token", accessToken)));

                // then
                result.andDo(print())
                                .andExpect(status().isNoContent());

                // verify deletion
                mockMvc.perform(get("/api/projects/" + project.getId())
                                .cookie(new Cookie("access_token", accessToken)))
                                .andExpect(status().isNotFound());
        }
}
