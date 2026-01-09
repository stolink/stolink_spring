package com.stolink.backend.domain.ai.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

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
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConflictDTO {
        private String type;
        private String description;
        private String severity;
        private String resolution;
    }
}
