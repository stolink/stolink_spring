package com.stolink.backend.domain.ai.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Validation DTO - AI 분석 결과의 검증 데이터
 * callback_result.json의 validation 객체에 대응
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationDTO {

    @JsonProperty("is_valid")
    private Boolean isValid;

    @JsonProperty("quality_score")
    private Integer qualityScore;

    private String action;

    @JsonProperty("action_description")
    private String actionDescription;

    @JsonProperty("data_completeness")
    private Map<String, Object> dataCompleteness;

    @JsonProperty("average_completeness")
    private Double averageCompleteness;

    @JsonProperty("validation_details")
    private ValidationDetailsDTO validationDetails;

    @JsonProperty("error_count")
    private Integer errorCount;

    @JsonProperty("warning_count")
    private Integer warningCount;

    @JsonProperty("execution_time_ms")
    private Double executionTimeMs;

    /**
     * ValidationDetails nested DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ValidationDetailsDTO {
        private List<String> errors;
        private List<String> warnings;

        @JsonProperty("per_field")
        private Map<String, Object> perField;
    }
}
