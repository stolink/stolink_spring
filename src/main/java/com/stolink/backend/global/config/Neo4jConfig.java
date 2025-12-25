package com.stolink.backend.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@Configuration
@EnableNeo4jRepositories(basePackages = "com.stolink.backend.domain.character.repository")
public class Neo4jConfig {
    // Neo4j configuration is handled by application.yml
}
