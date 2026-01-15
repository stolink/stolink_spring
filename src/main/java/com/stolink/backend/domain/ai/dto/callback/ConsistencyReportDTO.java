package com.stolink.backend.domain.ai.dto.callback;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ConsistencyReport DTO - AI 분석 결과의 일관성 보고서 데이터
 * Python 콜백의 consistency_report 객체에 대응
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsistencyReportDTO {

    /**
     * Overall consistency score (0-100)
     */
    private Integer score;

    @JsonProperty("high_severity")
    private Integer highSeverity;

    @JsonProperty("medium_severity")
    private Integer mediumSeverity;

    @JsonProperty("auto_fixable")
    private Integer autoFixable;

    @JsonProperty("requires_human_review")
    private Integer requiresHumanReview;

    @JsonProperty("requires_re_extraction")
    private Boolean requiresReExtraction;

    /**
     * Legacy/Optional fields (kept just in case, or remove if confirmed unused)
     */
    @JsonProperty("overall_score")
    private Integer overallScore;

    private List<ConflictDTO> conflicts;
    private List<Object> warnings;
    @JsonProperty("resolution_summary")
    private Map<String, Object> resolutionSummary;
    @JsonProperty("neo4j_validation")
    private Map<String, Object> neo4jValidation;

    /**
     * Get the effective score
     */
    public Integer getEffectiveScore() {
        return score != null ? score : (overallScore != null ? overallScore : 0);
    }

    /**
     * Conflict item DTO
     * AI 분석에서 감지된 일관성 충돌 정보
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConflictDTO {
        /**
         * 충돌 유형 (CHARACTER_TRAIT_CONFLICT, RELATIONSHIP_CONFLICT,
         * SOCIAL_PROTOCOL_VIOLATION, EMOTIONAL_CONTINUITY_ERROR,
         * CONSEQUENCE_MISSING, CROSS_CHAPTER_CONFLICT 등)
         */
        private String type;

        /**
         * 심각도 (HIGH, MEDIUM, LOW)
         */
        private String severity;

        /**
         * 충돌 원인/출처
         */
        private String source;

        /**
         * 기존 설정값
         */
        private String existing;

        /**
         * 충돌하는 새로운 값
         */
        @JsonProperty("new")
        private String newValue;

        /**
         * 관련 캐릭터(들)
         */
        private String character;

        /**
         * 충돌 상세 설명
         */
        private String description;

        /**
         * 제안된 조치 (FLAG_FOR_HUMAN, IGNORE, REEXTRACT 등)
         */
        @JsonProperty("suggested_action")
        private String suggestedAction;

        /**
         * Legacy: 해결 방안 (이전 버전 호환)
         */
        private String resolution;
        @JsonProperty("suggestion")
        private String suggestion;
        private LocationDTO location;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LocationDTO {
        private String chapter;
        private Integer line;

        @JsonProperty("document_id")
        private String documentId;
    }
}
