package com.stolink.backend.domain.foreshadowing.entity;

import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "foreshadowing_appearances")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ForeshadowingAppearance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "foreshadowing_id", nullable = false)
    private Foreshadowing foreshadowing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(nullable = false)
    private Integer line;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String context;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRecovery = false;

    public void markAsRecovery() {
        this.isRecovery = true;
    }
}
