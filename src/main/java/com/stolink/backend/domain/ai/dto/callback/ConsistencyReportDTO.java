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
     * Python sends "score", also supports "overall_score" for backward compatibility
     */
    private Integer score;

    @JsonProperty("overall_score")
    private Integer overallScore;

    /**
     * List of detected conflicts
     */
    private List<ConflictDTO> conflicts;

    /**
     * List of warnings
     */
    private List<Object> warnings;

    @JsonProperty("requires_reextraction")
    private Boolean requiresReextraction;

    @JsonProperty("resolution_summary")
    private Map<String, Object> resolutionSummary;

    @JsonProperty("neo4j_validation")
    private Map<String, Object> neo4jValidation;

    /**
     * Get the effective score (prefers 'score' field, falls back to 'overall_score')
     */
    public Integer getEffectiveScore() {
        return score != null ? score : overallScore;
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
