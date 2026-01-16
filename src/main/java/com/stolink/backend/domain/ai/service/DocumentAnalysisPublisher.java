package com.stolink.backend.domain.ai.service;

import java.util.List;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stolink.backend.domain.ai.dto.BatchRetryRequest;
import com.stolink.backend.domain.ai.dto.BatchRetryResponse;
import com.stolink.backend.domain.ai.dto.DocumentAnalysisMessage;
import com.stolink.backend.domain.ai.dto.GlobalMergeMessage;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.entity.Document.AnalysisStatus;
import com.stolink.backend.domain.document.repository.DocumentRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 문서 분석 메시지 발행 서비스
 *
 * 대용량 문서 분석을 위한 RabbitMQ 메시지 발행을 담당합니다.
 */
@Service
@Slf4j
public class DocumentAnalysisPublisher {

    private final DocumentRepository documentRepository;
    private final AnalysisJobRepository analysisJobRepository;
    private final RabbitTemplate agentRabbitTemplate;

    public DocumentAnalysisPublisher(
            DocumentRepository documentRepository,
            AnalysisJobRepository analysisJobRepository,
            @Qualifier("agentRabbitTemplate") RabbitTemplate agentRabbitTemplate) {
        this.documentRepository = documentRepository;
        this.analysisJobRepository = analysisJobRepository;
        this.agentRabbitTemplate = agentRabbitTemplate;
    }

    @Value("${app.rabbitmq.queues.document-analysis:document_analysis_queue}")
    private String documentAnalysisQueue;

    @Value("${app.rabbitmq.queues.global-merge:global_merge_queue}")
    private String globalMergeQueue;

    @Value("${app.ai.callback-base-url:http://stolink-backend:8080/api/internal/ai/analysis/callback}")
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

            // AnalysisJob 생성 및 저장
            String jobId = UUID.randomUUID().toString();
            String traceId = UUID.randomUUID().toString(); // traceId 생성

            AnalysisJob analysisJob = AnalysisJob.builder()
                    .jobId(jobId)
                    .project(doc.getProject())
                    .documentId(doc.getId()) // documentId 저장
                    .traceId(traceId) // ✅ traceId 설정
                    .status(AnalysisJob.JobStatus.PENDING)
                    .build();
            analysisJobRepository.save(analysisJob);

