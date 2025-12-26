package com.stolink.backend.domain.document.dto;

import com.stolink.backend.domain.document.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTreeResponse {
    private UUID id;
    private UUID projectId;
    private UUID parentId;
    private String type;
    private String title;
    private String content;
    private String synopsis;
    private Integer order;
    private String status;
    private String label;
    private String labelColor;
    private Integer wordCount;
    private Integer targetWordCount;
    private Boolean includeInCompile;
    private List<String> keywords;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Builder.Default
    private List<DocumentTreeResponse> children = new ArrayList<>();

    public static DocumentTreeResponse from(Document document) {
        // Convert comma-separated keywords to list
        List<String> keywordsList = new ArrayList<>();
        if (document.getKeywords() != null && !document.getKeywords().isEmpty()) {
            keywordsList = Arrays.stream(document.getKeywords().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        return DocumentTreeResponse.builder()
                .id(document.getId())
                .projectId(document.getProject().getId())
                .parentId(document.getParent() != null ? document.getParent().getId() : null)
                .type(document.getType().name().toLowerCase())
                .title(document.getTitle())
                .content(document.getContent())
                .synopsis(document.getSynopsis())
                .order(document.getOrder())
                .status(document.getStatus().name().toLowerCase())
                .label(document.getLabel())
                .labelColor(document.getLabelColor())
                .wordCount(document.getWordCount())
                .targetWordCount(document.getTargetWordCount())
                .includeInCompile(document.getIncludeInCompile())
                .keywords(keywordsList)
                .notes(document.getNotes())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
