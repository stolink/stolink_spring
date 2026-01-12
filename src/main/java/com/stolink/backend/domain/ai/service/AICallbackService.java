package com.stolink.backend.domain.ai.service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.DocumentAnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.GlobalMergeCallbackDTO;
import com.stolink.backend.domain.ai.dto.ImageCallbackDTO;
import com.stolink.backend.domain.ai.dto.callback.CharacterTimelineDTO;
import com.stolink.backend.domain.ai.dto.callback.ConsistencyReportDTO;
import com.stolink.backend.domain.ai.dto.callback.PlotDTO;
import com.stolink.backend.domain.ai.dto.callback.ValidationDTO;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.entity.CallbackLog;
import com.stolink.backend.domain.ai.entity.CharacterTimeline;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.ai.repository.CallbackLogRepository;
import com.stolink.backend.domain.ai.repository.CharacterTimelineRepository;
import com.stolink.backend.domain.character.entity.ImageGenerationTask;
import com.stolink.backend.domain.character.repository.ImageGenerationTaskRepository;
import com.stolink.backend.domain.consistency.entity.ConsistencyReport;
import com.stolink.backend.domain.consistency.repository.ConsistencyReportRepository;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.foreshadowing.entity.Foreshadowing;
import com.stolink.backend.domain.foreshadowing.repository.ForeshadowingRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.validation.entity.ValidationResult;
import com.stolink.backend.domain.validation.repository.ValidationResultRepository;
import com.stolink.backend.global.sse.SseEmitterService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI Worker 콜백 처리 서비스
 *
 * Multi-Agent 파이프라인 분석 결과를 처리합니다.
 * Character, Event, Setting, Relationship은 AI 백엔드가 직접 Neo4j에 저장하므로
 * Spring Backend는 별도의 저장을 수행하지 않습니다.
 * 오직 부가 정보(Plot, Consistency, Validation)만 Postgres에 저장하고 상태를 업데이트합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AICallbackService {

    private final DocumentRepository documentRepository;
    private final ImageGenerationTaskRepository imageGenerationTaskRepository;
    private final com.stolink.backend.domain.character.service.CharacterService characterService;

    private final AnalysisJobRepository analysisJobRepository;
    private final ConsistencyReportRepository consistencyReportRepository;
    private final ValidationResultRepository validationResultRepository;
    private final ForeshadowingRepository foreshadowingRepository;
    private final ProjectRepository projectRepository;
    private final CharacterTimelineRepository characterTimelineRepository;
    private final com.stolink.backend.domain.ai.repository.DocumentSummaryRepository documentSummaryRepository;

    private final CallbackLogRepository callbackLogRepository;
    private final ObjectMapper objectMapper;
    private final SseEmitterService sseEmitterService;
    private final TransactionTemplate transactionTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    @Value("${app.ai.callback-base-url}")
    private String callbackBaseUrl;

    /**
     * 분석 결과 콜백 처리 (Default AnalysisCallbackDTO)
     */
    public void handleAnalysisCallback(AnalysisCallbackDTO callback) {
        // Clear JPA L1 cache to ensure fresh reads
        entityManager.clear();

        String jobId = callback.getJobId();
        log.info("Processing analysis callback for job: {}, status: {}",
                jobId, callback.getStatus());

        // Null check for jobId
        if (jobId == null || jobId.isBlank()) {
            log.error("Job ID is missing in analysis callback. Callback dump: {}", callback);
            return;
        }

        // Idempotent check
        if (callbackLogRepository.existsByJobId(jobId)) {
            log.warn("Duplicate callback ignored: {}", jobId);
            return;
        }

        // Job 조회
        AnalysisJob job = analysisJobRepository.findByJobId(jobId).orElse(null);

        // [TEMPORARY] Dummy Data Injection Logic
        if (job == null && jobId.startsWith("dummy")) {
            log.warn("Job not found, but detecting dummy data injection. Creating context for insertion...");
            UUID tempProjectId = null;
            try {
                String[] jobIdParts = callback.getJobId().split("::");
                if (jobIdParts.length >= 2) {
                    try {
                        tempProjectId = UUID.fromString(jobIdParts[1]);
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid project ID in dummy jobId: {}", jobIdParts[1]);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse dummy project ID from jobId: {}", callback.getJobId());
            }

            if (tempProjectId == null) {
                log.error("Cannot proceed with dummy injection: Project ID is missing or invalid");
                return;
            }

            try {
                final UUID finalTargetProjectId = tempProjectId;
                log.info("Dummy injection target project: {}", finalTargetProjectId);
                Project project = projectRepository.findById(finalTargetProjectId)
                        .orElseThrow(() -> new RuntimeException("Target project not found: " + finalTargetProjectId));

                // Find or Create a Placeholder Document
                Document doc = documentRepository.findTextDocumentsByProjectId(finalTargetProjectId).stream()
                        .findFirst()
                        .orElseGet(() -> {
                            Document newDoc = Document.builder()
                                    .project(project)
                                    .title("Dummy Data Container")
                                    .type(Document.DocumentType.TEXT)
                                    .order(0)
                                    .content("Content for dummy data holder")
                                    .build();
                            return documentRepository.save(newDoc);
                        });

                job = AnalysisJob.builder()
                        .jobId(callback.getJobId())
                        .project(project)
                        .documentId(doc.getId())
                        .status(AnalysisJob.JobStatus.PROCESSING)
                        .startedAt(java.time.LocalDateTime.now())
                        .build();
                analysisJobRepository.save(job);
                log.info("Created temporary job context: {}", job.getJobId());

                // Note: Character and Relationship cleanup is handled by AI Backend in Neo4j
                // Spring Backend does not manage this data
            } catch (Exception e) {
                log.error("Failed to create dummy context or cleanup: {}", e.getMessage());
            }
        }

        if (job == null)

        {
            log.error("Job not found: {}", callback.getJobId());
            return;
        }

        processCallbackWithJob(callback, job);
    }

    /**
     * Document Analysis Callback (Python -> Spring)
     * Handles specific DTO from Python and adapts to internal logic.
     */
    public void handleDocumentAnalysisCallback(DocumentAnalysisCallbackDTO callback) {
        entityManager.clear();

        String traceId = callback.getTraceId();

        if (traceId == null) {
            log.error("Trace ID is missing in document analysis callback. callback dump: {}", callback);
            return;
        }

        log.info("Processing document analysis callback, traceId: {}, status: {}", traceId, callback.getStatus());

        // Find Job by Trace ID since JobID is missing in DTO
        AnalysisJob job = analysisJobRepository.findByTraceId(traceId).orElse(null);
        if (job == null) {
            log.error("Job not found for traceId: {}", traceId);
            return;
        }

        // Adapt DocumentAnalysisCallbackDTO to AnalysisCallbackDTO
        AnalysisCallbackDTO adaptedCallback = AnalysisCallbackDTO.builder()
                .jobId(job.getJobId())
                .status(callback.getStatus())
                .error(callback.getError() != null ? callback.getError().toString() : null)
                .processingTimeMs(callback.getProcessingTimeMs())
                .traceId(traceId)
                .build();

        // [Debug for E2E] Save raw callback to file
        try {
            java.io.File file = new java.io.File("/tmp/callback_result.json");
            objectMapper.writeValue(file, callback);
            log.info("Saved callback data to /tmp/callback_result.json for E2E verification");
        } catch (Exception e) {
            log.warn("Failed to save callback data to file: {}", e.getMessage());
        }

        // Convert Maps to DTOs
        try {
            if (callback.getPlotIntegration() != null) {
                PlotDTO plot = objectMapper.convertValue(callback.getPlotIntegration(), PlotDTO.class);
                adaptedCallback.setPlotIntegration(plot);
            }
            if (callback.getConsistencyReport() != null) {
                ConsistencyReportDTO consistency = objectMapper.convertValue(callback.getConsistencyReport(),
                        ConsistencyReportDTO.class);
                adaptedCallback.setConsistencyReport(consistency);
            }
            if (callback.getValidation() != null) {
                ValidationDTO validation = objectMapper.convertValue(callback.getValidation(), ValidationDTO.class);
                adaptedCallback.setValidation(validation);
            }
        } catch (IllegalArgumentException e) {
            log.error("Failed to convert callback data to DTOs", e);
        }

        // 4. Document Summary 저장 (document_summaries 테이블 저장)
        if (callback.getDocumentSummary() != null && job.getDocumentId() != null) {
            try {
                log.debug("Saving document summary for document: {}", job.getDocumentId());
                java.util.Map<String, Object> summaryMap = callback.getDocumentSummary();
                String summaryText = (String) summaryMap.get("summary");
                Integer level = (Integer) summaryMap.getOrDefault("level", 3);

                // 배열 변환 (List -> Array) - Null-safe
                @SuppressWarnings("unchecked")
                java.util.List<String> keyCharsList = (java.util.List<String>) summaryMap.get("key_characters");
                String[] keyChars = keyCharsList != null ? keyCharsList.toArray(new String[0]) : new String[0];

                @SuppressWarnings("unchecked")
                java.util.List<String> keyEventsList = (java.util.List<String>) summaryMap.get("key_events");
                String[] keyEvents = keyEventsList != null ? keyEventsList.toArray(new String[0]) : new String[0];

                // Ensure Project ID is available
                UUID projectId = job.getProject() != null ? job.getProject().getId() : null;
                if (projectId == null) {
                    log.error("Project ID is missing for job {}, cannot save summary", job.getJobId());
                    // Try to fetch from document if needed, but job should have it.
                }

                com.stolink.backend.domain.ai.entity.DocumentSummary summaryEntity = documentSummaryRepository
                        .findByDocumentIdAndLevel(job.getDocumentId(), level)
                        .orElse(com.stolink.backend.domain.ai.entity.DocumentSummary.builder()
                                .documentId(job.getDocumentId())
                                .projectId(projectId)
                                .level(level)
                                .build());

                summaryEntity.setSummary(summaryText);
                summaryEntity.setKeyCharacters(keyChars);
                summaryEntity.setKeyEvents(keyEvents);

                documentSummaryRepository.save(summaryEntity);
                log.info("Saved document_summaries entity for document: {}", job.getDocumentId());

            } catch (Exception e) {
                log.error("Failed to save document_summary for document {}: {}", job.getDocumentId(), e.getMessage(),
                        e);
            }
        } else {
            if (callback.getDocumentSummary() == null) {
                log.warn("Document summary is null in callback for job {}", job.getJobId());
            }
            if (job.getDocumentId() == null) {
                log.warn("Document ID is null in job match for job {}, cannot save summary", job.getJobId());
            }
        }

        // 5. Character Timelines 저장 (PostgreSQL - character_timeline 테이블)
        if (callback.getCharacterTimelines() != null && !callback.getCharacterTimelines().isEmpty()) {
            if (job.getProject() == null) {
                log.error("Project is null for job {}, cannot save character timelines", job.getJobId());
            } else {
                UUID projectId = job.getProject().getId();
                UUID documentId = job.getDocumentId();
                for (CharacterTimelineDTO timelineDTO : callback.getCharacterTimelines()) {
                    try {
                        // Upsert: 기존 데이터가 있으면 업데이트, 없으면 생성
                        CharacterTimeline timeline = characterTimelineRepository
                                .findByProjectIdAndCharacterNameAndChapter(projectId, timelineDTO.getCharacterName(),
                                        timelineDTO.getChapter())
                                .orElse(CharacterTimeline.builder()
                                        .projectId(projectId)
                                        .characterName(timelineDTO.getCharacterName())
                                        .chapter(timelineDTO.getChapter())
                                        .build());

                        timeline.setDocumentId(documentId);
                        timeline.setHealthStatus(timelineDTO.getHealthStatus());
                        timeline.setEmotionalState(timelineDTO.getEmotionalState());
                        timeline.setCurrentLocation(timelineDTO.getCurrentLocation());
                        timeline.setStateChanges(timelineDTO.getStateChanges());

                        characterTimelineRepository.save(timeline);
                        log.info("Saved character_timeline for {} chapter {}", timelineDTO.getCharacterName(),
                                timelineDTO.getChapter());
                    } catch (Exception e) {
                        log.error("Failed to save character_timeline for {}", timelineDTO.getCharacterName(), e);
                    }
                }
            }
        }

        processCallbackWithJob(adaptedCallback, job);
    }

    /**
     * Global Merge Callback
     */
    public void handleGlobalMergeCallback(GlobalMergeCallbackDTO callback) {
        log.info("Processing global merge callback, projectId: {}, status: {}", callback.getProjectId(),
                callback.getStatus());
        // Global Merge logic currently mostly handled by AI Backend (Neo4j update).
        // Log completion.
    }

    /**
     * Common processing logic with identified Job
     */
    private void processCallbackWithJob(AnalysisCallbackDTO callback, AnalysisJob job) {
        // 실패 처리
        if (callback.isFailed()) {
            log.error("Analysis failed for job {}: {}", callback.getJobId(), callback.getError());
            job.markAsFailed(callback.getError());
            analysisJobRepository.save(job);

            updateDocumentStatus(job.getDocumentId(), Document.AnalysisStatus.FAILED);
            return;
        }

        // Job에서 Project 확인
        Project projectProxy = job.getProject();
        Project project = projectRepository.findById(projectProxy.getId())
                .orElseThrow(() -> new RuntimeException("Project not found: " + projectProxy.getId()));

        // --- Character, Event, Setting, Relationship 저장 로직 제거됨 (AI Backend가 Neo4j 처리)
        // ---

        // 1. 플롯 저장 (PostgreSQL)
        PlotDTO plotData = callback.getEffectivePlot();
        if (plotData != null) {
            saveForeshadowing(plotData, project);
        }

        // 2. 일관성 보고서 저장 (PostgreSQL)
        ConsistencyReportDTO consistencyData = callback.getEffectiveConsistencyReport();
        if (consistencyData != null) {
            logConsistencyReport(consistencyData);
            saveConsistencyReport(consistencyData, project, callback.getJobId());
        }

        // 3. 검증 결과 저장 (PostgreSQL)
        ValidationDTO validationData = callback.getEffectiveValidation();
        if (validationData != null) {
            saveValidationResult(validationData, job.getDocumentId(), callback.getJobId());
        }

        // Job 완료 처리
        Long processingTimeMs = callback.getEffectiveProcessingTimeMs();
        transactionTemplate.execute(status -> {
            analysisJobRepository.findById(callback.getJobId()).ifPresent(j -> {
                j.markAsCompleted(processingTimeMs);
                analysisJobRepository.save(j);
            });
            return null;
        });

        // Document 상태 업데이트 (COMPLETED)
        updateDocumentStatus(job.getDocumentId(), Document.AnalysisStatus.COMPLETED);

        // 콜백 처리 로그 저장
        callbackLogRepository.save(CallbackLog.builder()
                .jobId(callback.getJobId())
                .messageType("DOCUMENT_ANALYSIS")
                .status(callback.getStatus())
                .processedAt(java.time.LocalDateTime.now())
                .projectId(project.getId())
                .build());

        // SSE 알림 전송
        sseEmitterService.sendStatus(job.getProject().getId(), new SseEmitterService.AnalysisStatusEvent(
                "COMPLETED", 1, 1, "분석이 완료되었습니다."));

        log.info("Analysis callback processed successfully for job: {}", callback.getJobId());
    }

    private void updateDocumentStatus(UUID documentId, Document.AnalysisStatus status) {
        if (documentId == null)
            return;
        try {
            transactionTemplate.execute(txStatus -> {
                documentRepository.findById(documentId).ifPresent(doc -> {
                    doc.updateAnalysisStatus(status);
                    documentRepository.save(doc);
                    log.info("Updated Document {} status to {}", documentId, status);
                });
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to update document status: {}", e.getMessage());
        }
    }

    private void logConsistencyReport(ConsistencyReportDTO consistencyReport) {
        if (consistencyReport != null) {
            Integer score = consistencyReport.getEffectiveScore();
            log.info("Consistency report - score: {}", score);
        }
    }

    @Transactional
    public void handleImageCallback(ImageCallbackDTO callback) {
        String jobId = callback.getJobId();
        log.info("Processing image callback for job: {}, character: {}",
                jobId, callback.getCharacterId());

        // Null check for jobId
        if (jobId == null || jobId.isBlank()) {
            log.error("Job ID is missing in image callback. Callback dump: {}", callback);
            return;
        }

        // Save raw callback data to file
        saveCallbackToJsonFile("image", jobId, callback);

        String tempImageUrl = callback.getImageUrl();
        if (tempImageUrl != null) {
            if (tempImageUrl.contains("minio:9000")) {
                tempImageUrl = tempImageUrl.replace("minio:9000", "localhost:9000");
            }
            // [HotFix] Docker 환경 MinIO URL 보정 (전역 적용)
            if (tempImageUrl.startsWith("http://localhost:9000/media/")) {
                String fixedUrl = tempImageUrl.replace("http://localhost:9000/media/",
                        "http://localhost:9000/stolink-test/media/");
                log.warn("Patching MinIO URL in Callback: {} -> {}", tempImageUrl, fixedUrl);
                tempImageUrl = fixedUrl;
            }
        }
        String imageUrl = tempImageUrl;

        if ("FAILED".equals(callback.getStatus())) {
            log.error("Image generation failed: {}", callback.getErrorMessage());
            final String errorMsg = callback.getErrorMessage();
            imageGenerationTaskRepository.findById(jobId).ifPresent(task -> {
                task.markAsFailed(errorMsg);
                imageGenerationTaskRepository.save(task);
            });
            return;
        }

        // Update Character Entity (Postgres & Neo4j via Service)
        if (callback.getCharacterId() != null) {
            try {
                characterService.updateCharacterImageUrl(callback.getCharacterId(), imageUrl);
            } catch (Exception e) {
                log.error("Failed to update character image URL: {}", e.getMessage());
            }
        }

        imageGenerationTaskRepository.findById(jobId).ifPresent(task -> {
            task.setImageUrl(imageUrl);
            task.setStatus(ImageGenerationTask.TaskStatus.COMPLETED);
            imageGenerationTaskRepository.save(task);
            log.info("Updated ImageGenerationTask {} to COMPLETED", jobId);
        });
    }

    private void saveConsistencyReport(ConsistencyReportDTO reportData, Project project, String jobId) {
        if (reportData == null)
            return;
        try {
            ConsistencyReport report = ConsistencyReport.builder()
                    .project(project)
                    .jobId(jobId)
                    .overallScore(reportData.getEffectiveScore())
                    .requiresReextraction(
                            reportData.getRequiresReExtraction() != null ? reportData.getRequiresReExtraction() : false)
                    .conflictsJson(toJson(reportData.getConflicts()))
                    .warningsJson(toJson(reportData.getWarnings()))
                    .resolutionSummaryJson(toJson(reportData.getResolutionSummary()))
                    .neo4jValidationJson(toJson(reportData.getNeo4jValidation()))
                    .build();
            consistencyReportRepository.save(report);
        } catch (Exception e) {
            log.error("Failed to save consistency report: {}", e.getMessage());
        }
    }

    private void saveValidationResult(ValidationDTO validationData, UUID documentId, String jobId) {
        if (validationData == null)
            return;
        try {
            ValidationResult validation = ValidationResult.builder()
                    .documentId(documentId)
                    .jobId(jobId)
                    .isValid(validationData.getIsValid() != null ? validationData.getIsValid() : true)
                    .qualityScore(validationData.getQualityScore())
                    .action(validationData.getAction())
                    .actionDescription(validationData.getActionDescription())
                    .averageCompleteness(validationData.getAverageCompleteness())
                    .errorCount(validationData.getErrorCount() != null ? validationData.getErrorCount() : 0)
                    .warningCount(validationData.getWarningCount() != null ? validationData.getWarningCount() : 0)
                    .executionTimeMs(validationData.getExecutionTimeMs())
                    .dataCompletenessJson(toJson(validationData.getDataCompleteness()))
                    .validationDetailsJson(toJson(validationData.getValidationDetails()))
                    .build();
            validationResultRepository.save(validation);
        } catch (Exception e) {
            log.error("Failed to save validation result for document {}: {}", documentId, e.getMessage(), e);
        }
    }

    private void saveForeshadowing(PlotDTO plotData, Project project) {
        if (plotData == null || plotData.getForeshadowing() == null)
            return;
        for (PlotDTO.ForeshadowingItemDTO fsData : plotData.getForeshadowing()) {
            if (fsData.getForeshadowId() == null)
                continue;
            try {
                // Not removing ForeshadowingRepository as it wasn't requested. Assuming
                // Foreshadowing entity is distinct.
                // Assuming ForeshadowingRepository is JPA.
                java.util.Optional<Foreshadowing> existingFs = foreshadowingRepository.findByProjectAndTag(project,
                        fsData.getForeshadowId());
                Foreshadowing foreshadowing;
                if (existingFs.isPresent()) {
                    foreshadowing = existingFs.get();
                    foreshadowing.update(fsData.getHintText(),
                            fsData.getConfidence() != null && fsData.getConfidence() >= 7
                                    ? Foreshadowing.Importance.MAJOR
                                    : Foreshadowing.Importance.MINOR);
                } else {
                    foreshadowing = Foreshadowing.builder()
                            .project(project)
                            .tag(fsData.getForeshadowId())
                            .description(fsData.getHintText()
                                    + (fsData.getPredictedOutcome() != null ? " -> " + fsData.getPredictedOutcome()
                                            : ""))
                            .importance(fsData.getConfidence() != null && fsData.getConfidence() >= 7
                                    ? Foreshadowing.Importance.MAJOR
                                    : Foreshadowing.Importance.MINOR)
                            .build();
                }
                foreshadowingRepository.save(foreshadowing);
            } catch (Exception e) {
                log.error("Failed to save foreshadowing", e);
            }
        }
    }

    private String toJson(Object obj) {
        if (obj == null)
            return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * AI 콜백 데이터를 로컬 JSON 파일로 저장 (디버깅용)
     * Docker 환경에서 안전하게 동작하도록 /tmp 디렉토리 사용
     */
    private void saveCallbackToJsonFile(String type, String id, Object data) {
        // Null check for required parameters
        if (type == null || data == null) {
            log.warn("Cannot save callback to file: type or data is null");
            return;
        }

        // Async execution to avoid I/O blocking
        CompletableFuture.runAsync(() -> {
            try {
                String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                        .format(java.time.LocalDateTime.now());
                // Sanitize ID for filename (null-safe)
                String safeId = (id != null && !id.isBlank())
                        ? id.replaceAll("[^a-zA-Z0-9-_]", "_")
                        : "unknown";
                // Use /tmp directory for Docker compatibility
                String fileName = String.format("/tmp/ai-callbacks/%s_%s_%s.json", type, safeId, timestamp);

                java.io.File file = new java.io.File(fileName);
                java.io.File parentDir = file.getParentFile();

                // Ensure directory exists with explicit error handling
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    if (!created && !parentDir.exists()) {
                        log.warn("Failed to create directory: {}", parentDir.getAbsolutePath());
                        return;
                    }
                }

                objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
                log.info("Saved callback data to file (Async): {}", fileName);
            } catch (Exception e) {
                log.error("Failed to save callback data to file: {}", e.getMessage());
            }
        });
    }

    // ===================================
    // SSE Progress Update Helper
    // ===================================
    public void sendProgressUpdate(UUID projectId) {
        if (projectId == null)
            return;

        try {
            long total = documentRepository.countTextDocumentsByProjectId(projectId);
            long completed = documentRepository.countByProjectIdAndTypeTextAndAnalysisStatus(
                    projectId,
                    com.stolink.backend.domain.document.entity.Document.AnalysisStatus.COMPLETED);

            String status = (total > 0 && total <= completed) ? "COMPLETED" : "ANALYZING";
            String message = (total > 0 && total <= completed)
                    ? "분석이 완료되었습니다."
                    : String.format("분석 진행 중: %d/%d", completed, total);

            sseEmitterService.sendStatus(projectId, new SseEmitterService.AnalysisStatusEvent(
                    status, (int) completed, (int) total, message));
            log.debug("Sent SSE progress update for project {}: {}/{}", projectId, completed, total);
        } catch (Exception e) {
            log.warn("Failed to send SSE progress update: {}", e.getMessage());
        }
    }
}
