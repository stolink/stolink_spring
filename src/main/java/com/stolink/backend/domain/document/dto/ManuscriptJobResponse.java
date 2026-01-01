package com.stolink.backend.domain.document.dto;

import com.stolink.backend.domain.document.entity.ManuscriptJob;
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
public class ManuscriptJobResponse {
    private UUID jobId;
    private UUID projectId;
    private String status;
    private int progress;
    private String message;
    private int totalDocuments;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public static ManuscriptJobResponse from(ManuscriptJob job) {
        return ManuscriptJobResponse.builder()
                .jobId(job.getId())
                .projectId(job.getProject().getId())
                .status(job.getStatus().name())
                .progress(job.getProgress())
                .message(job.getMessage())
                .totalDocuments(job.getTotalDocuments())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .build();
    }
}
