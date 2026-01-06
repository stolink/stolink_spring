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

    @Property("name")
    private String name;

    @Property("role")
    private String role; // protagonist, antagonist, supporting, mentor, sidekick, other

    @Property("status")
    private String status; // alive, dead, unknown, active

    // Profile fields
    @Property("age")
    private Integer age;

    @Property("gender")
    private String gender;

    @Property("race")
    private String race;

    @Property("mbti")
    private String mbti;

    @Property("backstory")
    private String backstory;

    @Property("faction")
    private String faction; // faction.name

    // Image
    @Property("imageUrl")
    private String imageUrl;

    // Graph position (for frontend layout persistence)
    private Double positionX;
    private Double positionY;

    // JSON fields for complex objects
    @Property("aliasesJson")
    private String aliasesJson; // ["한채린", ...]

    @Property("profileJson")
    private String profileJson; // Full profile object with faction.social

    @Property("appearanceJson")
    private String appearanceJson; // physique, skin_tone, eyes, hair, attire, etc.

    @Property("personalityJson")
    private String personalityJson; // core_traits, flaws, values

    @Property("relationsJson")
    private String relationsJson; // graph[], event_refs[], location_context

    @Property("currentMoodJson")
    private String currentMoodJson; // emotion, intensity, trigger

    @Property("metaJson")
    private String metaJson; // created_at, updated_at, data_version, lock_version

    @Property("embeddingJson")
    private String embeddingJson; // 1024-dim vector as JSON array

    @Property("inventoryJson")
    private String inventoryJson; // [{ item_id, name, description }]

    // Legacy fields for backward compatibility
    @Property("visualJson")
    private String visualJson;

    @Property("motivation")
    private String motivation;

    @Property("firstAppearance")
    private String firstAppearance;

    @Property("extrasJson")
    private String extrasJson;

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private List<CharacterRelationship> relationships = new ArrayList<>();
}
