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

    private String type; // friend, lover, enemy
    private Integer strength; // 1-10

    @com.fasterxml.jackson.annotation.JsonProperty("label")
    private String description;

    private String since; // When the relationship started

    @com.fasterxml.jackson.annotation.JsonProperty("target")
    public String getTargetId() {
        return target != null ? target.getId() : null;
    }
}
