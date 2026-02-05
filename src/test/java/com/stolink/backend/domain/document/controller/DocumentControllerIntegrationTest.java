package com.stolink.backend.domain.document.controller;

import com.stolink.backend.domain.document.dto.CreateDocumentRequest;
import com.stolink.backend.domain.document.dto.UpdateDocumentRequest;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.user.dto.LoginRequest;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.integration.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentControllerIntegrationTest extends AbstractIntegrationTest {

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
        @DisplayName("TC-STO-201: 문서 생성 성공")
        void createDocument_Success() throws Exception {
                // given
                User user = testDataFactory.createUser("doc-creator@example.com", "Creator", "password123!");
                String accessToken = getAccessToken(user);
                Project project = testDataFactory.createProject(user, "Fantasy World");

                CreateDocumentRequest request = CreateDocumentRequest.builder()
                                .projectId(project.getId())
                                .title("Chapter 1")
                                .type("TEXT")
                                .synopsis("Beginning of the journey")
                                .build();

                // when
                ResultActions result = mockMvc.perform(post("/api/projects/" + project.getId() + "/documents")
                                .header("Origin", "http://localhost:3000")
                                .cookie(new Cookie("access_token", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)));

                // then
                result.andDo(print())
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.title").value("Chapter 1"))
                                .andExpect(jsonPath("$.data.type").value("text"));
        }

        @Test
        @DisplayName("TC-STO-202: 문서 상세 조회 성공")
        void getDocument_Success() throws Exception {
                // given
                User user = testDataFactory.createUser("doc-viewer@example.com", "Viewer", "password123!");
                String accessToken = getAccessToken(user);
                Project project = testDataFactory.createProject(user, "Viewer Project");
                Document document = testDataFactory.createDocument(project, null, "Intro", Document.DocumentType.TEXT);

                // when
                ResultActions result = mockMvc.perform(get("/api/documents/" + document.getId())
                                .cookie(new Cookie("access_token", accessToken)));

                // then
                result.andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.title").value("Intro"))
                                .andExpect(jsonPath("$.data.id").value(document.getId().toString()));
        }

        @Test
        @DisplayName("TC-STO-203: 문서 정보 수정 성공")
        void updateDocument_Success() throws Exception {
                // given
                User user = testDataFactory.createUser("doc-editor@example.com", "Editor", "password123!");
                String accessToken = getAccessToken(user);
                Project project = testDataFactory.createProject(user, "Edit Project");
                Document document = testDataFactory.createDocument(project, null, "Old Title",
                                Document.DocumentType.TEXT);

                UpdateDocumentRequest request = UpdateDocumentRequest.builder()
                                .title("New Title")
                                .synopsis("Updated synopsis")
                                .build();

                // when
                ResultActions result = mockMvc.perform(patch("/api/documents/" + document.getId())
                                .header("Origin", "http://localhost:3000")
                                .cookie(new Cookie("access_token", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)));

                // then
                result.andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.title").value("New Title"))
                                .andExpect(jsonPath("$.data.synopsis").value("Updated synopsis"));
        }

        @Test
        @DisplayName("TC-STO-204: 문서 본문 수정 성공 (WordCount 업데이트 확인)")
        void updateContent_Success() throws Exception {
                // given
                User user = testDataFactory.createUser("content-editor@example.com", "Writer", "password123!");
                String accessToken = getAccessToken(user);
                Project project = testDataFactory.createProject(user, "Novel");
                Document document = testDataFactory.createDocument(project, null, "Chapter 1",
                                Document.DocumentType.TEXT);

                String newContent = "This is a verified content update.";
                Map<String, String> request = Map.of("content", newContent);

                // when
                ResultActions result = mockMvc.perform(patch("/api/documents/" + document.getId() + "/content")
                                .header("Origin", "http://localhost:3000")
                                .cookie(new Cookie("access_token", accessToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)));

                // then
                result.andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.id").value(document.getId().toString()))
                                .andExpect(jsonPath("$.data.wordCount").exists()); // Word count should be calculated
        }

        @Test
        @DisplayName("TC-STO-205: 문서 삭제 성공")
        void deleteDocument_Success() throws Exception {
                // given
                User user = testDataFactory.createUser("doc-deleter@example.com", "Deleter", "password123!");
                String accessToken = getAccessToken(user);
                Project project = testDataFactory.createProject(user, "Trash Project");
                Document document = testDataFactory.createDocument(project, null, "To Delete",
                                Document.DocumentType.TEXT);

                // when
                ResultActions result = mockMvc.perform(delete("/api/documents/" + document.getId())
                                .header("Origin", "http://localhost:3000")
                                .cookie(new Cookie("access_token", accessToken)));

                // then
                result.andDo(print())
                                .andExpect(status().isNoContent());

                // verify
                mockMvc.perform(get("/api/documents/" + document.getId())
                                .cookie(new Cookie("access_token", accessToken)))
                                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("TC-STO-206: 문서 트리 조회 성공")
        void getDocumentTree_Success() throws Exception {
                // given
                User user = testDataFactory.createUser("tree-viewer@example.com", "Viewer", "password123!");
                String accessToken = getAccessToken(user);
                Project project = testDataFactory.createProject(user, "Tree Project");

                Document folder = testDataFactory.createDocument(project, null, "Folder 1",
                                Document.DocumentType.FOLDER);
                testDataFactory.createDocument(project, folder, "Child Doc", Document.DocumentType.TEXT);

                // when
                ResultActions result = mockMvc.perform(get("/api/projects/" + project.getId() + "/documents")
                                .cookie(new Cookie("access_token", accessToken)));

                // then
                result.andDo(print())
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data[0].title").value("Folder 1"))
                                .andExpect(jsonPath("$.data[0].children[0].title").value("Child Doc"));
        }
}
