package com.stolink.backend.domain.character.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 관계 수정 요청 DTO (Partial Update 지원)
 */
public record RelationshipUpdateRequest(
        List<String> types,
        @Min(value = 1, message = "strength must be at least 1") @Max(value = 10, message = "strength must be at most 10") Integer strength,
        String description) {
}
