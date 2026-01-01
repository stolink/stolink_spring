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
    private UUID userId;
    private UUID projectId;

    @JsonProperty("character_id")
    private UUID characterId;

    /**
     * FastAPI의 ImageTaskMessage.message에 매핑됨
     * 캐릭터 외형 설명 또는 편집 요청 내용
     */
    private String message;

    /**
     * 이미지 작업 타입: "create" 또는 "edit"
     */
    @Builder.Default
    private String action = "create";

    // TODO: 향후 characterName, style 필요시 FastAPI 스키마와 함께 추가
    // private String characterName;
    // @Builder.Default
    // private String style = "anime";

    @JsonProperty("callback_url")
    private String callbackUrl;
}
