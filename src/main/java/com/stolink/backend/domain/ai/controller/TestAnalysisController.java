package com.stolink.backend.domain.ai.controller;

import com.stolink.backend.domain.ai.dto.DocumentAnalysisMessage;
import com.stolink.backend.domain.ai.service.DocumentAnalysisPublisher;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 연동 테스트용 컨트롤러
 * 
 * 개발/로컬 환경에서만 활성화됩니다.
 * 운영 환경에서는 비활성화됩니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/test/analysis")
@RequiredArgsConstructor
@Profile({ "dev", "local", "test" })
public class TestAnalysisController {

    private final DocumentAnalysisPublisher documentAnalysisPublisher;
    private final DocumentRepository documentRepository;

    @Qualifier("agentRabbitTemplate")
    private final RabbitTemplate agentRabbitTemplate;

    @Value("${app.rabbitmq.queues.document-analysis:document_analysis_queue}")
    private String documentAnalysisQueue;

    @Value("${app.callback.base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    /**
     * 프로젝트 분석 시작 (Batch 발행)
     * 
     * @param projectId 프로젝트 ID
     * @return 발행된 메시지 수
     */
    @PostMapping("/project/{projectId}/start")
    public ApiResponse<Map<String, Object>> startProjectAnalysis(@PathVariable UUID projectId) {
        log.info("Starting analysis for project: {}", projectId);

        int publishedCount = documentAnalysisPublisher.publishAnalysisForProject(projectId);

        return ApiResponse.ok(Map.of(
                "projectId", projectId,
                "publishedMessages", publishedCount,
                "status", "STARTED",
                "message", publishedCount + "개 문서 분석 시작"));
    }

    /**
     * 단일 문서 분석 테스트
     * 
     * @param documentId 문서 ID
     * @return 발행 결과
     */
    @PostMapping("/document/{documentId}/analyze")
    public ApiResponse<Map<String, Object>> analyzeDocument(@PathVariable UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + documentId));

        if (document.getType() != Document.DocumentType.TEXT) {
            return ApiResponse.<Map<String, Object>>builder()
                    .status(HttpStatus.BAD_REQUEST)
                    .message("TEXT 타입 문서만 분석할 수 있습니다.")
                    .build();
        }

        documentAnalysisPublisher.publishAnalysisForDocument(document);

        return ApiResponse.ok(Map.of(
                "documentId", documentId,
                "title", document.getTitle(),
                "status", "QUEUED",
                "message", "분석 요청이 발행되었습니다."));
    }

    /**
     * 수동 메시지 발행 테스트
     * 
     * 직접 메시지를 구성하여 RabbitMQ로 발행합니다.
     */
    @PostMapping("/manual")
    public ApiResponse<Map<String, Object>> sendManualMessage(@RequestBody Map<String, Object> request) {
        String documentId = (String) request.getOrDefault("documentId", "test-doc-001");
        String projectId = (String) request.getOrDefault("projectId", "test-project-001");
        String parentFolderId = (String) request.getOrDefault("parentFolderId", "test-folder-001");
        String chapterTitle = (String) request.getOrDefault("chapterTitle", "제1장");

        DocumentAnalysisMessage message = DocumentAnalysisMessage.builder()
                .documentId(documentId)
                .projectId(projectId)
                .parentFolderId(parentFolderId)
                .chapterTitle(chapterTitle)
                .documentOrder(1)
                .totalDocumentsInChapter(1)
                .analysisPass(1)
                .callbackUrl(callbackBaseUrl + "/api/ai-callback")
                .context(DocumentAnalysisMessage.AnalysisContext.builder()
                        .existingCharacters(List.of())
                        .existingEvents(List.of())
                        .existingRelationships(List.of())
                        .existingSettings(List.of())
                        .build())
                .traceId("test-" + UUID.randomUUID().toString().substring(0, 8))
                .build();

        agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message);

        log.info("Manual test message sent: documentId={}, projectId={}", documentId, projectId);

        return ApiResponse.ok(Map.of(
                "documentId", documentId,
                "projectId", projectId,
                "queue", documentAnalysisQueue,
                "callbackUrl", message.getCallbackUrl(),
                "traceId", message.getTraceId(),
                "status", "SENT"));
    }

    /**
     * RabbitMQ 연결 상태 확인
     */
    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> checkHealth() {
        boolean connected = false;
        String errorMessage = null;

        try {
            // 간단한 연결 확인
            agentRabbitTemplate.execute(channel -> {
                channel.queueDeclarePassive(documentAnalysisQueue);
                return null;
            });
            connected = true;
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("RabbitMQ connection failed: {}", e.getMessage());
        }

        return ApiResponse.ok(Map.of(
                "rabbitmq", connected ? "connected" : "disconnected",
                "queue", documentAnalysisQueue,
                "callbackUrl", callbackBaseUrl + "/api/ai-callback",
                "error", errorMessage != null ? errorMessage : "none"));
    }
}
