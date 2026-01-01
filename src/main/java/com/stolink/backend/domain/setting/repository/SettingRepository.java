package com.stolink.backend.domain.setting.repository;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.setting.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettingRepository extends JpaRepository<Setting, UUID> {

    List<Setting> findByProject(Project project);

    Optional<Setting> findByProjectAndSettingId(Project project, String settingId);

    Optional<Setting> findByProjectAndName(Project project, String name);

    void deleteAllByProject(Project project);
}
