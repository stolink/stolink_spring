package com.stolink.backend.domain.character.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stolink.backend.domain.character.entity.RelationshipEntity;

public interface RelationshipRepository extends JpaRepository<RelationshipEntity, UUID> {
    void deleteByProjectId(UUID projectId);
}
