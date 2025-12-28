package com.stolink.backend.domain.share.repository;

import com.stolink.backend.domain.share.entity.Share;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShareRepository extends JpaRepository<Share, UUID> {
    Optional<Share> findByProjectId(UUID projectId);

    @Query("SELECT s FROM Share s JOIN FETCH s.project p JOIN FETCH p.user WHERE p.id = :projectId")
    Optional<Share> findByProjectIdWithUser(@Param("projectId") UUID projectId);
}
