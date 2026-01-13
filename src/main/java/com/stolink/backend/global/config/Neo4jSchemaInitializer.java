package com.stolink.backend.global.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class Neo4jSchemaInitializer implements ApplicationRunner {

    private final Driver driver;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Session session = driver.session()) {
            log.info("Initializing Neo4j schema and indexes...");

            // Event 노드 인덱스
            createIndex(session, "event_projectId", "Event", "project_id");
            createIndex(session, "event_eventId", "Event", "eventId");

            // Character 노드 인덱스
            createIndex(session, "character_projectId", "Character", "project_id");
            createIndex(session, "character_id", "Character", "id");

            log.info("Neo4j schema initialization completed.");
        } catch (Exception e) {
            log.error("Failed to initialize Neo4j schema", e);
        }
    }

    private void createIndex(Session session, String indexName, String label, String property) {
        String query = String.format("CREATE INDEX %s IF NOT EXISTS FOR (n:%s) ON (n.%s)",
                indexName, label, property);
        try {
            session.run(query);
            log.info("Created index: {}", indexName);
        } catch (Exception e) {
            log.warn("Failed to create index {}: {}", indexName, e.getMessage());
        }
    }
}
