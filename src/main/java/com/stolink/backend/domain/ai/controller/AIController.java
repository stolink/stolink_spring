package com.stolink.backend.domain.ai.controller;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.AnalysisContext;
import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
import com.stolink.backend.domain.ai.dto.BatchRetryRequest;
import com.stolink.backend.domain.ai.dto.BatchRetryResponse;
import com.stolink.backend.domain.ai.dto.DocumentAnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.GlobalMergeCallbackDTO;
import com.stolink.backend.domain.ai.dto.GlobalMergeRequestDTO;
import com.stolink.backend.domain.ai.dto.ImageCallbackDTO;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.ai.service.AICallbackService;
import com.stolink.backend.domain.ai.service.DocumentAnalysisPublisher;
import com.stolink.backend.domain.ai.service.RabbitMQProducerService;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.global.common.dto.ApiResponse;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import com.stolink.backend.global.sse.SseEmitterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    private final DocumentRepository documentRepository;
    private final DocumentAnalysisPublisher documentAnalysisPublisher;
    private final ObjectMapper objectMapper;
    private final SseEmitterService sseEmitterService;

    @Value("${app.ai.callback-base-url}")
    private String callbackBaseUrl;

    /**
     * [DEBUG] 모든 분석 작업 삭제 (무한 폴링 방지용)
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/ai/debug/jobs")
    public ApiResponse<Void> clearAllJobs() {
        log.warn("Clearing all analysis jobs via debug endpoint");
        analysisJobRepository.deleteAll();
        return ApiResponse.ok();
    }

    /**
     * 작업별 전용 스트림 (프론트엔드 호환용)
     */
    @GetMapping(value = "/ai/jobs/{jobId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobStatus(@PathVariable String jobId) {
        log.info("SSE stream requested for job: {}", jobId);
        AnalysisJob job = analysisJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("AnalysisJob", "jobId", jobId));

        SseEmitter emitter = sseEmitterService.createEmitter(job.getProject().getId());

        // 연결 즉시 현재 진행 상태 전송 (새로고침 시 상태 동기화)
        callbackService.sendProgressUpdate(job.getProject().getId());

        return emitter;
    }

    @PostMapping("/ai/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SuppressWarnings("unchecked")
    public ApiResponse<Map<String, Object>> analyze(
            @AuthenticationPrincipal UUID userId,
            @RequestBody Map<String, Object> request) { // Using Map for flexibility as per Dev

        log.info("Analyze request body: {}", request);

        Object projectIdObj = request.get("projectId");
        if (projectIdObj == null) {
            throw new IllegalArgumentException("projectId is required");
        }

        UUID projectId = UUID.fromString(projectIdObj.toString());
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        // documentId (단일) 또는 documentIds (리스트) 처리
        java.util.List<UUID> documentIds = new java.util.ArrayList<>();
        if (request.containsKey("documentId") && request.get("documentId") != null) {
            documentIds.add(UUID.fromString(request.get("documentId").toString()));
        } else if (request.containsKey("documentIds") && request.get("documentIds") instanceof java.util.List) {
            java.util.List<String> ids = (java.util.List<String>) request.get("documentIds");
            for (String id : ids) {
                documentIds.add(UUID.fromString(id));
            }
        }

        if (documentIds.isEmpty()) {
            throw new IllegalArgumentException("documentId or documentIds is required");
        }

        log.info("Processing analysis for {} documents", documentIds.size());

        String firstJobId = null;
        String traceId = generateTraceId();
        AnalysisContext context = buildContext(request);
        String content = (String) request.get("content");

        for (UUID documentId : documentIds) {
            String jobId = UUID.randomUUID().toString();
            if (firstJobId == null)
                firstJobId = jobId;

            // Job 생성 및 저장
            AnalysisJob job = AnalysisJob.builder()
                    .jobId(jobId)
                    .project(project)
                    .documentId(documentId)
                    .traceId(traceId)
                    .status(AnalysisJob.JobStatus.PENDING)
                    .build();
            analysisJobRepository.save(job);

            // Document 상태 업데이트 (QUEUED) 및 저장
            UUID finalDocumentId = documentId;
            documentRepository.findById(finalDocumentId).ifPresent(doc -> {
                doc.updateAnalysisStatus(com.stolink.backend.domain.document.entity.Document.AnalysisStatus.QUEUED);
                documentRepository.save(doc);
                log.info("Reset document {} analysis status to QUEUED for jobId: {}", finalDocumentId, jobId);
            });

            AnalysisTaskDTO task = AnalysisTaskDTO.builder()
                    .jobId(jobId)
                    .projectId(projectId)
                    .documentId(documentId)
                    .content(content)
                    .callbackUrl(callbackBaseUrl + "/internal/ai/analysis/callback")
                    .traceId(traceId)
                    .context(context)
                    .build();

            producerService.sendAnalysisTask(task);

            // Job 상태를 PROCESSING으로 업데이트
            job.markAsProcessing();
            analysisJobRepository.save(job);
            log.info("Analysis request sent: jobId={}, documentId={}", jobId, documentId);
        }

        return ApiResponse.<Map<String, Object>>builder()
                .status(HttpStatus.ACCEPTED)
                .message("Analysis started for " + documentIds.size() + " documents")
                .data(Map.of(
                        "jobId", firstJobId,
                        "jobIds", documentIds.stream().map(Object::toString).toList(),
                        "traceId", traceId,
                        "status", "processing"))
                .build();
    }

    /**
     * Job 상태 조회 (프론트엔드 폴링용)
     */
    /**
     * Job 상태 조회 (프론트엔드 폴링용)
     */
    @GetMapping({ "/ai/jobs/{jobId}", "/ai/jobs/{jobId}/status" })
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> getJobStatus(@PathVariable String jobId) {
        log.debug("Get Job Status request for: {}", jobId);
        AnalysisJob job = analysisJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("AnalysisJob", "jobId", jobId));

        String projectId = "";
        if (job.getProject() != null) {
            projectId = job.getProject().getId().toString();
        } else {
            log.warn("AnalysisJob {} has no associated project", jobId);
        }

        return ApiResponse.ok(Map.of(
                "jobId", job.getJobId(),
                "projectId", projectId,
                "status", job.getStatus().name(),
                "traceId", job.getTraceId() != null ? job.getTraceId() : "",
                "documentId", job.getDocumentId() != null ? job.getDocumentId().toString() : "",
                "processingTimeMs", job.getProcessingTimeMs() != null ? job.getProcessingTimeMs() : 0));
    }

    /**
     * 이미지 생성 Job 상태 조회
     */
    @GetMapping({ "/ai/image/jobs/{jobId}", "/ai/image/jobs/{jobId}/status" })
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
     * Internal callback endpoint for Analysis Worker (Documented path)
     */
    @PostMapping("/internal/ai/analysis/callback")
    public ApiResponse<Void> handleInternalAICallback(jakarta.servlet.http.HttpServletRequest request) {
        try (java.io.InputStream inputStream = request.getInputStream()) {
            return processPayload(inputStream);
        } catch (java.io.IOException e) {
            log.error("Failed to get input stream from request", e);
            return ApiResponse.<Void>builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .message("Failed to read request body")
                    .build();
        }
    }

    /**
     * Legacy callback endpoint
     */
    @PostMapping("/ai-callback")
    public ApiResponse<Void> handleAICallback(jakarta.servlet.http.HttpServletRequest request) {
        try (java.io.InputStream inputStream = request.getInputStream()) {
            return processPayload(inputStream);
        } catch (java.io.IOException e) {
            log.error("Failed to get input stream from request", e);
            return ApiResponse.<Void>builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .message("Failed to read request body")
                    .build();
        }
    }

    private ApiResponse<Void> processPayload(java.io.InputStream inputStream) {
        // [Debug] Log start of processing (Do NOT log full payload)
        log.info("Starting to process AI callback payload stream...");

        try {
            // Use readTree on InputStream directly to avoid String allocation of the whole
            // body
            JsonNode root = objectMapper.readTree(inputStream);

            if (root == null || root.isEmpty()) {
                log.error("Received null or empty AI callback payload");
                return ApiResponse.<Void>builder()
                        .status(HttpStatus.BAD_REQUEST)
                        .message("Payload is null or empty")
                        .build();
            }

            String messageType = root.path("message_type").asText(null);
            log.info("Processing AI callback, message_type: {}", messageType);

            // Save only a summary or simplified version for debug if needed
            // avoiding full string serialization
            try {
                // For debug: write just the message type and status to a file, or a truncated
                // version
                java.util.Map<String, Object> debugInfo = new java.util.HashMap<>();
                debugInfo.put("message_type", messageType);
                debugInfo.put("timestamp", java.time.LocalDateTime.now().toString());
                // Add simple top-level fields
                if (root.has("job_id"))
                    debugInfo.put("job_id", root.get("job_id").asText());
                if (root.has("status"))
                    debugInfo.put("status", root.get("status").asText());

                objectMapper.writeValue(
                        new java.io.File("/tmp/callback_summary.json"),
                        debugInfo);
            } catch (Exception e) {
                log.warn("Failed to save callback summary", e);
            }

            if ("DOCUMENT_ANALYSIS_RESULT".equals(messageType)) {
                DocumentAnalysisCallbackDTO callback = objectMapper.treeToValue(root,
                        DocumentAnalysisCallbackDTO.class);
                callbackService.handleDocumentAnalysisCallback(callback);
            } else if ("GLOBAL_MERGE_RESULT".equals(messageType)) {
                GlobalMergeCallbackDTO callback = objectMapper.treeToValue(root,
                        GlobalMergeCallbackDTO.class);
                callbackService.handleGlobalMergeCallback(callback);
            } else {
                // Default or Legacy
                AnalysisCallbackDTO callback = objectMapper.treeToValue(root,
                        AnalysisCallbackDTO.class);
                callbackService.handleAnalysisCallback(callback);
            }

            return ApiResponse.ok();
        } catch (Exception e) {
            log.error("Failed to process AI callback: {}", e.getMessage(), e);
            return ApiResponse.<Void>builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .message("Error: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Internal endpoint for Job status update
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
     * 이미지 생성 작업 상태 업데이트
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
     * Trace ID 생성
     */
    private String generateTraceId() {
        return String.format("trace-%s-%s",
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Context 빌드
     */
    private AnalysisContext buildContext(Map<String, Object> contextMap) {
        if (contextMap == null) {
            return null;
        }

        return AnalysisContext.builder()
                .chapterNumber(
                        contextMap.get("chapterNumber") != null ? (Integer) contextMap.get("chapterNumber") : null)
                .totalChapters(
                        contextMap.get("totalChapters") != null ? (Integer) contextMap.get("totalChapters") : null)
                .worldRulesSummary((String) contextMap.get("worldRulesSummary"))
                .build();
    }

    /**
     * Global Merge 수동 트리거
     */
    @PostMapping("/project/{projectId}/merge")
    public ApiResponse<Void> triggerGlobalMerge(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID userId) {

        log.info("Triggering global merge for project: {} (User: {})", projectId, userId);

        String traceId = generateTraceId();

        GlobalMergeRequestDTO request = GlobalMergeRequestDTO.builder()
                .projectId(projectId)
                .callbackUrl(callbackBaseUrl + "/internal/ai/analysis/callback")
                .traceId(traceId)
                .build();

        producerService.sendGlobalMergeRequest(request);

        return ApiResponse.ok();
    }

    // ==================== 배치 기반 순서 보장 API ====================

    /**
     * 배치 재발송 요청 API
     * AI Backend에서 타임아웃된 배치의 누락 문서 재발송을 요청할 때 사용됩니다.
     */
    @PostMapping("/internal/ai/batch/retry")
    public ApiResponse<BatchRetryResponse> handleBatchRetry(
            @jakarta.validation.Valid @RequestBody BatchRetryRequest request) {

        log.info("Batch retry request: batchId={}, projectId={}, missingOrders={}",
                request.getBatchId(), request.getProjectId(), request.getMissingDocumentOrders());

        try {
            BatchRetryResponse response = documentAnalysisPublisher.retryBatch(request);
            log.info("Batch retry response: status={}, retriedOrders={}",
                    response.getStatus(), response.getRetriedOrders());
            return ApiResponse.ok(response);
        } catch (Exception e) {
            log.error("Batch retry failed: {}", e.getMessage(), e);
            BatchRetryResponse cancelledResponse = BatchRetryResponse.cancelled(
                    request.getBatchId(),
                    "Failed to retry batch: " + e.getMessage());
            return ApiResponse.ok(cancelledResponse);
        }
    }
}
