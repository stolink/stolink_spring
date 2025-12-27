package com.stolink.backend.domain.project.dto;

import com.stolink.backend.domain.project.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {
    private String title;
    private String genre;
    private String description;
    private String status;

    public Project.Genre getGenreEnum() {
        try {
            return genre != null ? Project.Genre.valueOf(genre.toUpperCase()) : Project.Genre.OTHER;
        } catch (IllegalArgumentException e) {
            return Project.Genre.OTHER;
        }
    }

    /**
     * status 문자열을 ProjectStatus enum으로 변환
     * "writing" -> WRITING, "completed" -> COMPLETED
     */
    public Project.ProjectStatus getStatusEnum() {
        if (status == null) {
            return null; // null이면 업데이트하지 않음
        }
        try {
            return Project.ProjectStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
