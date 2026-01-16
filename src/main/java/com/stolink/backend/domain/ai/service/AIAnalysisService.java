package com.stolink.backend.domain.ai.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stolink.backend.domain.ai.dto.AnalysisContext;
import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
import com.stolink.backend.domain.ai.dto.GlobalMergeRequestDTO;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.consistency.repository.ConsistencyReportRepository;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.foreshadowing.repository.ForeshadowingRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.validation.repository.ValidationResultRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import com.stolink.backend.global.sse.SseEmitterService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 분석 서비스
 *
 * 프로젝트/문서별 분석 요청 발행 및 자동 병합 트리거를 담당합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIAnalysisService {

    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;
    private final RabbitMQProducerService producerService;
    private final SseEmitterService sseEmitterService;

    // Repositories for data cleanup
    private final AnalysisJobRepository analysisJobRepository;

    // PlotIntegrationRepository removed
    private final ConsistencyReportRepository consistencyReportRepository;
    private final ValidationResultRepository validationResultRepository;
    private final ForeshadowingRepository foreshadowingRepository;
    // CharacterJpaRepository removed

    @Value("${app.ai.callback-base-url}")
    private String callbackBaseUrl;

    /**
     * 프로젝트의 현재 분석 상태를 조회합니다.
     * SSE 연결 초기화 시 클라이언트에게 현재 상태를 전달하기 위해 사용됩니다.
     * 
     * NOTE: @Transactional을 사용하지 않음 - SSE 엔드포인트에서 호출되어
     * 트랜잭션이 오래 유지되면 Connection Leak 경고가 발생할 수 있음.
     * 읽기 전용 COUNT 쿼리들이므로 트랜잭션 없이도 안전함.
     */
    public SseEmitterService.AnalysisStatusEvent getAnalysisStatus(UUID projectId) {
        long totalTextDocs = documentRepository.countTextDocumentsByProjectId(projectId);
        if (totalTextDocs == 0) {
            return new SseEmitterService.AnalysisStatusEvent("NONE", 0, 0, "분석할 문서가 없습니다.");
        }

        long completedDocs = documentRepository.countByProjectIdAndTypeTextAndAnalysisStatus(
                projectId, Document.AnalysisStatus.COMPLETED);

        long failedDocs = documentRepository.countByProjectIdAndTypeTextAndAnalysisStatus(
                projectId, Document.AnalysisStatus.FAILED);

        long processingDocs = documentRepository.countByProjectIdAndTypeTextAndAnalysisStatus(
                projectId, Document.AnalysisStatus.PROCESSING);
        long queuedDocs = documentRepository.countByProjectIdAndTypeTextAndAnalysisStatus(
                projectId, Document.AnalysisStatus.QUEUED);

        String status;
        String message;

        if (completedDocs == totalTextDocs) {
            status = "COMPLETED";
            message = "분석이 완료되었습니다.";
        } else if (failedDocs > 0) {
            status = "FAILED";
            message = "일부 문서 분석에 실패했습니다.";
        } else if (processingDocs > 0 || queuedDocs > 0) {
            status = "ANALYZING";
            message = String.format("분석 진행 중: %d/%d 챕터", completedDocs, totalTextDocs);
        } else {
            status = "NONE";
            message = "분석 대기 중";
        }

        return new SseEmitterService.AnalysisStatusEvent(status, (int) completedDocs, (int) totalTextDocs, message);
    }

    /**
     * 프로젝트 분석 상태를 강제로 초기화(리셋)합니다.
     * 멈춘 분석 작업을 취소할 때 사용합니다.
     * 또한 프로젝트와 연관된 모든 AI 분석 결과 데이터(DB)를 삭제합니다.
     */
    @Transactional
    public void resetProjectAnalysis(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        // 1. PostgreSQL 데이터 삭제
        analysisJobRepository.deleteAllByProject(project);
        // plotIntegrationRepository.deleteAllByProject(project); // Removed
        consistencyReportRepository.deleteAllByProject(project);
        // validationResultRepository.deleteAllByProject(project); // Removed:
        // document_id reference
        foreshadowingRepository.deleteAllByProject(project);
        // characterJpaRepository.deleteAllByProject(project); // Removed

        // 2. Neo4j 데이터 삭제 - AI Backend에서 처리 (제거됨)
        log.debug("Neo4j cleanup skipped - handled by AI Backend");

        // 3. 문서 상태 초기화 및 관련 검증 결과 삭제
        List<Document> documents = documentRepository.findTextDocumentsByProjectId(projectId);
        for (Document doc : documents) {
            doc.updateAnalysisStatus(Document.AnalysisStatus.NONE);
            validationResultRepository.deleteAllByDocumentId(doc.getId());
        }
        documentRepository.saveAll(documents);

        // SSE로 상태 초기화 알림 전송
        sseEmitterService.sendStatus(projectId, new SseEmitterService.AnalysisStatusEvent(
                "NONE", 0, documents.size(), "분석이 초기화되었습니다."));
        log.info("Project analysis reset and data deleted for projectId: {}", projectId);
    }

    /**
     * 프로젝트의 모든 TEXT 문서에 대해 분석 요청을 발행합니다.
     *
     * @param projectId 분석할 프로젝트 ID
     * @return 발행된 분석 요청 수
     */
    @Transactional
    public int triggerProjectAnalysis(UUID projectId) {
        List<Document> textDocuments = documentRepository.findTextDocumentsByProjectId(projectId);

        if (textDocuments.isEmpty()) {
            log.warn("No TEXT documents found for project: {}", projectId);
            return 0;
        }

        if (textDocuments.isEmpty()) {
            log.warn("No TEXT documents found for project: {}", projectId);
            return 0;
        }

        int totalChapters = textDocuments.size();
        List<AnalysisTaskDTO> batchTasks = new ArrayList<>();

        for (int i = 0; i < totalChapters; i++) {
            Document doc = textDocuments.get(i);

            // 이미 분석 완료된 문서는 스킵
            if (doc.getAnalysisStatus() == Document.AnalysisStatus.COMPLETED) {
                log.debug("Skipping already completed document: {}", doc.getId());
                continue;
            }

            // Batch Task 생성
            AnalysisTaskDTO task = createAnalysisTask(doc, i + 1, totalChapters);
            batchTasks.add(task);

            // 상태 업데이트 (DB)
            doc.updateAnalysisStatus(Document.AnalysisStatus.QUEUED);
        }

        // DB 일괄 업데이트
        documentRepository.saveAll(textDocuments);

        // RabbitMQ 배치 발행
        int publishedCount = producerService.sendAnalysisTaskBatch(batchTasks);

        log.info("Project analysis triggered: projectId={}, published={}/{} documents",
                projectId, publishedCount, totalChapters);
        return publishedCount;
    }

    /**
     * 내부 메서드: 문서 분석 요청 생성 (발행하지 않음)
     */
    private AnalysisTaskDTO createAnalysisTask(Document doc, int chapterNumber, int totalChapters) {
        String jobId = UUID.randomUUID().toString();
        String traceId = generateTraceId();

        // AnalysisJob 생성 및 저장 (콜백 수신을 위해 필수)
        AnalysisJob analysisJob = AnalysisJob.builder()
                .jobId(jobId)
                .project(doc.getProject())
                .documentId(doc.getId())
                .status(AnalysisJob.JobStatus.PROCESSING) // RabbitMQ로 바로 전송되므로 PROCESSING
                .traceId(traceId)
                .startedAt(java.time.LocalDateTime.now())
                .build();
        analysisJobRepository.save(analysisJob);
        log.info("Created AnalysisJob: {}", jobId);

        // Context 생성
        AnalysisContext context = AnalysisContext.builder()
                .chapterNumber(chapterNumber)
                .totalChapters(totalChapters)
                .build();

        // DTO 생성
        return AnalysisTaskDTO.builder()
                .jobId(jobId)
                .projectId(doc.getProject().getId())
                .documentId(doc.getId())
                .content(doc.getContent())
                .callbackUrl(callbackBaseUrl + "/api/internal/ai/analysis/callback")
                .traceId(traceId)
                .requiresDeepAnalysis(true)
                .context(context)
                .build();
    }

    /**
     * 단일 문서에 대해 분석 요청을 발행합니다.
     *
     * @param documentId 분석할 문서 ID
     */
    @Transactional
    public void triggerDocumentAnalysis(UUID documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        long totalChapters = documentRepository.countTextDocumentsByProjectId(doc.getProject().getId());
        triggerDocumentAnalysis(doc, 1, (int) totalChapters);
    }

    /**
     * 내부 메서드: 문서 분석 요청 발행
     */
    private void triggerDocumentAnalysis(Document doc, int chapterNumber, int totalChapters) {
        AnalysisTaskDTO task = createAnalysisTask(doc, chapterNumber, totalChapters);

        // 상태 업데이트
        doc.updateAnalysisStatus(Document.AnalysisStatus.QUEUED);
        documentRepository.save(doc);

        // 메시지 발행 (단건)
        producerService.sendAnalysisTask(task);

        log.info("Document analysis triggered: documentId={}, jobId={}, chapter={}/{}",
                doc.getId(), task.getJobId(), chapterNumber, totalChapters);
        System.out.println(
                "DEBUG_LOG: AIAnalysisService trigger - requiresDeepAnalysis=" + task.isRequiresDeepAnalysis());
    }

    /**
     * 프로젝트의 모든 문서 분석이 완료되었는지 확인하고, 완료 시 Global Merge를 트리거합니다.
     *
     * @param projectId 확인할 프로젝트 ID
     * @return Global Merge가 트리거되었는지 여부
     */
    @Transactional
    public boolean checkAndTriggerGlobalMerge(UUID projectId) {
        long totalTextDocs = documentRepository.countTextDocumentsByProjectId(projectId);
        long completedDocs = documentRepository.countByProjectIdAndTypeTextAndAnalysisStatus(
                projectId, Document.AnalysisStatus.COMPLETED);

        log.debug("Checking merge trigger for project {}: {}/{} completed",
                projectId, completedDocs, totalTextDocs);

        // 모든 문서가 완료되었을 때만 트리거
        if (totalTextDocs > 0 && completedDocs == totalTextDocs) {
            log.info("All documents completed for project {}. Triggering Global Merge.", projectId);
            triggerGlobalMerge(projectId);
            return true;
        }

        return false;
    }

    /**
     * Global Merge 요청을 발행합니다.
     *
     * @param projectId 병합할 프로젝트 ID
     */
    public void triggerGlobalMerge(UUID projectId) {
        String traceId = generateTraceId();

        GlobalMergeRequestDTO request = GlobalMergeRequestDTO.builder()
                .projectId(projectId)
                .callbackUrl(callbackBaseUrl + "/api/internal/ai/analysis/callback")
                .traceId(traceId)
                .build();

        producerService.sendGlobalMergeRequest(request);
        log.info("Global merge triggered: projectId={}, traceId={}", projectId, traceId);
    }

    /**
     * 프로젝트의 최신 일관성 분석 보고서를 조회합니다.
     */
    @Transactional(readOnly = true)
    public com.stolink.backend.domain.project.dto.ConsistencyReportResponse getLatestConsistencyReport(UUID projectId) {
        com.stolink.backend.domain.consistency.entity.ConsistencyReport report = consistencyReportRepository
                .findFirstByProjectIdOrderByCreatedAtDesc(projectId)
                .orElse(null);

        if (report == null) {
            return null;
        }

        List<com.stolink.backend.domain.project.dto.ConsistencyReportResponse.Conflict> conflicts = new ArrayList<>();
        java.util.Map<String, Object> resolutionSummary = null;

        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        try {
            if (report.getConflictsJson() != null && !report.getConflictsJson().isEmpty()) {
                List<java.util.Map<String, Object>> conflictsMapList = objectMapper.readValue(report.getConflictsJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {
                        });
                for (java.util.Map<String, Object> map : conflictsMapList) {
                    com.stolink.backend.domain.project.dto.ConsistencyReportResponse.Location location = null;
                    if (map.containsKey("location")) {
                        location = objectMapper.convertValue(map.get("location"),
                                com.stolink.backend.domain.project.dto.ConsistencyReportResponse.Location.class);
                    }

                    conflicts.add(com.stolink.backend.domain.project.dto.ConsistencyReportResponse.Conflict.builder()
                            .type((String) map.get("type"))
                            .severity((String) map.get("severity"))
                            .description((String) map.get("description"))
                            .suggestion((String) map.get("suggestion"))
                            .suggestedAction((String) map.get("suggested_action"))
                            .location(location)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse conflicts json for report {}: {}", report.getId(), e.getMessage());
        }

        try {
            if (report.getResolutionSummaryJson() != null && !report.getResolutionSummaryJson().isEmpty()) {
                resolutionSummary = objectMapper.readValue(report.getResolutionSummaryJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<>() {
                        });
            } else {
                // Fallback stats if summary json is missing
                resolutionSummary = new java.util.HashMap<>();
                resolutionSummary.put("high_severity_count", report.getHighSeverityCount());
                resolutionSummary.put("medium_severity_count", report.getMediumSeverityCount());
                resolutionSummary.put("auto_fixable_count", report.getAutoFixableCount());
                resolutionSummary.put("requires_human_review_count", report.getRequiresHumanReviewCount());
            }
        } catch (Exception e) {
            log.warn("Failed to parse resolution summary json for report {}: {}", report.getId(), e.getMessage());
        }

        return com.stolink.backend.domain.project.dto.ConsistencyReportResponse.builder()
                .jobId(report.getJobId())
                .createdAt(report.getCreatedAt())
                .score(report.getOverallScore())
                .overallScore(report.getOverallScore())
                .requiresHumanReview(
                        report.getRequiresHumanReviewCount() != null && report.getRequiresHumanReviewCount() > 0)
                .conflicts(conflicts)
                .resolutionSummary(resolutionSummary)
                .build();
    }

    /**
     * Trace ID 생성 (분산 추적용)
     */
    private String generateTraceId() {
        return String.format("trace-%s-%s",
                LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE),
                UUID.randomUUID().toString().substring(0, 8));
    }
}
