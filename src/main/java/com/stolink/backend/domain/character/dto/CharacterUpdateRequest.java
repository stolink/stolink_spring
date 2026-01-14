package com.stolink.backend.domain.character.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 캐릭터 부분 업데이트 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterUpdateRequest {
    private String name;
    private String role;
    private String imageUrl;

    private Double positionX;
    private Double positionY;

    // JSON fields (Map -> String conversion in Service)
    private java.util.Map<String, Object> appearance;
    private java.util.Map<String, Object> profile;
    private java.util.Map<String, Object> personality;
    private java.util.Map<String, Object> currentMood;
    private java.util.List<Object> inventory;
}
