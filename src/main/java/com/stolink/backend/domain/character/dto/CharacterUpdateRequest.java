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
    private Double positionX;
    private Double positionY;

    // Basic Info
    private String name;
    private String role;
    private String status;
    private Integer age;
    private String gender;
    private String race;
    private String mbti;
    private String backstory;

    // JSON fields (received as Map/Object, serialized to String)
    private Object appearance;
    private Object personality;
    private Object aliases;
    private Object profile;
    private Object relations;
    private Object inventory;
}
