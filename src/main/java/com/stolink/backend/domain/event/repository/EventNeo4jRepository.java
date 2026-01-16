package com.stolink.backend.domain.event.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.event.node.Event;

@Repository
public interface EventNeo4jRepository extends Neo4jRepository<Event, String> {

        @org.springframework.data.neo4j.repository.query.Query("MATCH (e:Event) WHERE (e.project_id = $projectId OR e.projectId = $projectId) RETURN e")
        List<Event> findByProjectId(@org.springframework.data.repository.query.Param("projectId") String projectId);

        @org.springframework.data.neo4j.repository.query.Query("MATCH (e:Event) WHERE (e.project_id = $projectId OR e.projectId = $projectId) RETURN e ORDER BY e.importance DESC")
        List<Event> findByProjectIdOrderByImportanceDesc(
                        @org.springframework.data.repository.query.Param("projectId") String projectId);

        @org.springframework.data.neo4j.repository.query.Query("MATCH (e:Event) WHERE (e.project_id = $projectId OR e.projectId = $projectId) AND e.eventId = $eventId RETURN e LIMIT 1")
        Optional<Event> findByProjectIdAndEventId(
                        @org.springframework.data.repository.query.Param("projectId") String projectId,
                        @org.springframework.data.repository.query.Param("eventId") String eventId);

        // 중복 이벤트가 있어도 안전하게 조회
        @org.springframework.data.neo4j.repository.query.Query("MATCH (e:Event) WHERE (e.project_id = $projectId OR e.projectId = $projectId) AND e.eventId = $eventId RETURN e")
        List<Event> findAllByProjectIdAndEventId(
                        @org.springframework.data.repository.query.Param("projectId") String projectId,
                        @org.springframework.data.repository.query.Param("eventId") String eventId);

        // Character -> Event (PARTICIPATED_IN) Edge 생성
        @org.springframework.data.neo4j.repository.query.Query("MATCH (c:Character {projectId: $projectId, name: $characterName}) "
                        +
                        "WITH c LIMIT 1 " +
                        "MATCH (e:Event) WHERE (e.project_id = $projectId OR e.projectId = $projectId) AND e.eventId = $eventId "
                        +
                        "WITH c, e LIMIT 1 " +
                        "MERGE (c)-[r:PARTICIPATED_IN]->(e) " +
                        "RETURN r")
        void createParticipationEdge(
                        @org.springframework.data.repository.query.Param("projectId") String projectId,
                        @org.springframework.data.repository.query.Param("characterName") String characterName,
                        @org.springframework.data.repository.query.Param("eventId") String eventId);

        // Event -> Setting (HAPPENED_AT) Edge 생성
        @org.springframework.data.neo4j.repository.query.Query("MATCH (e:Event) WHERE (e.project_id = $projectId OR e.projectId = $projectId) AND e.eventId = $eventId "
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

        // Character -> Event: participants 속성 기반 필터링 (Edge 데이터 대신 속성 사용)
        @org.springframework.data.neo4j.repository.query.Query("MATCH (c:Character {id: $characterId}) " +
                        "MATCH (e:Event) " +
                        "WHERE c.name IN e.participants AND (e.project_id = c.project_id OR e.projectId = c.project_id OR e.project_id = c.projectId OR e.projectId = c.projectId) "
                        +
                        "RETURN e")
        List<Event> findEventsByCharacterId(
                        @org.springframework.data.repository.query.Param("characterId") String characterId);

        // eventId 리스트로 직접 필터링 (성능 최적화)
        @org.springframework.data.neo4j.repository.query.Query("MATCH (e:Event) " +
                        "WHERE (e.project_id = $projectId OR e.projectId = $projectId) " +
                        "AND ANY(ref IN $eventRefs WHERE e.eventId = ref OR e.eventId ENDS WITH '_' + ref) " +
                        "RETURN e.id as id, e.eventId as eventId, e.narrativeSummary as narrativeSummary, " +
                        "e.eventType as eventType, e.description as description, e.participants as participants, " +
                        "e.chapter as chapter, e.sequenceOrder as sequenceOrder, e.importance as importance, " +
                        "e.locationRef as locationRef, e.documentId as documentId, coalesce(e.project_id, e.projectId) as projectId")
        List<Event> findEventsByProjectIdAndEventRefs(
                        @org.springframework.data.repository.query.Param("projectId") String projectId,
                        @org.springframework.data.repository.query.Param("eventRefs") List<String> eventRefs);

        void deleteByProjectId(String projectId);
}
