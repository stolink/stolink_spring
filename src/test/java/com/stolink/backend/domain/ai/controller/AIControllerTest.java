package com.stolink.backend.domain.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.JobStatusUpdateRequest;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.ai.service.AICallbackService;
import com.stolink.backend.domain.project.entity.Project;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(AIController.class)
class AIControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AnalysisJobRepository analysisJobRepository;

    @MockBean
    private AICallbackService callbackService;

    // rabbitMQProducerService and projectRepository are likely needed by
    // AIController context
    @MockBean
    private com.stolink.backend.domain.ai.service.RabbitMQProducerService rabbitMQProducerService;

    @MockBean
    private com.stolink.backend.domain.project.repository.ProjectRepository projectRepository;

    @Test
    @DisplayName("Job 상태 업데이트 성공 - 올바른 상태값")
    void updateJobStatus_Success() throws Exception {
        // given
        String jobId = "test-job-id";
        JobStatusUpdateRequest request = new JobStatusUpdateRequest("ANALYZING", "Analysis in progress");

        AnalysisJob mockJob = AnalysisJob.builder()
                .jobId(jobId)
                .status(AnalysisJob.JobStatus.PENDING)
                .build();

        given(analysisJobRepository.findByJobId(jobId)).willReturn(Optional.of(mockJob));

        // when & then
        mockMvc.perform(post("/api/internal/ai/jobs/{jobId}/status", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf())) // Adding csrf token as security is likely enabled
                .andExpect(status.isOk());

        verify(analysisJobRepository).save(any(AnalysisJob.class));
    }

    @Test
    @DisplayName("Job 상태 업데이트 실패 - 잘못된 상태값")
    void updateJobStatus_InvalidStatus() throws Exception {
        // given
        String jobId = "test-job-id";
        JobStatusUpdateRequest request = new JobStatusUpdateRequest("INVALID_STATUS", "Invalid");

        AnalysisJob mockJob = AnalysisJob.builder()
                .jobId(jobId)
                .status(AnalysisJob.JobStatus.PENDING)
                .build();

        given(analysisJobRepository.findByJobId(jobId)).willReturn(Optional.of(mockJob));

        // when & then
        mockMvc.perform(post("/api/internal/ai/jobs/{jobId}/status", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status.isBadRequest());
    }

    @Test
    @DisplayName("AI 분석 콜백 수신 성공 - results 필드 매핑 및 소문자 status")
    void handleAnalysisCallback_Success() throws Exception {
        // given
        // JSON payload mimicking FastAPI response
        String jsonPayload = """
                {
                  "job_id": "test-job-id",
                  "status": "completed",
                  "results": {
                    "characters": []
                  }
                }
                """;

        // when & then
        mockMvc.perform(post("/api/internal/ai/analysis/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
                .with(csrf()))
                .andExpect(status.isOk());

        verify(callbackService).handleAnalysisCallback(any(AnalysisCallbackDTO.class));
    }
}
