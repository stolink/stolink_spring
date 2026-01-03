package com.stolink.backend.domain.event.repository;

import com.stolink.backend.domain.event.node.Event;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventNeo4jRepository extends Neo4jRepository<Event, String> {

    List<Event> findByProjectId(String projectId);

    List<Event> findByProjectIdOrderByImportanceDesc(String projectId);

    Optional<Event> findByProjectIdAndEventId(String projectId, String eventId);

    void deleteByProjectId(String projectId);
}
