package com.stolink.backend.domain.event.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.event.node.Event;

@Repository
public interface EventNeo4jRepository extends Neo4jRepository<Event, String> {

        List<Event> findByProjectId(String projectId);

        List<Event> findByProjectIdOrderByImportanceDesc(String projectId);

        Optional<Event> findByProjectIdAndEventId(String projectId, String eventId);

        // 중복 이벤트가 있어도 안전하게 조회
        List<Event> findAllByProjectIdAndEventId(String projectId, String eventId);

        // Character -> Event (PARTICIPATED_IN) Edge 생성
        @org.springframework.data.neo4j.repository.query.Query("MATCH (c:Character {projectId: $projectId, name: $characterName}) "
                        +
                        "WITH c LIMIT 1 " +
                        "MATCH (e:Event {projectId: $projectId, eventId: $eventId}) " +
                        "WITH c, e LIMIT 1 " +
                        "MERGE (c)-[r:PARTICIPATED_IN]->(e) " +
                        "RETURN r")
        void createParticipationEdge(
                        @org.springframework.data.repository.query.Param("projectId") String projectId,
                        @org.springframework.data.repository.query.Param("characterName") String characterName,
                        @org.springframework.data.repository.query.Param("eventId") String eventId);

        // Event -> Setting (HAPPENED_AT) Edge 생성
        @org.springframework.data.neo4j.repository.query.Query("MATCH (e:Event {projectId: $projectId, eventId: $eventId}) "
                        +
                        "WITH e LIMIT 1 " +
                        "MATCH (s:Setting {projectId: $projectId, name: $settingName}) " +
                        "WITH e, s LIMIT 1 " +
                        "MERGE (e)-[r:HAPPENED_AT]->(s) " +
                        "RETURN r")
        void createHappenedAtEdge(
                        @org.springframework.data.repository.query.Param("projectId") String projectId,
                        @org.springframework.data.repository.query.Param("eventId") String eventId,
                        @org.springframework.data.repository.query.Param("settingName") String settingName);

        // Character -> Event (PARTICIPATES_IN or PARTICIPATED_IN) 조회
        @org.springframework.data.neo4j.repository.query.Query("MATCH (c:Character)-[:PARTICIPATES_IN|PARTICIPATED_IN]->(e:Event) "
                        +
                        "WHERE c.id = $characterId " +
                        "RETURN e")
        List<Event> findEventsByCharacterId(
                        @org.springframework.data.repository.query.Param("characterId") String characterId);

        void deleteByProjectId(String projectId);
}
