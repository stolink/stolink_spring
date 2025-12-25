package com.stolink.backend.domain.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDocumentRequest {
    private UUID projectId;
    private UUID parentId;
    private String type; // folder or text
    private String title;
    private String synopsis;
    private Integer targetWordCount;
}
