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
}
