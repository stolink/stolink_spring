package com.stolink.backend.domain.ai.service;

import java.util.UUID;

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

        log.info("Processing analysis callback for job: {}, status: {}",
                callback.getJobId(), callback.getStatus());

        // Idempotent check
        if (callback.getJobId() != null && callbackLogRepository.existsByJobId(callback.getJobId())) {
            log.warn("Duplicate callback ignored: {}", callback.getJobId());
            return;
        }

        // Job 조회
        AnalysisJob job = analysisJobRepository.findByJobId(callback.getJobId()).orElse(null);

        // [TEMPORARY] Dummy Data Injection Logic
        if (job == null && callback.getJobId().startsWith("dummy")) {
            log.warn("Job not found, but detecting dummy data injection. Creating context for insertion...");
            try {
                UUID tempProjectId = null;
                String[] jobIdParts = callback.getJobId().split("::");
                if (jobIdParts.length >= 2) {
                    try {
                        tempProjectId = UUID.fromString(jobIdParts[1]);
                    } catch (IllegalArgumentException e) {
                        tempProjectId = UUID.fromString("e2a08b38-9049-4647-b9f0-1cac7792a2d7");
                    }
                } else {
                    tempProjectId = UUID.fromString("e2a08b38-9049-4647-b9f0-1cac7792a2d7");
                }

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

                // Cleanup existing characters and relationships for this project
                transactionTemplate.execute(status -> {
                    relationshipRepository.deleteByProjectId(finalTargetProjectId);
                    characterJpaRepository.deleteAllByProject(project);
                    characterRepository.deleteByProjectId(finalTargetProjectId.toString());
                    log.info("Cleaned up existing data for project: {}", finalTargetProjectId);
                    return null;
                });
            } catch (Exception e) {
                log.error("Failed to create dummy context or cleanup: {}", e.getMessage());
            }
        }

        if (job == null) {
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
        log.info("Processing image callback for job: {}, character: {}",
                callback.getJobId(), callback.getCharacterId());

        // Save raw callback data to file
        saveCallbackToJsonFile("image", callback.getJobId(), callback);

        String jobId = callback.getJobId();
        String tempImageUrl = callback.getImageUrl();
        if (tempImageUrl != null && tempImageUrl.contains("minio:9000")) {
            tempImageUrl = tempImageUrl.replace("minio:9000", "localhost:9000");
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

        // Character Update (JPA) removed - assuming handled externally or not needed in
        // Postgres.

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
                            reportData.getRequiresReextraction() != null ? reportData.getRequiresReextraction() : false)
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
     */
    private void saveCallbackToJsonFile(String type, String id, Object data) {
        // Async execution to avoid I/O blocking
        CompletableFuture.runAsync(() -> {
            try {
                String timestamp = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                        .format(java.time.LocalDateTime.now());
                // Sanitize ID for filename
                String safeId = id != null ? id.replaceAll("[^a-zA-Z0-9-_]", "_") : "unknown";
                String fileName = String.format("logs/ai-callbacks/%s_%s_%s.json", type, safeId, timestamp);

                java.io.File file = new java.io.File(fileName);
                file.getParentFile().mkdirs();

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
    private void sendProgressUpdate(UUID projectId) {
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
