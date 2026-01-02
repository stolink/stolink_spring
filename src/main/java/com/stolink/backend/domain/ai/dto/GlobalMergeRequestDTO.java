package com.stolink.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalMergeRequestDTO {

    @JsonProperty("message_type")
    @Builder.Default
    private String messageType = "GLOBAL_MERGE";

    @JsonProperty("project_id")
    private UUID projectId;

    @JsonProperty("callback_url")
    private String callbackUrl;

    @JsonProperty("trace_id")
    private String traceId;
}
