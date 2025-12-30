package com.stolink.backend.domain.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Image Worker로부터 받는 이미지 생성 결과 콜백 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageCallbackDTO {

    @JsonAlias("job_id")
    private String jobId;

    @JsonAlias("character_id")
    private UUID characterId;

    private String status; // "SUCCESS" or "FAILED"

    @JsonAlias("image_url")
    private String imageUrl;

    @JsonAlias("error_message")
    private String errorMessage;
}
