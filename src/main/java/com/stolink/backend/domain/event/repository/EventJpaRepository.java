package com.stolink.backend.domain.event.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stolink.backend.domain.event.entity.EventEntity;
import com.stolink.backend.domain.project.entity.Project;

public interface EventJpaRepository extends JpaRepository<EventEntity, UUID> {
    Optional<EventEntity> findByProjectAndName(Project project, String name);

    // 중복 안전 조회
    List<EventEntity> findAllByProjectAndName(Project project, String name);

    List<EventEntity> findByProject(Project project);
}
