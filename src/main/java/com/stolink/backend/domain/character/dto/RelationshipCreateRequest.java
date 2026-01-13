package com.stolink.backend.domain.character.dto;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * 관계 생성 요청 DTO
 */
public record RelationshipCreateRequest(
        @NotBlank(message = "sourceId is required") String sourceId,
        @NotBlank(message = "targetId is required") String targetId,
        @NotEmpty(message = "types cannot be empty") List<String> types,
        @Min(value = 1, message = "strength must be at least 1") @Max(value = 10, message = "strength must be at most 10") Integer strength,
        Boolean bidirectional,
        String description) {

    public RelationshipCreateRequest {
        // Default values
        if (bidirectional == null) {
            bidirectional = false;
        }
        if (strength == null) {
            strength = 5;
        }
    }
}
