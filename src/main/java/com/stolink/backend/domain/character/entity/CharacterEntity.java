package com.stolink.backend.domain.character.entity;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * AI 서버 호환용 캐릭터 엔티티 (PostgreSQL)
 * AI 서버가 character 테이블을 조회하므로 추가함.
 */
@Entity
@Table(name = "characters")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class CharacterEntity extends BaseEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // AI 생성 ID (예: char-세라-001)
    @Column(name = "character_id", length = 100)
    private String characterId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 50)
    private String role; // protagonist, antagonist, etc.

    @Column(length = 50)
    private String status; // alive, dead, unknown, active

    // Profile fields
    @Column
    private Integer age;

    @Column(length = 30)
    private String gender;

    @Column(length = 50)
    private String race;

    @Column(length = 10)
    private String mbti;

    @Column(columnDefinition = "TEXT")
    private String backstory;

    @Column(length = 100)
    private String faction;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    // JSON fields for complex objects
    @Column(name = "aliases_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String aliasesJson;

    @Column(name = "profile_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String profileJson;

    @Column(name = "appearance_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String appearanceJson;

    @Column(name = "visual_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String visualJson;

    @Column(name = "personality_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String personalityJson;

    @Column(name = "relations_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String relationsJson;

    @Column(name = "current_mood_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String currentMoodJson;

    @Column(name = "meta_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metaJson;

    @Column(name = "embedding_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String embeddingJson;

    @Column(name = "motivation", columnDefinition = "TEXT")
    private String motivation;

    @Column(name = "inventory_json", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String inventoryJson;

    @Column(name = "first_appearance", length = 255)
    private String firstAppearance;
}
