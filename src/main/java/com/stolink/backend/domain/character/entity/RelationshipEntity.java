package com.stolink.backend.domain.character.entity;

import java.util.UUID;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 캐릭터 관계 엔티티
 * AI 분석 결과의 relationships 리스트를 저장함.
 */
@Entity
@Table(name = "relationships")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RelationshipEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_character_id")
    private CharacterEntity sourceCharacter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_character_id")
    private CharacterEntity targetCharacter;

    @Column(name = "source_name", nullable = false)
    private String sourceName;

    @Column(name = "target_name", nullable = false)
    private String targetName;

    @Column(name = "relation_type", length = 50)
    private String relationType;

    @Column
    private Integer strength;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private Boolean bidirectional;
}
