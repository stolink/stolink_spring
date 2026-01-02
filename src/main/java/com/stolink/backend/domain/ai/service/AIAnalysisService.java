package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.ai.dto.AnalysisContext;
import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
import com.stolink.backend.domain.ai.dto.GlobalMergeRequestDTO;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

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
    private final RabbitMQProducerService producerService;

    @Value("${app.ai.callback-base-url}")
    private String callbackBaseUrl;

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

        int totalChapters = textDocuments.size();
        int publishedCount = 0;

        for (int i = 0; i < totalChapters; i++) {
            Document doc = textDocuments.get(i);

            // 이미 분석 완료된 문서는 스킵
            if (doc.getAnalysisStatus() == Document.AnalysisStatus.COMPLETED) {
                log.debug("Skipping already completed document: {}", doc.getId());
                continue;
            }

            triggerDocumentAnalysis(doc, i + 1, totalChapters);
            publishedCount++;
        }

        log.info("Project analysis triggered: projectId={}, published={}/{} documents",
                projectId, publishedCount, totalChapters);
        return publishedCount;
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
        String jobId = UUID.randomUUID().toString();
        String traceId = generateTraceId();

        // Context 생성
        AnalysisContext context = AnalysisContext.builder()
                .chapterNumber(chapterNumber)
                .totalChapters(totalChapters)
                .build();

        // DTO 생성
        AnalysisTaskDTO task = AnalysisTaskDTO.builder()
                .jobId(jobId)
                .projectId(doc.getProject().getId())
                .documentId(doc.getId())
                .content(doc.getContent())
                .callbackUrl(callbackBaseUrl + "/ai-callback")
                .traceId(traceId)
                .context(context)
                .build();

        // 상태 업데이트
        doc.updateAnalysisStatus(Document.AnalysisStatus.QUEUED);
        documentRepository.save(doc);

        // 메시지 발행
        producerService.sendAnalysisTask(task);

        log.info("Document analysis triggered: documentId={}, jobId={}, chapter={}/{}",
                doc.getId(), jobId, chapterNumber, totalChapters);
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
                .callbackUrl(callbackBaseUrl + "/ai-callback")
                .traceId(traceId)
                .build();

        producerService.sendGlobalMergeRequest(request);
        log.info("Global merge triggered: projectId={}, traceId={}", projectId, traceId);
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
