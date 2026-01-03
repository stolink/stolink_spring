package com.stolink.backend.domain.draft.dto;

import com.stolink.backend.domain.draft.entity.Draft;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record DraftDetailResponse(
    UUID id,
    String documentId,
    String projectId,
    String title,
    String content,
    Map<String, Object> graphSnapshot,
    String workTitle,
    String workSynopsis,
    String workGenre,
    String workCoverUrl,
    Draft.PublishStatus publishStatus,
    Long externalWorkId,
    Long externalChapterId,
    LocalDateTime createdAt,
    LocalDateTime expiresAt
) {
    public static DraftDetailResponse from(Draft draft) {
        return new DraftDetailResponse(
            draft.getId(),
            draft.getDocumentId(),
            draft.getProjectId(),
            draft.getTitle(),
            draft.getContent(),
            draft.getGraphSnapshot(),
            draft.getWorkTitle(),
            draft.getWorkSynopsis(),
            draft.getWorkGenre(),
            draft.getWorkCoverUrl(),
            draft.getPublishStatus(),
            draft.getExternalWorkId(),
            draft.getExternalChapterId(),
            draft.getCreatedAt(),
            draft.getExpiresAt()
        );
    }
}


