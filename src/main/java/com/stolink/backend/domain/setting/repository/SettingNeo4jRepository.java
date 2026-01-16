package com.stolink.backend.domain.setting.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.setting.node.Setting;

@Repository
public interface SettingNeo4jRepository extends Neo4jRepository<Setting, String> {

    @org.springframework.data.neo4j.repository.query.Query("MATCH (s:Setting) WHERE s.project_id = $projectId OR s.projectId = $projectId RETURN s")
    List<Setting> findByProjectId(@org.springframework.data.repository.query.Param("projectId") String projectId);

    @org.springframework.data.neo4j.repository.query.Query("MATCH (s:Setting) WHERE (s.project_id = $projectId OR s.projectId = $projectId) AND s.name = $name RETURN s LIMIT 1")
    Optional<Setting> findByProjectIdAndName(
            @org.springframework.data.repository.query.Param("projectId") String projectId,
            @org.springframework.data.repository.query.Param("name") String name);

    // 중복 안전 조회
    @org.springframework.data.neo4j.repository.query.Query("MATCH (s:Setting) WHERE (s.project_id = $projectId OR s.projectId = $projectId) AND s.name = $name RETURN s")
    List<Setting> findAllByProjectIdAndName(
            @org.springframework.data.repository.query.Param("projectId") String projectId,
            @org.springframework.data.repository.query.Param("name") String name);

    @org.springframework.data.neo4j.repository.query.Query("MATCH (s:Setting) WHERE (s.project_id = $projectId OR s.projectId = $projectId) AND s.settingId = $settingId RETURN s LIMIT 1")
    Optional<Setting> findByProjectIdAndSettingId(
            @org.springframework.data.repository.query.Param("projectId") String projectId,
            @org.springframework.data.repository.query.Param("settingId") String settingId);

    @org.springframework.data.neo4j.repository.query.Query("MATCH (s:Setting) WHERE s.project_id = $projectId OR s.projectId = $projectId DETACH DELETE s")
    void deleteByProjectId(@org.springframework.data.repository.query.Param("projectId") String projectId);
}
