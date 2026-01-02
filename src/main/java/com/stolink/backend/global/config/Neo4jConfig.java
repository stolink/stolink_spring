package com.stolink.backend.global.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@Configuration
@EnableNeo4jRepositories(
    basePackages = {
        "com.stolink.backend.domain.character.repository",
        "com.stolink.backend.domain.setting.repository",
        "com.stolink.backend.domain.event.repository"
    },
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = JpaRepository.class
    ),
    transactionManagerRef = "neo4jTransactionManager"
)
public class Neo4jConfig {

    @org.springframework.context.annotation.Bean("neo4jTransactionManager")
    public org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager transactionManager(
            org.neo4j.driver.Driver driver) {
        return new org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager(driver);
    }

    @org.springframework.context.annotation.Primary
    @org.springframework.context.annotation.Bean
    public org.springframework.data.neo4j.core.Neo4jTemplate neo4jTemplate(
            org.springframework.data.neo4j.core.Neo4jClient neo4jClient,
            org.springframework.data.neo4j.core.mapping.Neo4jMappingContext neo4jMappingContext,
            @org.springframework.beans.factory.annotation.Qualifier("neo4jTransactionManager") org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager transactionManager) {
        return new org.springframework.data.neo4j.core.Neo4jTemplate(neo4jClient, neo4jMappingContext,
                transactionManager);
    }
}
