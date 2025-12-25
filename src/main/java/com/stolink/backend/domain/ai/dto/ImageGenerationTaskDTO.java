package com.stolink.backend.domain.ai.dto;

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

    private String jobId;
    private UUID projectId;
    private UUID characterId;
    private String characterName;
    private String description;

    @Builder.Default
    private String style = "anime";

    private String callbackUrl;
}
