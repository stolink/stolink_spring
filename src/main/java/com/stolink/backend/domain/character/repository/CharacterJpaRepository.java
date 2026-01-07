package com.stolink.backend.domain.character.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stolink.backend.domain.character.entity.CharacterEntity;
import com.stolink.backend.domain.project.entity.Project;

public interface CharacterJpaRepository extends JpaRepository<CharacterEntity, UUID> {
    Optional<CharacterEntity> findByProjectAndName(Project project, String name);

    // characterId (AI 백엔드에서 부여한 ID)로 조회
    Optional<CharacterEntity> findByCharacterId(String characterId);

    // 중복 캐릭터 존재 시에도 안전하게 조회
    java.util.List<CharacterEntity> findAllByProjectAndName(Project project, String name);

    void deleteAllByProject(Project project);
}
