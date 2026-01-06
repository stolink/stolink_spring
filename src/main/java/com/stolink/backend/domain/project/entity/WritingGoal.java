package com.stolink.backend.domain.project.entity;

import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "writing_goals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class WritingGoal extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WritingGoalType type;

    @Column(nullable = false)
    private Long targetCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WritingGoalUnit unit;

    public void update(Long targetCount, WritingGoalUnit unit) {
        this.targetCount = targetCount;
        this.unit = unit;
        // Reset or recalculate logic is done in Service
    }
}
