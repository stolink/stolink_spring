package com.stolink.backend.domain.character.repository;

import com.stolink.backend.domain.character.entity.CharacterEntity;
import com.stolink.backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CharacterJpaRepository extends JpaRepository<CharacterEntity, UUID> {
    Optional<CharacterEntity> findByProjectAndName(Project project, String name);
}
