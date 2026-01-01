package com.stolink.backend.domain.character.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

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
    @GeneratedValue(strategy = GenerationType.UUID)
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
    @Column(name = "aliases_json", columnDefinition = "TEXT")
    private String aliasesJson;

    @Column(name = "profile_json", columnDefinition = "TEXT")
    private String profileJson;

    @Column(name = "appearance_json", columnDefinition = "TEXT")
    private String appearanceJson;

    @Column(name = "visual_json", columnDefinition = "TEXT")
    private String visualJson;

    @Column(name = "personality_json", columnDefinition = "TEXT")
    private String personalityJson;

    @Column(name = "relations_json", columnDefinition = "TEXT")
    private String relationsJson;

    @Column(name = "current_mood_json", columnDefinition = "TEXT")
    private String currentMoodJson;

    @Column(name = "inventory_json", columnDefinition = "TEXT")
    private String inventoryJson;

    @Column(name = "meta_json", columnDefinition = "TEXT")
    private String metaJson;

    @Column(name = "embedding_json", columnDefinition = "TEXT")
    private String embeddingJson;

    @Column(name = "motivation", columnDefinition = "TEXT")
    private String motivation;

    @Column(name = "first_appearance", length = 255)
    private String firstAppearance;
}
