package com.stolink.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

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
    private String messageType = "DOCUMENT_ANALYSIS";

    @JsonProperty("document_id")
    private String documentId;

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

    private AnalysisContext context;

    @JsonProperty("trace_id")
    private String traceId;

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
