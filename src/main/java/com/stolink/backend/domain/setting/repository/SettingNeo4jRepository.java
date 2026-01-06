package com.stolink.backend.domain.setting.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.setting.node.Setting;

@Repository
public interface SettingNeo4jRepository extends Neo4jRepository<Setting, String> {

    List<Setting> findByProjectId(String projectId);

    Optional<Setting> findByProjectIdAndName(String projectId, String name);

    // 중복 안전 조회
    List<Setting> findAllByProjectIdAndName(String projectId, String name);

    Optional<Setting> findByProjectIdAndSettingId(String projectId, String settingId);

    void deleteByProjectId(String projectId);
}
