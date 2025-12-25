package com.stolink.backend.domain.document.dto;

import com.stolink.backend.domain.document.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTreeResponse {
    private UUID id;
    private String type;
    private String title;
    private Integer order;
    private MetadataInfo metadata;
    @Builder.Default
    private List<DocumentTreeResponse> children = new ArrayList<>();

    public static DocumentTreeResponse from(Document document) {
        return DocumentTreeResponse.builder()
                .id(document.getId())
                .type(document.getType().name().toLowerCase())
                .title(document.getTitle())
                .order(document.getOrder())
                .metadata(MetadataInfo.from(document))
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataInfo {
        private String status;
        private Integer wordCount;

        private LocalDateTime createdAt;

        public static MetadataInfo from(Document document) {
            return MetadataInfo.builder()
                    .status(document.getStatus().name().toLowerCase())
                    .wordCount(document.getWordCount())
                    .createdAt(document.getCreatedAt())
                    .build();
        }
    }
}
