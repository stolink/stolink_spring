package com.stolink.backend.domain.ai.dto;

import lombok.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class AnalysisTaskDTO {
    private String jobId;
    private UUID projectId;
    private UUID documentId;
    private String content;
    private String callbackUrl;
    private Map<String, Object> options = new HashMap<>();

    @Builder
    public AnalysisTaskDTO(String jobId, UUID projectId, UUID documentId, String content, String callbackUrl) {
        this.jobId = jobId;
        this.projectId = projectId;
        this.documentId = documentId;
        this.content = content;
        this.callbackUrl = callbackUrl;
    }
}
