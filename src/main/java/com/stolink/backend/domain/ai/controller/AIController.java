package com.stolink.backend.domain.ai.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.AnalysisContext;
import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
import com.stolink.backend.domain.ai.dto.GlobalMergeCallbackDTO;
import com.stolink.backend.domain.ai.dto.GlobalMergeRequestDTO;
import com.stolink.backend.domain.ai.dto.ImageCallbackDTO;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.ai.service.AICallbackService;
import com.stolink.backend.domain.ai.service.RabbitMQProducerService;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.global.common.dto.ApiResponse;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.stolink.backend.domain.ai.dto.AnalysisRequestDTO;
import jakarta.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AIController {

        private final RabbitMQProducerService producerService;
        private final AICallbackService callbackService;
        private final AnalysisJobRepository analysisJobRepository;
        private final com.stolink.backend.domain.character.repository.ImageGenerationTaskRepository imageGenerationTaskRepository;
        private final ProjectRepository projectRepository;
        private final ObjectMapper objectMapper;

        @Value("${app.ai.callback-base-url}")
        private String callbackBaseUrl;

        /**
         * AI 분석 요청
         */
        @PostMapping("/ai/analyze")
        @ResponseStatus(HttpStatus.ACCEPTED)
        public ApiResponse<Map<String, String>> analyze(
                        @AuthenticationPrincipal UUID userId,
                        @Valid @RequestBody AnalysisRequestDTO request) {

                String jobId = UUID.randomUUID().toString();
                String traceId = generateTraceId();

                UUID projectId = request.getProjectId();
                UUID documentId = request.getDocumentId();

                // Project 조회
                Project project = projectRepository.findById(projectId)
                                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

                // Job 생성 및 저장
                AnalysisJob job = AnalysisJob.builder()
                                .jobId(jobId)
                                .project(project)
                                .documentId(documentId)
                                .traceId(traceId)
                                .status(AnalysisJob.JobStatus.PENDING)
                                .build();
                analysisJobRepository.save(job);
                log.info("Created analysis job: {}", jobId);

                // Context 빌드 (선택적)
                AnalysisContext context = buildContext(request.getContext());

                AnalysisTaskDTO task = AnalysisTaskDTO.builder()
                                .jobId(jobId)
                                .projectId(projectId)
                                .documentId(documentId)
                                .content(request.getContent())
                                .callbackUrl(callbackBaseUrl)
                                .traceId(traceId)
                                .context(context)
                                .build();

                try {
                    producerService.sendAnalysisTask(task);
                } catch (Exception e) {
                    log.error("Failed to send analysis task: {}", e.getMessage(), e);
                    job.markAsFailed(e.getMessage());
                    analysisJobRepository.save(job);
                    throw e; // GlobalExceptionHandler will handle it, or return specific error response
                }

                // Job 상태를 PROCESSING으로 업데이트
                job.markAsProcessing();
                analysisJobRepository.save(job);

                log.info("Analysis request sent: jobId={}, traceId={}", jobId, traceId);

                return ApiResponse.<Map<String, String>>builder()
                                .status(HttpStatus.ACCEPTED)
                                .message("Analysis started")
                                .data(Map.of(
                                                "jobId", jobId,
                                                "traceId", traceId,
                                                "status", "processing"))
                                .build();
        }

        /**
         * Job 상태 조회 (프론트엔드 폴링용)
         */
        @GetMapping("/ai/jobs/{jobId}")
        public ApiResponse<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
                AnalysisJob job = analysisJobRepository.findByJobId(jobId)
                                .orElseThrow(() -> new ResourceNotFoundException("AnalysisJob", "jobId", jobId));

                return ApiResponse.ok(Map.of(
                                "jobId", job.getJobId(),
                                "projectId", job.getProject().getId().toString(),
                                "status", job.getStatus().name(),
                                "traceId", job.getTraceId() != null ? job.getTraceId() : "",
                                "processingTimeMs", job.getProcessingTimeMs() != null ? job.getProcessingTimeMs() : 0));
        }

        /**
         * 이미지 생성 Job 상태 조회 (프론트엔드 폴링용 - 분리된 엔드포인트)
         */
        @GetMapping("/ai/image/jobs/{jobId}")
        public ApiResponse<Map<String, Object>> getImageJobStatus(@PathVariable String jobId) {
                com.stolink.backend.domain.character.entity.ImageGenerationTask task = imageGenerationTaskRepository
                                .findById(jobId)
                                .orElseThrow(() -> new ResourceNotFoundException("ImageJob", "jobId", jobId));

                java.util.Map<String, Object> response = new java.util.HashMap<>();
                response.put("jobId", task.getJobId());
                response.put("status", task.getStatus().name());
                response.put("imageUrl", task.getImageUrl() != null ? task.getImageUrl() : "");
                response.put("errorMessage", task.getErrorMessage() != null ? task.getErrorMessage() : "");

                if (task.getProjectId() != null) {
                        response.put("projectId", task.getProjectId().toString());
                }
                if (task.getCharacterId() != null) {
                        response.put("characterId", task.getCharacterId().toString());
                }

                return ApiResponse.ok(response);
        }

        /**
         * Internal callback endpoint for Analysis Worker
         * message_type으로 분기: DOCUMENT_ANALYSIS_RESULT, GLOBAL_MERGE_RESULT
         */
        @PostMapping("/internal/ai/analysis/callback")
        public ApiResponse<Void> handleAnalysisCallback(@RequestBody String rawPayload) {
                // 콜백 데이터 파일 저장 (로그 출력 없음)
                try {
                        java.nio.file.Files.writeString(java.nio.file.Path.of("/tmp/callback_result.json"), rawPayload);
                } catch (java.io.IOException e) {
                        // 파일 저장 실패는 무시
                }

                try {
                        // message_type 먼저 확인하여 분기
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> rawMap = objectMapper.readValue(rawPayload, java.util.Map.class);
                        String messageType = (String) rawMap.get("message_type");

                        if ("GLOBAL_MERGE_RESULT".equals(messageType)) {
                                // 글로벌 병합 결과 처리
                                GlobalMergeCallbackDTO mergeCallback = objectMapper.readValue(rawPayload,
                                                GlobalMergeCallbackDTO.class);
                                log.info("Received GLOBAL_MERGE callback for project: {}, status: {}",
                                                mergeCallback.getProjectId(), mergeCallback.getStatus());
                                callbackService.handleGlobalMergeCallback(mergeCallback);
                        } else {
                                // 기본: 문서 분석 결과 처리 (DOCUMENT_ANALYSIS_RESULT 또는 기존 포맷)
                                AnalysisCallbackDTO callback = objectMapper.readValue(rawPayload,
                                                AnalysisCallbackDTO.class);
                                log.info("Received DOCUMENT_ANALYSIS callback for job: {}, status: {}",
                                                callback.getJobId(), callback.getStatus());
                                callbackService.handleAnalysisCallback(callback);
                        }
                        return ApiResponse.ok();
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                        log.error("Failed to parse callback payload: {}", e.getMessage());
                        return ApiResponse.<Void>builder()
                                        .status(HttpStatus.BAD_REQUEST)
                                        .message("Invalid JSON: " + e.getMessage())
                                        .build();
                } catch (RuntimeException e) {
                        log.error("Error processing callback: {}", e.getMessage(), e);
                        // 500 대신 500을 명시적으로 리턴하되, JSON 파싱이나 예기치 못한 에러를 잡음
                        return ApiResponse.<Void>builder()
                                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                        .message("Processing error: " + e.getMessage())
                                        .build();
                }
        }

        /**
         * Internal endpoint for Job status update (FastAPI에서 호출)
         */
        @PostMapping("/internal/ai/jobs/{jobId}/status")
        public ApiResponse<Void> updateJobStatus(
                        @PathVariable String jobId,
                        @RequestBody com.stolink.backend.domain.ai.dto.JobStatusUpdateRequest request) {
                String status = request.getStatus();
                String message = request.getMessage();

                log.info("Updating analysis job status: {} -> {}", jobId, status);

                AnalysisJob job = analysisJobRepository.findByJobId(jobId)
                                .orElseThrow(() -> new ResourceNotFoundException("AnalysisJob", "jobId", jobId));

                try {
                        AnalysisJob.JobStatus newStatus = AnalysisJob.JobStatus.valueOf(status.toUpperCase());
                        job.updateStatus(newStatus, message);
                        analysisJobRepository.save(job);
                        log.info("AnalysisJob {} status updated to {}", jobId, newStatus);
                        return ApiResponse.ok();
                } catch (IllegalArgumentException e) {
                        log.error("Invalid analysis status: {}", status);
                        return ApiResponse.<Void>builder()
                                        .status(HttpStatus.BAD_REQUEST)
                                        .message("Invalid status: " + status)
                                        .build();
                }
        }

        /**
         * 이미지 생성 작업 상태 업데이트 (FastAPI에서 호출 - 분리된 엔드포인트)
         */
        @PostMapping("/internal/ai/image/jobs/{jobId}/status")
        public ApiResponse<Void> updateImageJobStatus(
                        @PathVariable String jobId,
                        @RequestBody com.stolink.backend.domain.ai.dto.JobStatusUpdateRequest request) {
                String status = request.getStatus();
                String message = request.getMessage();

                log.info("Updating image job status: {} -> {}", jobId, status);

                com.stolink.backend.domain.character.entity.ImageGenerationTask task = imageGenerationTaskRepository
                                .findById(jobId)
                                .orElseThrow(() -> new ResourceNotFoundException("ImageJob", "jobId", jobId));

                try {
                        com.stolink.backend.domain.character.entity.ImageGenerationTask.TaskStatus newStatus = com.stolink.backend.domain.character.entity.ImageGenerationTask.TaskStatus
                                        .valueOf(status.toUpperCase());
                        task.setStatus(newStatus);
                        if (message != null)
                                task.setErrorMessage(message);
                        imageGenerationTaskRepository.save(task);
                        log.info("ImageJob {} status updated to {}", jobId, newStatus);
                        return ApiResponse.ok();
                } catch (IllegalArgumentException e) {
                        log.error("Invalid image task status: {}", status);
                        return ApiResponse.<Void>builder()
                                        .status(HttpStatus.BAD_REQUEST)
                                        .message("Invalid status: " + status)
                                        .build();
                }
        }

        /**
         * Internal callback endpoint for Image Worker
         */
        @PostMapping("/internal/ai/image/callback")
        public ApiResponse<Void> handleImageCallback(@RequestBody ImageCallbackDTO callback) {
                log.info("Received image callback for job: {}, character: {}",
                                callback.getJobId(), callback.getCharacterId());
                callbackService.handleImageCallback(callback);
                return ApiResponse.ok();
        }

        /**
         * Trace ID 생성 (분산 추적용)
         */
        private String generateTraceId() {
                return String.format("trace-%s-%s",
                                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                                UUID.randomUUID().toString().substring(0, 8));
        }

        /**
         * Context 빌드 (요청에서 추출)
         */
        private AnalysisContext buildContext(Map<String, Object> contextMap) {
                if (contextMap == null) {
                        return null;
                }

                return AnalysisContext.builder()
                                .chapterNumber((Integer) contextMap.get("chapterNumber"))
                                .totalChapters((Integer) contextMap.get("totalChapters"))
                                .worldRulesSummary((String) contextMap.get("worldRulesSummary"))
                                .build();
        }

        /**
         * Global Merge 수동 트리거 (Integration Test Scenario B)
         */
        @PostMapping("/project/{projectId}/merge")
        public ApiResponse<Void> triggerGlobalMerge(
                        @PathVariable UUID projectId,
                        @AuthenticationPrincipal UUID userId) {

                log.info("Triggering global merge for project: {} (User: {})", projectId, userId);

                String traceId = generateTraceId();
                GlobalMergeCallbackDTO.builder().build(); // Just to ensure import if needed, or better just use the DTO

                GlobalMergeRequestDTO request = GlobalMergeRequestDTO.builder()
                                .projectId(projectId)
                                .callbackUrl(callbackBaseUrl)
                                .traceId(traceId)
                                .build();

                producerService.sendGlobalMergeRequest(request);

                return ApiResponse.ok();
        }
}