            // 메시지 생성 및 발행 (우선순위: 1 - 낮음)
            DocumentAnalysisMessage message = buildMessage(doc, projectId, totalDocuments, "full_manuscript", jobId,
                    traceId); // ✅ traceId 전달
            agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message, m -> {
                m.getMessageProperties().setPriority(1);
                return m;
            });

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
     *
     * @return 발행된 작업의 JobId
     */
    @Transactional
    public String publishAnalysisForDocument(Document document, String analysisType) {
        document.updateAnalysisStatus(AnalysisStatus.PENDING);
        documentRepository.save(document);

        String jobId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString(); // traceId 생성

        AnalysisJob analysisJob = AnalysisJob.builder()
                .jobId(jobId)
                .project(document.getProject())
                .documentId(document.getId())
                .traceId(traceId) // ✅ traceId 설정
                .status(AnalysisJob.JobStatus.PENDING)
                .build();
        analysisJobRepository.save(analysisJob);

        DocumentAnalysisMessage message = buildMessage(
                document,
                document.getProject().getId(),
                1,
                analysisType,
                jobId,
                traceId); // ✅ traceId 전달

        // 작가가 직접 요청한 경우 심화 분석(복선 등) 수행
        message.setRequiresDeepAnalysis(true);

        // 우선순위: 10 (높음 - 작가 요청)
        agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message, m -> {
            m.getMessageProperties().setPriority(10);
            return m;
        });

        document.updateAnalysisStatus(AnalysisStatus.QUEUED);
        documentRepository.save(document);

        log.info("문서 {} 분석 요청 발행 완료: jobId={}", document.getId(), jobId);

        return jobId;
    }

    /**
     * 글로벌 병합 (2차 Pass) 요청 발행
     */
    public void publishGlobalMerge(UUID projectId, String traceId) {
        GlobalMergeMessage message = GlobalMergeMessage.builder()
                .projectId(projectId.toString())
                .callbackUrl(callbackBaseUrl + "/api/internal/ai/analysis/callback")
                .traceId(traceId)
                .build();

        agentRabbitTemplate.convertAndSend(globalMergeQueue, message);
        log.info("프로젝트 {} 글로벌 병합 요청 발행 완료", projectId);
    }

    /**
     * 분석 메시지 생성
     */
    private DocumentAnalysisMessage buildMessage(Document document, UUID projectId, int totalDocuments,
            String analysisType, String jobId, String traceId) { // ✅ traceId 파라미터 추가
        Document parent = document.getParent();
        String parentFolderId = parent != null ? parent.getId().toString() : null;
        String chapterTitle = parent != null ? parent.getTitle() : document.getTitle();

        return DocumentAnalysisMessage.builder()
                .jobId(jobId)
                .documentId(document.getId().toString())

                .projectId(projectId.toString())
                .parentFolderId(parentFolderId)
                .chapterTitle(chapterTitle)
                .documentOrder(document.getOrder())
                .totalDocumentsInChapter(totalDocuments)
                .analysisPass(1)
                .requiresDeepAnalysis(true) // 무조건 심화 분석 수행
                .callbackUrl(callbackBaseUrl + "/api/internal/ai/analysis/callback")
                .content(document.getContent()) // ✅ 문서 내용 추가
                .analysisType(analysisType != null ? analysisType : "full_manuscript")
                .context(DocumentAnalysisMessage.AnalysisContext.builder()
                        .existingCharacters(List.of()) // 1차 Pass는 빈 배열
                        .existingEvents(List.of())
                        .existingRelationships(List.of())
                        .existingSettings(List.of())
                        .build())
                .traceId(traceId) // ✅ 전달받은 traceId 사용
                .sentAt(System.currentTimeMillis()) // ✅ 발송 시점 타임스탬프 (순서 보장용)
                .build();
    }

    // ==================== 배치 재발송 기능 ====================

    /**
     * 배치 재발송 처리
     * AI Backend에서 타임아웃된 배치의 누락 문서 재발송을 요청할 때 호출됩니다.
     *
     * @param request 재발송 요청 정보 (batchId, projectId, missingDocumentOrders)
     * @return 재발송 결과 (RETRY 또는 CANCELLED)
     */
    @Transactional
    public BatchRetryResponse retryBatch(BatchRetryRequest request) {
        UUID projectId = UUID.fromString(request.getProjectId());
        List<Integer> missingOrders = request.getMissingDocumentOrders();

        log.info("배치 재발송 요청: batchId={}, projectId={}, missingOrders={}",
                request.getBatchId(), projectId, missingOrders);

        // 프로젝트의 문서 목록 조회
        List<Document> textDocuments = documentRepository.findTextDocumentsByProjectId(projectId);

        if (textDocuments.isEmpty()) {
            log.warn("프로젝트 {}에 문서가 없습니다.", projectId);
            return BatchRetryResponse.cancelled(request.getBatchId(), "프로젝트에 문서가 없습니다.");
        }

        // 누락된 순서에 해당하는 문서 필터링
        List<Document> documentsToRetry = textDocuments.stream()
                .filter(doc -> missingOrders.contains(doc.getOrder()))
                .toList();

        if (documentsToRetry.isEmpty()) {
            log.warn("재발송할 문서를 찾을 수 없습니다: missingOrders={}", missingOrders);
            return BatchRetryResponse.cancelled(request.getBatchId(), "재발송할 문서를 찾을 수 없습니다.");
        }

        // 문서 재발송
        List<Integer> retriedOrders = new java.util.ArrayList<>();
        String batchId = request.getBatchId();
        int totalDocuments = textDocuments.size();

        for (Document doc : documentsToRetry) {
            try {
                String jobId = UUID.randomUUID().toString();
                String traceId = UUID.randomUUID().toString();

                // AnalysisJob 생성
                AnalysisJob analysisJob = AnalysisJob.builder()
                        .jobId(jobId)
                        .project(doc.getProject())
                        .documentId(doc.getId())
                        .traceId(traceId)
                        .status(AnalysisJob.JobStatus.PENDING)
                        .build();
                analysisJobRepository.save(analysisJob);

                // 메시지 생성 (배치 정보 포함)
                DocumentAnalysisMessage message = buildMessage(doc, projectId, totalDocuments,
                        "full_manuscript", jobId, traceId);
                message.setBatchId(batchId);
                message.setTotalDocuments(totalDocuments);
                message.setBatchTimeoutSeconds(300);

                // 발송
                agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message, m -> {
                    m.getMessageProperties().setPriority(5); // 재발송은 중간 우선순위
                    return m;
                });

                retriedOrders.add(doc.getOrder());
                log.info("문서 재발송 완료: documentId={}, order={}", doc.getId(), doc.getOrder());

            } catch (Exception e) {
                log.error("문서 재발송 실패: documentId={}, error={}", doc.getId(), e.getMessage());
            }
        }

        if (retriedOrders.isEmpty()) {
            return BatchRetryResponse.cancelled(request.getBatchId(), "모든 문서 재발송에 실패했습니다.");
        }

        log.info("배치 재발송 완료: batchId={}, retriedOrders={}", batchId, retriedOrders);
        return BatchRetryResponse.retry(batchId, retriedOrders);
    }
}
