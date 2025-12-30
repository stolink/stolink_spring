package com.stolink.backend.domain.share.dto;

import com.stolink.backend.domain.project.entity.Project;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class SharedProjectResponse {
    private UUID id;
    private String title;
    private String description;
    private List<SharedDocumentResponse> documents;

    public static SharedProjectResponse from(Project project, List<SharedDocumentResponse> documents) {
        return SharedProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .description(project.getDescription())
                .documents(documents)
                .build();
    }
}
