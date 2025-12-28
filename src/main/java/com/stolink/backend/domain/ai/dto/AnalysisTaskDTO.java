package com.stolink.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisTaskDTO {

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

    private AnalysisContext context;
}
