package com.stolink.backend.domain.character.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 캐릭터 이미지 생성 요청 DTO
 * 
 * @param description 캐릭터 외형 설명 (FastAPI message 필드로 매핑)
 */
public record ImageGenerationRequest(
    @NotBlank(message = "description은 필수입니다")
    String description
) {}
