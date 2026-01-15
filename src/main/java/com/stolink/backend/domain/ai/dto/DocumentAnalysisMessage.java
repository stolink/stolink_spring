package com.stolink.backend.domain.ai.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 문서 분석 요청 메시지 (RabbitMQ 발행용)
 * 
 * Spring → Python으로 전송되는 분석 요청 메시지입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAnalysisMessage {

    @JsonProperty("message_type")
    @Builder.Default
    private String messageType = "DOCUMENT_ANALYSIS_REQUEST";

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("project_id")
    private String projectId;

    @JsonProperty("parent_folder_id")
    private String parentFolderId;

    @JsonProperty("chapter_title")
    private String chapterTitle;

    @JsonProperty("document_order")
    private Integer documentOrder;

    @JsonProperty("total_documents_in_chapter")
    private Integer totalDocumentsInChapter;

    @JsonProperty("analysis_pass")
    @Builder.Default
    private Integer analysisPass = 1;

    @JsonProperty("callback_url")
    private String callbackUrl;

    /**
     * 문서 내용 (AI 분석 대상)
     * AI Backend가 분석할 실제 텍스트 내용
     */
    @JsonProperty("content")
    private String content;

    /**
     * 분석 유형 (full_manuscript: 전체 분석, partial_snippet: 경량 분석)
     * - full_manuscript: 전체 분석 (청킹 + 관계분석 + GlobalMerge)
     * - partial_snippet: 경량 Fast Track 분석
     */
    @JsonProperty("analysis_type")
    @Builder.Default
    private String analysisType = "full_manuscript";

    /**
     * 심화 분석 수행 여부 (복선, 플롯 통합 등)
     * 무조건 true로 설정 (사용자 요청)
     */
    @JsonProperty("requires_deep_analysis")
    @Builder.Default
    private boolean requiresDeepAnalysis = true;

    private AnalysisContext context;

    @JsonProperty("trace_id")
    private String traceId;

    /**
     * 메시지 발송 시점 타임스탬프 (Unix epoch milliseconds)
     * 네트워크 지연에 관계없이 발송 순서대로 처리하기 위해 사용됩니다.
     * 필수 필드 - 빌더 사용 시 자동으로 현재 시간이 설정됩니다.
     */
    @JsonProperty("sent_at")
    @Builder.Default
    private Long sentAt = System.currentTimeMillis();

    // ==================== 배치 기반 순서 보장 필드 ====================

    /**
     * 배치 식별자 (UUID)
     * 여러 문서를 순차 처리할 때 사용됩니다.
     * null이면 즉시 처리 모드로 동작합니다.
     */
    @JsonProperty("batch_id")
    private String batchId;

    /**
     * 배치 내 총 문서 수
     * 모든 문서가 도착했는지 확인하는 데 사용됩니다.
     */
    @JsonProperty("total_documents")
    private Integer totalDocuments;

    /**
     * 배치 타임아웃 (초)
     * 이 시간 내에 모든 문서가 도착하지 않으면 재발송 요청이 발생합니다.
     */
    @JsonProperty("batch_timeout_seconds")
    @Builder.Default
    private Integer batchTimeoutSeconds = 300;

    /**
     * 분석 컨텍스트 (기존 캐릭터, 이벤트 등)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnalysisContext {

        @JsonProperty("existing_characters")
        @Builder.Default
        private List<Map<String, Object>> existingCharacters = List.of();

        @JsonProperty("existing_events")
        @Builder.Default
        private List<Map<String, Object>> existingEvents = List.of();

        @JsonProperty("existing_relationships")
        @Builder.Default
        private List<Map<String, Object>> existingRelationships = List.of();

        @JsonProperty("existing_settings")
        @Builder.Default
        private List<Map<String, Object>> existingSettings = List.of();

        @JsonProperty("previous_document_summary")
        private String previousDocumentSummary;
    }
}
