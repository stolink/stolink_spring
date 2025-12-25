package com.stolink.backend.domain.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisTaskDTO {
    private String jobId;
    private UUID projectId;
    private UUID documentId;
    private String content;
    private String callbackUrl;
    private Map<String, Object> options;
}
