package com.stolink.backend.domain.share.controller;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.share.entity.Share;
import com.stolink.backend.domain.share.repository.ShareRepository;
import com.stolink.backend.domain.user.dto.LoginRequest;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.integration.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ShareControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShareRepository shareRepository;

    @Autowired
    private com.stolink.backend.support.TestDataFactory testDataFactory;

    @Autowired
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
    @DisplayName("TC-STO-501: 공유 링크 생성 성공")
    void createShareLink_Success() throws Exception {
        // given
        User user = testDataFactory.createUser("share-creator@example.com", "ShareCreator", "password123!");
        String accessToken = getAccessToken(user);
        Project project = testDataFactory.createProject(user, "Shared Project");

        // when
        ResultActions result = mockMvc.perform(post("/api/projects/" + project.getId() + "/share")
                .header("Origin", "http://localhost:3000")
                .cookie(new Cookie("access_token", accessToken))
                .contentType(MediaType.APPLICATION_JSON)); // Empty body is allowed

        // then
        result.andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.projectId").value(project.getId().toString()))
                .andExpect(jsonPath("$.data.shareId").exists());

        assertTrue(shareRepository.findByProjectId(project.getId()).isPresent());
    }

    @Test
    @DisplayName("TC-STO-502: 공유된 프로젝트 조회 성공 (공개 링크)")
    void getSharedProject_Success() throws Exception {
        // given
        User user = testDataFactory.createUser("share-viewer@example.com", "ShareCreator", "password123!");
        Project project = testDataFactory.createProject(user, "Public Project");

        Share share = Share.builder()
                .project(project)
                .build();
        shareRepository.save(share);

        // when (No Auth required)
        ResultActions result = mockMvc.perform(get("/api/share/" + share.getId())
                .header("Origin", "http://localhost:3000"));

        // then
        result.andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Public Project"));
    }

    @Test
    @DisplayName("TC-STO-503: 공유 링크 삭제 성공")
    void deleteShareLink_Success() throws Exception {
        // given
        User user = testDataFactory.createUser("share-deleter@example.com", "ShareDeleter", "password123!");
        String accessToken = getAccessToken(user);
        Project project = testDataFactory.createProject(user, "Delete Share Project");

        Share share = Share.builder()
                .project(project)
                .build();
        shareRepository.save(share);

        // when
        ResultActions result = mockMvc.perform(delete("/api/projects/" + project.getId() + "/share")
                .header("Origin", "http://localhost:3000")
                .cookie(new Cookie("access_token", accessToken)));

        // then
        result.andDo(print())
                .andExpect(status().isNoContent());

        assertTrue(shareRepository.findByProjectId(project.getId()).isEmpty());
    }
}
