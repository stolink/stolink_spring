package com.stolink.backend.domain.draft.dto;

import java.util.Map;

public record DraftCreateRequest(
    String documentId,
    String projectId,
    String title,
    String content,
    Map<String, Object> graphSnapshot
) {}

