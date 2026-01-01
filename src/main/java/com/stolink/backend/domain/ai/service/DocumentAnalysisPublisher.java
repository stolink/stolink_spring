package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.ai.dto.DocumentAnalysisMessage;
import com.stolink.backend.domain.ai.dto.GlobalMergeMessage;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.entity.Document.AnalysisStatus;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 문서 분석 메시지 발행 서비스
 * 
 * 대용량 문서 분석을 위한 RabbitMQ 메시지 발행을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAnalysisPublisher {

    private final DocumentRepository documentRepository;

    @Qualifier("agentRabbitTemplate")
    private final RabbitTemplate agentRabbitTemplate;

    @Value("${app.rabbitmq.queues.document-analysis:document_analysis_queue}")
    private String documentAnalysisQueue;

    @Value("${app.rabbitmq.queues.global-merge:global_merge_queue}")
    private String globalMergeQueue;

    @Value("${app.callback.base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    /**
     * 프로젝트 내 모든 TEXT 문서에 대해 분석 요청 발행
     * 
     * @param projectId 프로젝트 ID
     * @return 발행된 메시지 수
     */
    @Transactional
    public int publishAnalysisForProject(UUID projectId) {
        List<Document> textDocuments = documentRepository.findTextDocumentsByProjectId(projectId);

        if (textDocuments.isEmpty()) {
            log.warn("프로젝트 {}에 분석할 TEXT 문서가 없습니다.", projectId);
            return 0;
        }

        int totalDocuments = textDocuments.size();
        log.info("프로젝트 {} - {}개 문서 분석 요청 시작", projectId, totalDocuments);

        long startTime = System.currentTimeMillis();

        for (Document doc : textDocuments) {
            // 상태를 PENDING으로 업데이트
            doc.updateAnalysisStatus(AnalysisStatus.PENDING);
            documentRepository.save(doc);

            // 메시지 생성 및 발행
            DocumentAnalysisMessage message = buildMessage(doc, projectId, totalDocuments);
            agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message);

            // 상태를 QUEUED로 업데이트
            doc.updateAnalysisStatus(AnalysisStatus.QUEUED);
            documentRepository.save(doc);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("프로젝트 {} - {}개 메시지 발행 완료 ({}ms)", projectId, totalDocuments, duration);

        return totalDocuments;
    }

    /**
     * 단일 문서 분석 요청 발행
     */
    @Transactional
    public void publishAnalysisForDocument(Document document) {
        document.updateAnalysisStatus(AnalysisStatus.PENDING);
        documentRepository.save(document);

        DocumentAnalysisMessage message = buildMessage(
                document,
                document.getProject().getId(),
                1);

        agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message);

        document.updateAnalysisStatus(AnalysisStatus.QUEUED);
        documentRepository.save(document);

        log.info("문서 {} 분석 요청 발행 완료", document.getId());
    }

    /**
     * 글로벌 병합 (2차 Pass) 요청 발행
     */
    public void publishGlobalMerge(UUID projectId, String traceId) {
        GlobalMergeMessage message = GlobalMergeMessage.builder()
                .projectId(projectId.toString())
                .callbackUrl(callbackBaseUrl + "/api/ai-callback")
                .traceId(traceId)
                .build();

        agentRabbitTemplate.convertAndSend(globalMergeQueue, message);
        log.info("프로젝트 {} 글로벌 병합 요청 발행 완료", projectId);
    }

    /**
     * 분석 메시지 생성
     */
    private DocumentAnalysisMessage buildMessage(Document document, UUID projectId, int totalDocuments) {
        Document parent = document.getParent();
        String parentFolderId = parent != null ? parent.getId().toString() : null;
        String chapterTitle = parent != null ? parent.getTitle() : document.getTitle();

        return DocumentAnalysisMessage.builder()
                .documentId(document.getId().toString())
                .projectId(projectId.toString())
                .parentFolderId(parentFolderId)
                .chapterTitle(chapterTitle)
                .documentOrder(document.getOrder())
                .totalDocumentsInChapter(totalDocuments)
                .analysisPass(1)
                .callbackUrl(callbackBaseUrl + "/api/ai-callback")
                .context(DocumentAnalysisMessage.AnalysisContext.builder()
                        .existingCharacters(List.of()) // 1차 Pass는 빈 배열
                        .existingEvents(List.of())
                        .existingRelationships(List.of())
                        .existingSettings(List.of())
                        .build())
                .traceId(UUID.randomUUID().toString())
                .build();
    }
}
