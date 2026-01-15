package com.stolink.backend.domain.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsistencyReportResponse {
    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    private Integer score;

    @JsonProperty("overall_score")
    private Integer overallScore;

    @JsonProperty("requires_human_review")
    private Boolean requiresHumanReview;

    private List<Conflict> conflicts;

    @JsonProperty("resolution_summary")
    private Map<String, Object> resolutionSummary;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Conflict {
        private String type;
        private String severity;
        private String description;
        private String suggestion;
        private Location location;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String chapter;
        private Integer line;

        @JsonProperty("document_id")
        private String documentId;
    }
}
