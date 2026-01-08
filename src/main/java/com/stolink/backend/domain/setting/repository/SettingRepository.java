package com.stolink.backend.domain.setting.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.setting.entity.SettingEntity;

/**
 * 설정(장소) 리포지토리 (PostgreSQL)
 */
@Repository
public interface SettingRepository extends JpaRepository<SettingEntity, UUID> {

    Optional<SettingEntity> findByProjectAndName(Project project, String name);

    List<SettingEntity> findByProject(Project project);

    List<SettingEntity> findByProjectAndNameIn(Project project, java.util.Collection<String> names);
}
