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

    public Project.Genre getGenreEnum() {
        try {
            return genre != null ? Project.Genre.valueOf(genre.toUpperCase()) : Project.Genre.OTHER;
        } catch (IllegalArgumentException e) {
            return Project.Genre.OTHER;
        }
    }
}
