package com.stolink.backend.domain.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 이미지 생성 API 요청 DTO (클라이언트 → Spring)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageGenerationRequest {

    private UUID projectId;
    private UUID characterId;
    
    /**
     * 이미지 생성/수정 액션
     * - "create": 새 이미지 생성
     * - "edit": 기존 이미지 수정
     * - "reflect_time": 시간 경과 반영
     */
    @Builder.Default
    private String action = "create";
    
    /**
     * 이미지 생성 프롬프트 (메시지)
     */
    private String message;
    
    /**
     * 기존 이미지 URL (수정 시 사용)
     */
    private String imageUrl;
    
    /**
     * 수정 요청 내용 (edit, reflect_time 시 사용)
     */
    private String editRequest;
}
