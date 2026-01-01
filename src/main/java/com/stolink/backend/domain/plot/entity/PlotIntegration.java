package com.stolink.backend.domain.plot.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * AI 분석으로 추출된 플롯 통합 데이터
 */
@Entity
@Table(name = "plot_integrations")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PlotIntegration extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "document_id")
    private UUID documentId;

    // plot_summary
    @Column(columnDefinition = "TEXT")
    private String narrative;

    @Column(name = "central_conflict", columnDefinition = "TEXT")
    private String centralConflict;

    // narrative_beats (JSON)
    @Column(name = "narrative_beats_json", columnDefinition = "TEXT")
    private String narrativeBeatsJson;

    // tension_curve (JSON array)
    @Column(name = "tension_curve_json", columnDefinition = "TEXT")
    private String tensionCurveJson;

    @Column(name = "overall_tension")
    private Double overallTension;

    // three_act_structure (JSON)
    @Column(name = "three_act_structure_json", columnDefinition = "TEXT")
    private String threeActStructureJson;

    // foreshadowing (JSON)
    @Column(name = "foreshadowing_json", columnDefinition = "TEXT")
    private String foreshadowingJson;

    // multimedia_summary (JSON)
    @Column(name = "multimedia_summary_json", columnDefinition = "TEXT")
    private String multimediaSummaryJson;
}
