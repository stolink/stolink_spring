package com.stolink.backend.domain.ai.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisTaskDTO {

    @JsonProperty("message_type")
    @Builder.Default
    private String messageType = "DOCUMENT_ANALYSIS";

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("project_id")
    private UUID projectId;

    @JsonProperty("document_id")
    private UUID documentId;

    private String content;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @JsonProperty("trace_id")
    private String traceId;

    @JsonProperty("requires_deep_analysis")
    @Builder.Default
    private boolean requiresDeepAnalysis = true;

    private AnalysisContext context;
}
