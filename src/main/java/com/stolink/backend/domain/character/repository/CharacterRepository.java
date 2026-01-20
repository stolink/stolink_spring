package com.stolink.backend.domain.character.repository;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.character.node.Character;

@Repository
public interface CharacterRepository extends Neo4jRepository<Character, String> {

        @Query("MATCH (c:Character) WHERE (c.projectId = $projectId OR c.project_id = $projectId) RETURN c")
        List<Character> findByProjectId(@Param("projectId") String projectId);

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
                        "ON MATCH SET r.projectId = $projectId, r.types = $types, r.strength = $strength, r.description = $description, r.bidirectional = $bidirectional")
        void createRelationship(
                        @Param("sourceId") String sourceId,
                        @Param("targetId") String targetId,
                        @Param("projectId") String projectId,
                        @Param("types") List<String> types,
                        @Param("strength") Integer strength,
                        @Param("description") String description,
                        @Param("bidirectional") Boolean bidirectional);

        void deleteByProjectId(String projectId);

        void deleteAllByProjectId(String projectId);

        java.util.Optional<Character> findByNameAndProjectId(String name, String projectId);

        java.util.Optional<Character> findByCharacterId(String characterId);

        @Query("MATCH (c:Character {id: $id}) RETURN coalesce(c.projectId, c.project_id)")
        java.util.Optional<String> findProjectIdById(@Param("id") String id);

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
                        "WITH p, m, properties(m) AS mergedProps " +
                        "OPTIONAL MATCH (m)-[r]-() " +
                        "DELETE r " +
                        "DELETE m " +
                        "RETURN p")
        Character mergeNodes(@Param("primaryId") String primaryId, @Param("mergedId") String mergedId);

        @Query("MATCH (c:Character {id: $characterId}) " +
                        "SET c.positionX = COALESCE($positionX, c.positionX), " +
                        "    c.positionY = COALESCE($positionY, c.positionY), " +
                        "    c.name = COALESCE($name, c.name), " +
                        "    c.role = COALESCE($role, c.role), " +
                        "    c.gender = COALESCE($gender, c.gender), " +
                        "    c.age = COALESCE($age, c.age), " +
                        "    c.race = COALESCE($race, c.race), " +
                        "    c.imageUrl = COALESCE($imageUrl, c.imageUrl), " +
                        "    c.appearanceJson = COALESCE($appearanceJson, c.appearanceJson), " +
                        "    c.profileJson = COALESCE($profileJson, c.profileJson), " +
                        "    c.personalityJson = COALESCE($personalityJson, c.personalityJson), " +
                        "    c.currentMoodJson = COALESCE($currentMoodJson, c.currentMoodJson), " +
                        "    c.inventoryJson = COALESCE($inventoryJson, c.inventoryJson) " +
                        "RETURN c")
        Character updateCharacterFull(
                        @Param("characterId") String characterId,
                        @Param("name") String name,
                        @Param("role") String role,
                        @Param("gender") String gender,
                        @Param("age") Integer age,
                        @Param("race") String race,
                        @Param("imageUrl") String imageUrl,
                        @Param("positionX") Double positionX,
                        @Param("positionY") Double positionY,
                        @Param("appearanceJson") String appearanceJson,
                        @Param("profileJson") String profileJson,
                        @Param("personalityJson") String personalityJson,
                        @Param("currentMoodJson") String currentMoodJson,
                        @Param("inventoryJson") String inventoryJson);

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

        // =========== Relationship CRUD Queries ===========

        /**
         * 중복 관계 확인 (sourceId → targetId 방향)
         */
        @Query("MATCH (s:Character {id: $sourceId})-[r:RELATED_TO]->(t:Character {id: $targetId}) " +
                        "RETURN count(r) > 0 as exists")
        Boolean existsRelationship(@Param("sourceId") String sourceId, @Param("targetId") String targetId);

        /**
         * 역방향 관계 조회 (양방향 삭제용)
         */
        @Query("MATCH (s:Character {id: $targetId})-[r:RELATED_TO]->(t:Character {id: $sourceId}) " +
                        "WHERE r.bidirectional = true " +
                        "RETURN id(r) as relId")
        java.util.Optional<Long> findReverseRelationshipId(
                        @Param("sourceId") String sourceId,
                        @Param("targetId") String targetId);

        /**
         * 관계 수정 (Partial Update)
         */
        @Query("MATCH (source:Character)-[r:RELATED_TO]->(target:Character) " +
                        "WHERE id(r) = $relationshipId " +
                        "SET r.types = CASE WHEN $types IS NOT NULL THEN $types ELSE r.types END, " +
                        "    r.strength = CASE WHEN $strength IS NOT NULL THEN $strength ELSE r.strength END, " +
                        "    r.description = CASE WHEN $description IS NOT NULL THEN $description ELSE r.description END, "
                        +
                        "    r.bidirectional = CASE WHEN $bidirectional IS NOT NULL THEN $bidirectional ELSE r.bidirectional END, "
                        +
                        "    r.since = CASE WHEN $since IS NOT NULL THEN $since ELSE r.since END "
                        +
                        "RETURN source.id as sourceId, target.id as targetId, " +
                        "       id(r) as relId, r.types as types, r.strength as strength, " +
                        "       r.description as description, r.bidirectional as bidirectional, r.since as since, r.projectId as projectId")
        RelationshipProjection updateRelationship(
                        @Param("relationshipId") Long relationshipId,
                        @Param("types") java.util.List<String> types,
                        @Param("strength") Integer strength,
                        @Param("description") String description,
                        @Param("bidirectional") Boolean bidirectional,
                        @Param("since") String since);

        /**
         * 관계 삭제
         */
        @Query("MATCH ()-[r:RELATED_TO]->() " +
                        "WHERE id(r) = $relationshipId " +
                        "DELETE r")
        void deleteRelationshipById(@Param("relationshipId") Long relationshipId);

        /**
         * 관계 상세 조회 (Response용)
         */
        @Query("MATCH (source:Character)-[r:RELATED_TO]->(target:Character) " +
                        "WHERE id(r) = $relationshipId " +
                        "RETURN source.id as sourceId, target.id as targetId, " +
                        "       id(r) as relId, r.types as types, r.strength as strength, " +
                        "       r.description as description, r.bidirectional as bidirectional, r.since as since, r.projectId as projectId")
        java.util.Optional<RelationshipProjection> getRelationshipDetailsById(
                        @Param("relationshipId") Long relationshipId);

        /**
         * 관계 생성 후 ID 반환
         */
        @Query("MATCH (source:Character {id: $sourceId}), (target:Character {id: $targetId}) " +
                        "CREATE (source)-[r:RELATED_TO {projectId: $projectId, types: $types, strength: $strength, " +
                        "        description: $description, bidirectional: $bidirectional}]->(target) " +
                        "RETURN id(r) as relId")
        Long createRelationshipReturningId(
                        @Param("sourceId") String sourceId,
                        @Param("targetId") String targetId,
                        @Param("projectId") String projectId,
                        @Param("types") java.util.List<String> types,
                        @Param("strength") Integer strength,
                        @Param("description") String description,
                        @Param("bidirectional") Boolean bidirectional);

        // =========== Source/Target 기반 쿼리 (프론트엔드 복합 ID 지원) ===========

        /**
         * 관계 상세 조회 (sourceId + targetId 기반)
         */
        @Query("MATCH (source:Character {id: $sourceId})-[r:RELATED_TO]->(target:Character {id: $targetId}) " +
                        "RETURN source.id as sourceId, target.id as targetId, " +
                        "       id(r) as relId, r.types as types, r.strength as strength, " +
                        "       r.description as description, r.bidirectional as bidirectional, r.since as since, r.projectId as projectId")
        java.util.Optional<RelationshipProjection> getRelationshipDetailsBySourceTarget(
                        @Param("sourceId") String sourceId,
                        @Param("targetId") String targetId,
                        @Param("projectId") String projectId);

        /**
         * 관계 수정 (sourceId + targetId 기반, Partial Update)
         */
        @Query("MATCH (source:Character {id: $sourceId})-[r:RELATED_TO]->(target:Character {id: $targetId}) " +
                        "SET r.types = CASE WHEN $types IS NOT NULL THEN $types ELSE r.types END, " +
                        "    r.strength = CASE WHEN $strength IS NOT NULL THEN $strength ELSE r.strength END, " +
                        "    r.description = CASE WHEN $description IS NOT NULL THEN $description ELSE r.description END, "
                        +
                        "    r.bidirectional = CASE WHEN $bidirectional IS NOT NULL THEN $bidirectional ELSE r.bidirectional END, "
                        +
                        "    r.since = CASE WHEN $since IS NOT NULL THEN $since ELSE r.since END "
                        +
                        "RETURN source.id as sourceId, target.id as targetId, " +
                        "       id(r) as relId, r.types as types, r.strength as strength, " +
                        "       r.description as description, r.bidirectional as bidirectional, r.since as since, r.projectId as projectId")
        RelationshipProjection updateRelationshipBySourceTarget(
                        @Param("sourceId") String sourceId,
                        @Param("targetId") String targetId,
                        @Param("projectId") String projectId,
                        @Param("types") java.util.List<String> types,
                        @Param("strength") Integer strength,
                        @Param("description") String description,
                        @Param("bidirectional") Boolean bidirectional,
                        @Param("since") String since);

        /**
         * 관계 삭제 (sourceId + targetId 기반)
         */
        @Query("MATCH (source:Character {id: $sourceId})-[r:RELATED_TO]->(target:Character {id: $targetId}) " +
                        "DELETE r")
        void deleteRelationshipBySourceTarget(
                        @Param("sourceId") String sourceId,
                        @Param("targetId") String targetId,
                        @Param("projectId") String projectId);
}
