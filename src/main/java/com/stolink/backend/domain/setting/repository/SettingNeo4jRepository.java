package com.stolink.backend.domain.setting.repository;

import com.stolink.backend.domain.setting.node.Setting;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SettingNeo4jRepository extends Neo4jRepository<Setting, String> {

    List<Setting> findByProjectId(String projectId);

    Optional<Setting> findByProjectIdAndName(String projectId, String name);

    Optional<Setting> findByProjectIdAndSettingId(String projectId, String settingId);

    void deleteByProjectId(String projectId);
}
