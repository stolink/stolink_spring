package com.stolink.backend.domain.share.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.stolink.backend.domain.document.entity.Document;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SharedDocumentResponse {
    private UUID id;
    private String title;
    private String type; // "folder" or "text"
    private String content; // HTML content, null for folders
    private Integer wordCount;
    private List<SharedDocumentResponse> children;

    public static SharedDocumentResponse from(Document document) {
        return SharedDocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .type(document.getType().name().toLowerCase())
                .content(document.getType().name().equals("TEXT") ? document.getContent() : null)
                .wordCount(document.getWordCount())
                .children(new ArrayList<>())
                .build();
    }
}
