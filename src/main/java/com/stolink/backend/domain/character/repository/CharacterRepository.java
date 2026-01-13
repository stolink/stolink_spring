package com.stolink.backend.domain.character.repository;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.character.node.Character;

@Repository
public interface CharacterRepository extends Neo4jRepository<Character, String> {

        List<Character> findByProjectId(String projectId);

        List<Character> findAll();

        @Query("MATCH (c:Character) " +
                        "WHERE (c.projectId = $projectId OR c.project_id = $projectId) " +
                        "OPTIONAL MATCH (c)-[r]-(other:Character) " +
                        "WHERE type(r) IN ['RELATED_TO', 'ALLY', 'ENEMY', 'RIVAL', 'ROMANTIC', 'FAMILY', 'NEUTRAL'] " +
                        "RETURN c, collect(r), collect(other)")
        List<Character> findAllWithRelationshipsByProjectId(@Param("projectId") String projectId);

        @Query("MATCH (c:Character {id: $characterId}) " +
                        "WHERE (c.projectId = $projectId OR c.project_id = $projectId) " +
                        "OPTIONAL MATCH (c)-[r]-(other:Character) " +
                        "WHERE type(r) IN ['RELATED_TO', 'ALLY', 'ENEMY', 'RIVAL', 'ROMANTIC', 'FAMILY', 'NEUTRAL'] " +
                        "RETURN c, collect(r), collect(other)")
        Character findByIdAndProjectIdWithRelationships(
                        @Param("characterId") String characterId,
                        @Param("projectId") String projectId);

        @Query("MATCH (c:Character {id: $characterId}) " +
                        "OPTIONAL MATCH (c)-[r]-(other:Character) " +
                        "WHERE type(r) IN ['RELATED_TO', 'ALLY', 'ENEMY', 'RIVAL', 'ROMANTIC', 'FAMILY', 'NEUTRAL'] " +
                        "RETURN c, collect(r), collect(other)")
        java.util.Optional<Character> findByIdWithRelationships(@Param("characterId") String characterId);

        @Query("MATCH (source:Character {id: $sourceId}), (target:Character {id: $targetId}) " +
                        "MERGE (source)-[r:RELATED_TO]->(target) " +
                        "ON CREATE SET r.id = randomUUID(), r.projectId = $projectId, r.types = $types, r.strength = $strength, r.description = $description, r.bidirectional = $bidirectional "
                        +
                        "ON MATCH SET r.projectId = $projectId, r.types = $types, r.strength = $strength, r.description = $description, r.bidirectional = $bidirectional "
                        +
                        "RETURN r")
        void createRelationship(
                        @Param("sourceId") String sourceId,
                        @Param("targetId") String targetId,
                        @Param("projectId") String projectId,
                        @Param("types") List<String> types,
                        @Param("strength") Integer strength,
                        @Param("description") String description,
                        @Param("bidirectional") Boolean bidirectional);

        void deleteByProjectId(String projectId);

        java.util.Optional<Character> findByNameAndProjectId(String name, String projectId);

        java.util.Optional<Character> findByCharacterId(String characterId);

        // 중복 안전 조회 - 여러 결과가 있을 수 있는 경우 사용
        List<Character> findAllByNameAndProjectId(String name, String projectId);

        @Query("MATCH (c:Character {id: $characterId}) " +
                        "SET c.imageUrl = $imageUrl " +
                        "RETURN c")
        Character updateImageUrl(
                        @Param("characterId") String characterId,
                        @Param("imageUrl") String imageUrl);

        @Query("MATCH (p:Character {id: $primaryId}) " +
                        "MATCH (m:Character {id: $mergedId}) " +
                        "CALL apoc.refactor.mergeNodes([p, m], {properties: 'discard', mergeRels: true}) YIELD node " +
                        "RETURN node")
        Character mergeNodes(@Param("primaryId") String primaryId, @Param("mergedId") String mergedId);

        @Query("MATCH (c:Character {id: $characterId}) " +
                        "SET c.positionX = $positionX, c.positionY = $positionY " +
                        "RETURN c")
        Character updatePosition(
                        @Param("characterId") String characterId,
                        @Param("positionX") Double positionX,
                        @Param("positionY") Double positionY);

        @Query("MATCH (c:Character {projectId: $projectId}) WHERE c.id IS NULL SET c.id = randomUUID()")
        void assignUuidToCharacters(@Param("projectId") String projectId);

        @Query("MATCH (n) WHERE (n:Character OR n:Event OR n:Setting OR n:Project) AND (n.projectId = $projectId OR n.project_id = $projectId) "
                        +
                        "SET n.projectId = COALESCE(n.projectId, n.project_id) " +
                        "WITH n " +
                        "MATCH (c:Character) WHERE (c.projectId = $projectId OR c.project_id = $projectId) AND c.id IS NULL AND c.characterId IS NOT NULL "
                        +
                        "SET c.id = c.characterId " +
                        "WITH c " +
                        "MATCH ()-[r]->() WHERE r.project_id = $projectId " +
                        "SET r.projectId = r.project_id")
        void normalizeAllEntities(@Param("projectId") String projectId);
}
