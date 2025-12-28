package com.stolink.backend.domain.ai.dto.context;

import lombok.*;

/**
 * 기존 캐릭터 참조 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExistingCharacterRef {
    private String id;
    private String name;
    private String role;
}
