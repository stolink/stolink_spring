package com.stolink.backend.domain.character.node;

import lombok.*;
import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;

import com.stolink.backend.domain.character.relationship.CharacterRelationship;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private String projectId;
    private String name;
    private String faction;
    private String role; // protagonist, antagonist, supporting, mentor, sidekick, other
    private String imageUrl;

    @Relationship(type = "RELATED_TO", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private List<CharacterRelationship> relationships = new ArrayList<>();

    // Dynamic extras
    @Builder.Default
    @org.springframework.data.neo4j.core.schema.CompositeProperty
    private Map<String, Object> extras = new HashMap<>();

    public void updateExtras(String key, Object value) {
        this.extras.put(key, value);
    }
}
