package com.stolink.backend.domain.character.node;

import lombok.*;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import com.stolink.backend.domain.character.relationship.CharacterRelationship;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.neo4j.core.schema.Property;

@Node("Character")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Character {

    @Id
    @GeneratedValue(generatorClass = UUIDStringGenerator.class)
    private String id;

    @Property("projectId")
    private String projectId;

    // AI 생성 ID (예: char-세라-001)
    @Property("characterId")
    private String characterId;

    private String name;
    private String role; // protagonist, antagonist, supporting, mentor, sidekick, other
    private String status; // alive, dead, unknown, active

    // Profile fields
    private Integer age;
    private String gender;
    private String race;
    private String mbti;
    private String backstory;
    private String faction; // faction.name

    // Image
    private String imageUrl;

    // JSON fields for complex objects
    private String aliasesJson; // ["한채린", ...]
    private String profileJson; // Full profile object with faction.social
    private String appearanceJson; // physique, skin_tone, eyes, hair, attire, etc.
    private String personalityJson; // core_traits, flaws, values
    private String relationsJson; // graph[], event_refs[], location_context
    private String currentMoodJson; // emotion, intensity, trigger

    private String metaJson; // created_at, updated_at, data_version, lock_version
    private String embeddingJson; // 1024-dim vector as JSON array
    private String inventoryJson; // [{ item_id, name, description }]

    // Legacy fields for backward compatibility
    private String visualJson;
    private String motivation;
    private String firstAppearance;
    private String extrasJson;

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private List<CharacterRelationship> relationships = new ArrayList<>();
}
