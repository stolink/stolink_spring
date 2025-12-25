package com.stolink.backend.domain.foreshadowing.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "foreshadowing", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "project_id", "tag" })
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Foreshadowing extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 100)
    private String tag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ForeshadowingStatus status = ForeshadowingStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private Importance importance = Importance.MINOR;

    @OneToMany(mappedBy = "foreshadowing", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ForeshadowingAppearance> appearances = new ArrayList<>();

    public void updateStatus(ForeshadowingStatus status) {
        this.status = status;
    }

    public void update(String description, Importance importance) {
        if (description != null)
            this.description = description;
        if (importance != null)
            this.importance = importance;
    }

    public enum ForeshadowingStatus {
        PENDING, RECOVERED, IGNORED
    }

    public enum Importance {
        MAJOR, MINOR
    }
}
