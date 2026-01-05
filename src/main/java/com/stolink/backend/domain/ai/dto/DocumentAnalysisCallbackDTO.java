package com.stolink.backend.domain.ai.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 문서 분석 결과 콜백 DTO (Python → Spring)
 * 
 * AI 분석 완료 후 Python에서 Spring으로 전송하는 결과입니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentAnalysisCallbackDTO {

    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("parent_folder_id")
    private String parentFolderId;

    private String status; // COMPLETED, FAILED

    private List<SectionDTO> sections;

    private List<Map<String, Object>> characters;

    private List<Map<String, Object>> events;

    private List<Map<String, Object>> settings;

    private List<Map<String, Object>> relationships;

    // 추가된 필드들 (심화 분석 요청 시 포함됨)
    @JsonProperty("plot_integration")
    private Map<String, Object> plotIntegration;

    @JsonProperty("consistency_report")
    private Map<String, Object> consistencyReport;

    @JsonProperty("trace_id")
    private String traceId;

    private Object error;

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;

    /**
     * Validation result from AI analysis
     */
    @JsonProperty("validation")
    private Map<String, Object> validation;

    /**
     * 성공 여부 확인
     */
    public boolean isSuccess() {
        return "COMPLETED".equalsIgnoreCase(status);
    }

    /**
     * 실패 여부 확인
     */
    public boolean isFailed() {
        return "FAILED".equalsIgnoreCase(status);
    }

    /**
     * Section DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SectionDTO {

        @JsonProperty("sequence_order")
        private Integer sequenceOrder;

        @JsonProperty("nav_title")
        private String navTitle;

        private String content;

        private List<Double> embedding;

        @JsonProperty("related_characters")
        private List<String> relatedCharacters;

        @JsonProperty("related_events")
        private List<String> relatedEvents;
    }
}
