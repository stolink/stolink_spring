package com.stolink.backend.domain.project.entity;

import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.global.common.entity.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Project extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Genre genre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String coverImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.WRITING;

    @Column(length = 100)
    private String author;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> extras = new HashMap<>();

    public void update(String title, Genre genre, String description, String author, ProjectStatus status) {
        if (title != null)
            this.title = title;
        if (genre != null)
            this.genre = genre;
        if (description != null)
            this.description = description;
        if (author != null)
            this.author = author;
        if (status != null)
            this.status = status;
    }

    public void updateCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    public enum Genre {
        FANTASY, ROMANCE, SF, MYSTERY, THRILLER, HORROR, DRAMA, OTHER
    }

    public enum ProjectStatus {
        WRITING, COMPLETED
    }
}
