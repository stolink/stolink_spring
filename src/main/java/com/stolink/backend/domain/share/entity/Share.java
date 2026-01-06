package com.stolink.backend.domain.share.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "shares")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Share extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    @Column
    private String passwordHash;

    @Column
    private java.time.LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    public void updateSettings(String passwordHash, java.time.LocalDateTime expiresAt) {
        this.passwordHash = passwordHash;
        this.expiresAt = expiresAt;
    }

    public void incrementViewCount() {
        this.viewCount = (this.viewCount == null ? 0 : this.viewCount) + 1;
    }
}
