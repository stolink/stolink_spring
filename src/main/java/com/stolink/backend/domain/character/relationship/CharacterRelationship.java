package com.stolink.backend.domain.character.relationship;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

import com.stolink.backend.domain.character.node.Character;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@RelationshipProperties
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterRelationship {

    @Property("projectId")
    private String projectId;

    @Property("project_id")
    private String secondProjectId;

    public String getProjectId() {
        return projectId != null ? projectId : secondProjectId;
    }

    @Id
    @GeneratedValue
    private Long id;

    @TargetNode
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Character target;

    private String source;

    private java.util.List<String> types; // friend, lover, enemy, ally
    private Integer strength; // 1-10

    @com.fasterxml.jackson.annotation.JsonProperty("label")
    private String description;

    private String since; // When the relationship started
    private String history; // History of the relationship (nullable)

    private Boolean bidirectional; // 양방향 관계 여부
    private Integer revealedInChapter; // 관계가 드러난 챕터

    // New detailed relationship fields
    private Integer emotionalBond; // 1-10
    private Integer functionalTrust; // 1-10
    private Integer valueAlignment; // 1-10
    private Integer interdependence; // 1-10
    private Integer latentTension; // 1-10
    private String publicStance; // e.g., ALLY, ENEMY, NEUTRAL
    private String privateFeeling; // e.g., TRUST, HATE, CURIOSITY

    @com.fasterxml.jackson.annotation.JsonProperty("target")
    public String getTargetId() {
        return target != null ? target.getId() : null;
    }
}
