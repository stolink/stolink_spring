package com.stolink.backend.domain.share.repository;

import com.stolink.backend.domain.share.entity.Share;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShareRepository extends JpaRepository<Share, UUID> {
    Optional<Share> findByProjectId(UUID projectId);
}
