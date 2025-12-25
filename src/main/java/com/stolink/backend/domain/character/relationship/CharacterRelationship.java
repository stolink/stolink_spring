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
    private Character target;

    private String type; // friendly, hostile, neutral, romantic, family
    private Integer strength; // 1-10
    private String description;
    private String since; // When the relationship started
}
