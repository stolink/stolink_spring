package com.stolink.backend.domain.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class AnalysisRequestDTO {

    @NotNull(message = "projectId는 필수입니다.")
    private UUID projectId;

    @NotNull(message = "documentId는 필수입니다.")
    private UUID documentId;

    @NotBlank(message = "content는 필수입니다.")
    private String content;

    private Map<String, Object> context;
}
