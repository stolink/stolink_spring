package com.stolink.backend.domain.ai.controller;

import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.AnalysisContext;
import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AIController {

        private final RabbitMQProducerService producerService;
        private final AICallbackService callbackService;
        private final AnalysisJobRepository analysisJobRepository;
        private final ProjectRepository projectRepository;

        @Value("${app.ai.callback-base-url}")
        private String callbackBaseUrl;

        /**
         * AI 분석 요청
         */
        @PostMapping("/ai/analyze")
        @ResponseStatus(HttpStatus.ACCEPTED)
        public ApiResponse<Map<String, String>> analyze(
                        @RequestHeader(value = "X-User-Id", required = false) UUID userId,
                        @RequestBody Map<String, Object> request) {

                String jobId = UUID.randomUUID().toString();
                String traceId = generateTraceId();

                UUID projectId = UUID.fromString((String) request.get("projectId"));
                UUID documentId = UUID.fromString((String) request.get("documentId"));

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
                AnalysisContext context = buildContext(request);

                AnalysisTaskDTO task = AnalysisTaskDTO.builder()
                                .jobId(jobId)
                                .projectId(projectId)
                                .documentId(documentId)
                                .content((String) request.get("content"))
                                .callbackUrl(callbackBaseUrl + "/analysis/callback")
                                .traceId(traceId)
                                .context(context)
                                .build();

                producerService.sendAnalysisTask(task);

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
         * Job 상태 조회
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
         * Internal callback endpoint for Analysis Worker
         */
        @PostMapping("/internal/ai/analysis/callback")
        public ApiResponse<Void> handleAnalysisCallback(@RequestBody AnalysisCallbackDTO callback) {
                log.info("Received analysis callback for job: {}, status: {}",
                                callback.getJobId(), callback.getStatus());
                callbackService.handleAnalysisCallback(callback);
                return ApiResponse.ok();
        }

        /**
         * Internal endpoint for Job status update (FastAPI에서 호출)
         */
        @PostMapping("/internal/ai/jobs/{jobId}/status")
        public ApiResponse<Void> updateJobStatus(
                        @PathVariable String jobId,
                        @RequestBody Map<String, String> request) {
                String status = request.get("status");
                String message = request.get("message");

                log.info("Updating job status: {} -> {}", jobId, status);

                AnalysisJob job = analysisJobRepository.findByJobId(jobId)
                                .orElseThrow(() -> new ResourceNotFoundException("AnalysisJob", "jobId", jobId));

                try {
                        AnalysisJob.JobStatus newStatus = AnalysisJob.JobStatus.valueOf(status.toUpperCase());
                        job.updateStatus(newStatus, message);
                        analysisJobRepository.save(job);
                        log.info("Job {} status updated to {}", jobId, newStatus);
                } catch (IllegalArgumentException e) {
                        log.error("Invalid job status: {}", status);
                        return ApiResponse.<Void>builder()
                                        .status(HttpStatus.BAD_REQUEST)
                                        .message("Invalid status: " + status)
                                        .build();
                }

                return ApiResponse.ok();
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
        @SuppressWarnings("unchecked")
        private AnalysisContext buildContext(Map<String, Object> request) {
                Map<String, Object> contextMap = (Map<String, Object>) request.get("context");
                if (contextMap == null) {
                        return null;
                }

                return AnalysisContext.builder()
                                .chapterNumber((Integer) contextMap.get("chapterNumber"))
                                .totalChapters((Integer) contextMap.get("totalChapters"))
                                .worldRulesSummary((String) contextMap.get("worldRulesSummary"))
                                .build();
        }
}
