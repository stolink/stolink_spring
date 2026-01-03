package com.stolink.backend.domain.draft.entity;

import com.stolink.backend.domain.user.entity.User;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "drafts")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Draft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column
    private String documentId;

    @Column
    private String projectId;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    // 인물관계도 데이터 (JSONB)
    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> graphSnapshot;

    // Work 생성용 필드 (storead에서 사용)
    @Column(name = "work_title", length = 255)
    private String workTitle;

    @Column(name = "work_synopsis", columnDefinition = "TEXT")
    private String workSynopsis;

    @Column(name = "work_genre", length = 50)
    private String workGenre;

    @Column(name = "work_cover_url", length = 512)
    private String workCoverUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PublishStatus publishStatus = PublishStatus.DRAFT;

    @Column(name = "external_work_id")
    private Long externalWorkId;

    @Column(name = "external_chapter_id")
    private Long externalChapterId;

    public void updatePublishStatus(PublishStatus status) {
        this.publishStatus = status;
    }

    public void updatePublishResult(Long workId, Long chapterId) {
        this.externalWorkId = workId;
        this.externalChapterId = chapterId;
        this.publishStatus = PublishStatus.PUBLISHED;
    }

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

    public enum PublishStatus {
        DRAFT,
        PUBLISHING,
        PUBLISHED,
        FAILED
    }
}

