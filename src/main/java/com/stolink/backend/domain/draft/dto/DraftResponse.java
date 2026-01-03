package com.stolink.backend.domain.draft.dto;

import com.stolink.backend.domain.draft.entity.Draft;
import java.time.LocalDateTime;
import java.util.UUID;

public record DraftResponse(
    UUID id,
    Draft.PublishStatus publishStatus,
    LocalDateTime createdAt,
    LocalDateTime expiresAt
) {
    public static DraftResponse from(Draft draft) {
        return new DraftResponse(
            draft.getId(),
            draft.getPublishStatus(),
            draft.getCreatedAt(),
            draft.getExpiresAt()
        );
    }
}
