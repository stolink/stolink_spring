package com.stolink.backend.domain.document.dto;

import com.stolink.backend.domain.document.entity.Document;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDocumentRequest {
    private String title;
    private String content;
    private String synopsis;
    private Integer order;
    private Document.DocumentStatus status;
    private String label;
    private String labelColor;
    private Integer targetWordCount;
    private Boolean includeInCompile;
    private List<String> keywords;
    private String notes;
}
