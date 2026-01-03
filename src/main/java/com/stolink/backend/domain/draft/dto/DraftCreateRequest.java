package com.stolink.backend.domain.draft.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record DraftCreateRequest(
    @NotBlank(message = "documentId is required")
    String documentId,
    
    @NotBlank(message = "projectId is required")
    String projectId,
    
    String title,
    String content,
    Map<String, Object> graphSnapshot,
    
    // Work 생성용 필드 (storead에서 사용)
    @NotBlank(message = "workTitle is required for publishing")
    String workTitle,
    
    String workSynopsis,
    String workGenre,
    String workCoverUrl
) {}
