package com.stolink.backend.domain.character.repository;

import com.stolink.backend.domain.character.node.Character;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterRepository extends Neo4jRepository<Character, String> {

        List<Character> findByProjectId(String projectId);

        List<Character> findAll();

        @Query("MATCH (c:Character {projectId: $projectId}) " +
                        "OPTIONAL MATCH (c)-[r:RELATED_TO]-(other:Character) " +
                        "RETURN c, collect(r), collect(other)")
        List<Character> findAllWithRelationshipsByProjectId(@Param("projectId") String projectId);

        @Query("MATCH (c:Character {id: $characterId, projectId: $projectId}) " +
                        "OPTIONAL MATCH (c)-[r:RELATED_TO]-(other:Character) " +
                        "RETURN c, collect(r), collect(other)")
        Character findByIdAndProjectIdWithRelationships(
                        @Param("characterId") String characterId,
                        @Param("projectId") String projectId);

        @Query("MATCH (source:Character {id: $sourceId}), (target:Character {id: $targetId}) " +
                        "CREATE (source)-[r:RELATED_TO {id: randomUUID(), type: $type, strength: $strength, description: $description}]->(target) "
                        +
                        "RETURN r")
        void createRelationship(
                        @Param("sourceId") String sourceId,
                        @Param("targetId") String targetId,
                        @Param("type") String type,
                        @Param("strength") Integer strength,
                        @Param("description") String description);

        void deleteByProjectId(String projectId);

        java.util.Optional<Character> findByNameAndProjectId(String name, String projectId);

        @Query("MATCH (c:Character {id: $characterId}) " +
                        "SET c.imageUrl = $imageUrl " +
                        "RETURN c")
        Character updateImageUrl(
                        @Param("characterId") String characterId,
                        @Param("imageUrl") String imageUrl);
}
