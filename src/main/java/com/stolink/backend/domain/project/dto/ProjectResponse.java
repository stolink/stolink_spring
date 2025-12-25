package com.stolink.backend.domain.project.dto;

import com.stolink.backend.domain.project.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    private UUID id;
    private String title;
    private String genre;
    private String description;
    private String coverImage;
    private String status;
    private String author;
    private ProjectStats stats;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProjectResponse from(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .title(project.getTitle())
                .genre(project.getGenre() != null ? project.getGenre().name() : null)
                .description(project.getDescription())
                .coverImage(project.getCoverImage())
                .status(project.getStatus().name().toLowerCase())
                .author(project.getAuthor())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    public static ProjectResponse from(Project project, ProjectStats stats) {
        ProjectResponse response = from(project);
        response.setStats(stats);
        return response;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectStats {
        private Long totalWords;
        private Long chapterCount;
    }
}
