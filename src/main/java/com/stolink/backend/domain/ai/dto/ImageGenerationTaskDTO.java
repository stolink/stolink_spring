package com.stolink.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Image Worker로 전송되는 이미지 생성 요청 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationTaskDTO {

    @JsonProperty("job_id")
    private String jobId;

    @JsonProperty("project_id")
    private UUID projectId;

    @JsonProperty("character_id")
    private UUID characterId;

    @JsonProperty("character_name")
    private String characterName;

    private String description;

    @Builder.Default
    private String style = "anime";

    @JsonProperty("callback_url")
    private String callbackUrl;
}
