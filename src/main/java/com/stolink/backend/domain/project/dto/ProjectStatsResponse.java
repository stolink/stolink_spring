package com.stolink.backend.domain.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectStatsResponse {
    private Long totalWords;
    private Integer chapterCount;
    private Integer characterCount;
    private List<ChapterStats> chapters;
    private WritingActivity writingActivity;
}
