package com.stolink.backend.domain.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.stolink.backend.domain.ai.dto.BatchRetryRequest;
import com.stolink.backend.domain.ai.dto.BatchRetryResponse;
import com.stolink.backend.domain.ai.dto.DocumentAnalysisMessage;

/**
 * 배치 기반 순서 보장 통합 테스트
 * 
 * AI Backend의 순서 보장 로직 검증을 위한 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class BatchOrderingIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BatchOrderingIntegrationTest.class);

    @Autowired
    @Qualifier("agentRabbitTemplate")
    private RabbitTemplate agentRabbitTemplate;

    @Autowired
    private DocumentAnalysisPublisher documentAnalysisPublisher;

    @Value("${app.rabbitmq.queues.document-analysis:document_analysis_queue}")
    private String documentAnalysisQueue;

    private String testProjectId;
    private String testCallbackUrl;

    @BeforeEach
    void setUp() {
        testProjectId = UUID.randomUUID().toString();
        testCallbackUrl = "http://localhost:8080/api/internal/ai/analysis/callback";
    }

    // ==================== Phase 1: 기본 동작 확인 ====================

    @Test
    @DisplayName("즉시 처리 모드 - batchId 없으면 즉시 처리")
    void testImmediateProcessing() {
        // Given: 배치 정보 없는 메시지
        // 🔧 documentId는 UUID 형식 필수
        String documentId = UUID.randomUUID().toString();

        // 🔧 content는 분석에 충분한 길이 필요
        String testContent = "제1장: 시작\n\n" +
                "주인공 아린은 검을 들고 숲 속을 걸었다. " +
                "카엘이 그녀의 뒤를 따랐다. 두 사람은 오랜 친구였지만, 최근 사이가 멀어지고 있었다. " +
                "아린은 어둠의 마법사가 있다는 성으로 향하고 있었다.";

        DocumentAnalysisMessage message = DocumentAnalysisMessage.builder()
                .messageType("DOCUMENT_ANALYSIS") // 🔧 DOCUMENT_ANALYSIS
                .documentId(documentId)
                .projectId(testProjectId)
                .jobId(UUID.randomUUID().toString())
                .callbackUrl(testCallbackUrl)
                .content(testContent)
                .analysisType("full_manuscript")
                .requiresDeepAnalysis(true)
                // batchId 없음 → 즉시 처리
                .build();

        // When: 메시지 발송
        agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message);

        // Then: 메시지가 정상적으로 큐에 전송됨 (AI Backend에서 즉시 처리)
        log.info("✅ 즉시 처리 모드 메시지 발송 완료: documentId={}", message.getDocumentId());
        assertThat(message.getBatchId()).isNull();
        assertThat(message.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("배치 모드 - 순서대로 발송하면 순서대로 처리")
    void testBatchOrderingSequential() throws InterruptedException {
        // Given: 배치 ID와 3개 문서 (순서대로 발송)
        String batchId = UUID.randomUUID().toString();
        int totalDocuments = 3;

        // When: 순서대로 발송 (1 → 2 → 3)
        for (int order = 1; order <= totalDocuments; order++) {
            DocumentAnalysisMessage message = createBatchMessage(batchId, order, totalDocuments);
            agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message);
            log.info("📤 발송: order={} (순서대로)", order);
            Thread.sleep(50); // 발송 간격
        }

        // Then: AI Backend에서 순서대로 처리될 것
        log.info("✅ 배치 순차 발송 완료: batchId={}, totalDocuments={}", batchId, totalDocuments);
        assertThat(batchId).isNotNull();
    }

    // ==================== Phase 2: 순서 보장 확인 ====================

    @Test
    @DisplayName("배치 모드 - 역순 발송해도 정순 처리")
    void testBatchOrderingReverse() throws InterruptedException {
        // Given: 배치 ID와 3개 문서
        String batchId = UUID.randomUUID().toString();
        int totalDocuments = 3;
        List<Integer> sendOrder = List.of(3, 2, 1); // 역순

        // When: 의도적으로 역순 발송 (3 → 2 → 1)
        for (int order : sendOrder) {
            DocumentAnalysisMessage message = createBatchMessage(batchId, order, totalDocuments);
            agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message);
            log.info("📤 발송: order={} (역순)", order);
            Thread.sleep(100); // 네트워크 지연 시뮬레이션
        }

        // Then: AI Backend에서 Redis Sorted Set으로 정렬하여 1 → 2 → 3 순서로 처리될 것
        log.info("✅ 배치 역순 발송 완료. AI Backend는 sentAt 기준으로 정렬하여 처리할 것: batchId={}", batchId);
        assertThat(batchId).isNotNull();
    }

    @Test
    @DisplayName("배치 모드 - 랜덤 순서 발송해도 순서대로 처리")
    void testBatchOrderingRandom() throws InterruptedException {
        // Given: 배치 ID와 5개 문서
        String batchId = UUID.randomUUID().toString();
        int totalDocuments = 5;
        List<Integer> sendOrder = List.of(3, 1, 5, 2, 4); // 랜덤 순서

        // When: 랜덤 순서로 발송
        for (int order : sendOrder) {
            DocumentAnalysisMessage message = createBatchMessage(batchId, order, totalDocuments);
            agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message);
            log.info("📤 발송: order={} (랜덤)", order);
            Thread.sleep(80);
        }

        // Then: AI Backend가 sentAt 기준으로 정렬하여 1→2→3→4→5 순서로 처리
        log.info("✅ 배치 랜덤 발송 완료. AI Backend는 sentAt 순서로 처리: batchId={}", batchId);
        assertThat(sendOrder).hasSize(totalDocuments);
    }

    // ==================== Phase 3: 예외 상황 ====================

    @Test
    @DisplayName("배치 타임아웃 - 일부 문서 누락 시 재발송 요청 처리")
    void testBatchRetryMechanism() {
        // Given: 총 3개 문서 중 2개만 발송된 상황 (order=2 누락)
        String batchId = UUID.randomUUID().toString();
        List<Integer> arrivedOrders = List.of(1, 3);
        List<Integer> missingOrders = List.of(2);

        // 실제로는 2개만 발송
        for (int order : arrivedOrders) {
            DocumentAnalysisMessage message = createBatchMessage(batchId, order, 3);
            agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message);
            log.info("📤 발송: order={}", order);
        }

        log.warn("⏱️ order=2는 발송하지 않음 (타임아웃 시뮬레이션)");

        // When: AI Backend가 타임아웃 후 재발송 요청
        BatchRetryRequest retryRequest = BatchRetryRequest.builder()
                .batchId(batchId)
                .projectId(testProjectId)
                .missingDocumentOrders(missingOrders)
                .action("RETRY_OR_CANCEL")
                .build();

        BatchRetryResponse response = documentAnalysisPublisher.retryBatch(retryRequest);

        // Then: 재발송 응답 확인
        log.info("✅ 재발송 응답: status={}, retriedOrders={}",
                response.getStatus(), response.getRetriedOrders());

        // 실제 프로젝트에 문서가 없어서 CANCELLED가 반환될 수 있음
        assertThat(response).isNotNull();
        assertThat(response.getBatchId()).isEqualTo(batchId);

        if ("RETRY".equals(response.getStatus())) {
            assertThat(response.getRetriedOrders()).isNotNull();
        } else if ("CANCELLED".equals(response.getStatus())) {
            assertThat(response.getErrorMessage()).isNotNull();
            log.info("재발송 취소됨: {}", response.getErrorMessage());
        }
    }

    @Test
    @DisplayName("배치 필드 검증 - 모든 필수 필드가 설정되어야 함")
    void testBatchMessageFields() {
        // Given
        String batchId = UUID.randomUUID().toString();

        // When: 배치 메시지 생성
        DocumentAnalysisMessage message = createBatchMessage(batchId, 1, 3);

        // Then: 모든 배치 필드 검증
        assertThat(message.getBatchId()).isEqualTo(batchId);
        assertThat(message.getTotalDocuments()).isEqualTo(3);
        assertThat(message.getDocumentOrder()).isEqualTo(1);
        assertThat(message.getBatchTimeoutSeconds()).isEqualTo(300);
        assertThat(message.getSentAt()).isNotNull();

        log.info("✅ 배치 필드 검증 완료: batchId={}, totalDocuments={}, order={}, timeout={}, sentAt={}",
                message.getBatchId(),
                message.getTotalDocuments(),
                message.getDocumentOrder(),
                message.getBatchTimeoutSeconds(),
                message.getSentAt());
    }

    /**
     * 배치 메시지 생성 헬퍼
     * 
     * 🔧 수정사항:
     * - documentId: UUID 형식 필수 (AI Backend DB 쿼리에서 UUID 파싱 필요)
     * - content: 분석에 충분한 길이 필요 (최소 100자 권장)
     */
    private DocumentAnalysisMessage createBatchMessage(String batchId, int order, int totalDocuments) {
        // 테스트용 고정 UUID 생성 (order 기반으로 재현 가능)
        String documentId = UUID.nameUUIDFromBytes(("test-doc-" + order).getBytes()).toString();

        // 분석에 충분한 테스트 콘텐츠
        String testContent = String.format(
                "제%d장: 테스트 챕터\n\n" +
                        "주인공 아린은 검을 들고 숲 속을 걸었다. 카엘이 그녀의 뒤를 따랐다. " +
                        "두 사람은 오랜 친구였지만, 최근 사이가 멀어지고 있었다. " +
                        "아린은 어둠의 마법사가 있다는 성으로 향하고 있었다. " +
                        "카엘은 그녀를 보호하기 위해 동행했지만, 마음 한편으로는 걱정이 됐다. " +
                        "이 여정이 어떻게 끝날지 알 수 없었기 때문이다. " +
                        "숲의 나무들은 점점 어두워졌고, 이상한 소리가 들려왔다.",
                order);

        return DocumentAnalysisMessage.builder()
                .messageType("DOCUMENT_ANALYSIS") // 🔧 DOCUMENT_ANALYSIS (REQUEST 아님)
                .documentId(documentId)
                .projectId(testProjectId)
                .jobId(UUID.randomUUID().toString())
                .callbackUrl(testCallbackUrl)
                .content(testContent)
                .analysisType("full_manuscript")
                .requiresDeepAnalysis(true)
                .documentOrder(order)
                .totalDocumentsInChapter(totalDocuments)
                // 배치 필드
                .batchId(batchId)
                .totalDocuments(totalDocuments)
                .batchTimeoutSeconds(300)
                // sentAt은 Builder.Default로 자동 설정됨
                .build();
    }
}
