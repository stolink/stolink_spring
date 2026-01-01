package com.stolink.backend.domain.document.entity;

import com.stolink.backend.domain.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "manuscript_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManuscriptJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;

    @Column(nullable = false)
    private int progress; // 0-100

    private String message;

    private int totalDocuments;

    @Column(columnDefinition = "TEXT")
    private String manuscriptContent;

    private UUID parentId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    public enum JobStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    public void updateProgress(int progress, String message) {
        this.progress = progress;
        this.message = message;
    }

    public void complete(int totalDocuments) {
        this.status = JobStatus.COMPLETED;
        this.progress = 100;
        this.totalDocuments = totalDocuments;
        this.completedAt = LocalDateTime.now();
        this.message = "서재 준비 완료!";
    }

    public void fail(String errorMessage) {
        this.status = JobStatus.FAILED;
        this.message = errorMessage;
        this.completedAt = LocalDateTime.now();
    }
}
