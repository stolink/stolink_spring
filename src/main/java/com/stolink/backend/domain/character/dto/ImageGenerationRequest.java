package com.stolink.backend.domain.character.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for image generation endpoint.
 * Uses Java record for immutability and conciseness.
 */
public record ImageGenerationRequest(
    @NotBlank(message = "description is required")
    String description
) {}
