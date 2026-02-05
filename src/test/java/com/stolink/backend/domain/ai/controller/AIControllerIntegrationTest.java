package com.stolink.backend.domain.ai.controller;

import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
import com.stolink.backend.domain.ai.dto.GlobalMergeRequestDTO;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.ai.service.RabbitMQProducerService;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.user.dto.LoginRequest;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.global.sse.SseEmitterService;
import com.stolink.backend.integration.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AIControllerIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private RabbitMQProducerService rabbitMQProducerService;

    @MockitoBean
    private SseEmitterService sseEmitterService;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

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
    @DisplayName("TC-STO-401: 문서 분석 요청 성공")
    void analyze_Success() throws Exception {
        // given
        User user = testDataFactory.createUser("ai-user@example.com", "AIUser", "password123!");
        String accessToken = getAccessToken(user);
        Project project = testDataFactory.createProject(user, "AI Project");
        Document document = testDataFactory.createDocument(project, null, "Doc 1", Document.DocumentType.TEXT);

        Map<String, Object> requestBody = Map.of(
                "projectId", project.getId(),
                "documentIds", List.of(document.getId().toString()),
                "content", "Sample content for analysis");

        // when
        ResultActions result = mockMvc.perform(post("/api/ai/analyze")
                .header("Origin", "http://localhost:3000")
                .cookie(new Cookie("access_token", accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)));

        // then
        result.andDo(print())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.jobIds[0]").value(document.getId().toString()));

        verify(rabbitMQProducerService).sendAnalysisTask(any(AnalysisTaskDTO.class));
    }

    @Test
    @DisplayName("TC-STO-402: 분석 작업 상태 조회 성공")
    void getJobStatus_Success() throws Exception {
        // given
        User user = testDataFactory.createUser("job-viewer@example.com", "JobViewer", "password123!");
        String accessToken = getAccessToken(user);
        Project project = testDataFactory.createProject(user, "Job Project");

        AnalysisJob job = AnalysisJob.builder()
                .jobId(UUID.randomUUID().toString())
                .project(project)
                .status(AnalysisJob.JobStatus.PROCESSING)
                .build();
        analysisJobRepository.save(job);

        // when
        ResultActions result = mockMvc.perform(get("/api/ai/jobs/" + job.getJobId())
                .header("Origin", "http://localhost:3000")
                .cookie(new Cookie("access_token", accessToken)));

        // then
        result.andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(job.getJobId()))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("TC-STO-403: 글로벌 병합 요청 성공")
    void triggerGlobalMerge_Success() throws Exception {
        // given
        User user = testDataFactory.createUser("merge-user@example.com", "MergeUser", "password123!");
        String accessToken = getAccessToken(user);
        Project project = testDataFactory.createProject(user, "Merge Project");

        // when
        ResultActions result = mockMvc.perform(post("/api/project/" + project.getId() + "/merge")
                .header("Origin", "http://localhost:3000")
                .cookie(new Cookie("access_token", accessToken)));

        // then
        result.andDo(print())
                .andExpect(status().isOk());

        verify(rabbitMQProducerService).sendGlobalMergeRequest(any(GlobalMergeRequestDTO.class));
    }
}
