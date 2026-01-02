package com.stolink.backend.domain.character.relationship;

import com.stolink.backend.domain.character.node.Character;
import lombok.*;
import org.springframework.data.neo4j.core.schema.*;

@RelationshipProperties
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CharacterRelationship {

    @Id
    @GeneratedValue
    private Long id;

    @TargetNode
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Character target;

    private String source;

    private String type; // friend, lover, enemy, ally
    private Integer strength; // 1-10

    @com.fasterxml.jackson.annotation.JsonProperty("label")
    private String description;

    private String since; // When the relationship started
    private String history; // History of the relationship (nullable)

    private Boolean bidirectional; // 양방향 관계 여부
    private Integer revealedInChapter; // 관계가 드러난 챕터

    @com.fasterxml.jackson.annotation.JsonProperty("target")
    public String getTargetId() {
        return target != null ? target.getId() : null;
    }
}
