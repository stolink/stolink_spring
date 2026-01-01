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
                .build();
    }
}
