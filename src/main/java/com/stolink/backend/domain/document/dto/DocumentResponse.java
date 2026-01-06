package com.stolink.backend.domain.document.dto;

import com.stolink.backend.domain.document.entity.Document;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class DocumentResponse {
    private UUID id;
    private UUID projectId;
    private UUID parentId;
    private String type;
    private String title;
    private String content;
    private String synopsis;
    private Integer order;
    private String status;
    private String analysisStatus;
    private Integer analysisRetryCount;
    private String label;
    private String labelColor;
    private Integer wordCount;
    private Integer targetWordCount;
    private Boolean includeInCompile;
    private String keywords;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // 커뮤니티(Storead) 게시 완료 여부 - 프론트엔드에서 배포 상태 표시 및 선택 비활성화에 사용
    private Boolean isPublished;

    public static DocumentResponse from(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .projectId(document.getProject().getId())
                .parentId(document.getParent() != null ? document.getParent().getId() : null)
                .type(document.getType().name())
                .title(document.getTitle())
                .content(document.getContent())
                .synopsis(document.getSynopsis())
                .order(document.getOrder())
                .status(document.getStatus().name())
                .analysisStatus(document.getAnalysisStatus() != null ? document.getAnalysisStatus().name() : null)
                .analysisRetryCount(document.getAnalysisRetryCount())
                .label(document.getLabel())
                .labelColor(document.getLabelColor())
                .wordCount(document.getWordCount())
                .targetWordCount(document.getTargetWordCount())
                .includeInCompile(document.getIncludeInCompile())
                .keywords(document.getKeywords())
                .notes(document.getNotes())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .isPublished(document.getIsPublished())
                .build();
    }
}
