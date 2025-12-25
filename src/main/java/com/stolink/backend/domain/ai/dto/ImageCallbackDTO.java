package com.stolink.backend.domain.ai.dto;

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

    private String jobId;
    private UUID characterId;
    private String status; // "SUCCESS" or "FAILED"
    private String imageUrl;
    private String errorMessage;
}
