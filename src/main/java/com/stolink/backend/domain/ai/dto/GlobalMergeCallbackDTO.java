package com.stolink.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * 글로벌 병합 결과 콜백 DTO (2차 Pass 결과)
 * 
 * Entity Resolution(캐릭터 병합) 결과를 포함합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalMergeCallbackDTO {

    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("project_id")
    private String projectId;

    private String status; // COMPLETED, FAILED

    @JsonProperty("character_merges")
    private List<CharacterMergeDTO> characterMerges;

    @JsonProperty("consistency_report")
    private Map<String, Object> consistencyReport;

    @JsonProperty("trace_id")
    private String traceId;

    private String error;

    @JsonProperty("processing_time_ms")
    private Long processingTimeMs;

    /**
     * 성공 여부 확인
     */
    public boolean isSuccess() {
        return "COMPLETED".equalsIgnoreCase(status);
    }

    /**
     * 캐릭터 병합 정보 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CharacterMergeDTO {

        @JsonProperty("primary_id")
        private String primaryId;

        @JsonProperty("merged_ids")
        private List<String> mergedIds;

        @JsonProperty("canonical_name")
        private String canonicalName;

        @JsonProperty("merged_aliases")
        private List<String> mergedAliases;

        private Double confidence;
    }
}
