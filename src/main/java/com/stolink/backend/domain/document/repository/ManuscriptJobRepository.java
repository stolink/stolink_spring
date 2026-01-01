package com.stolink.backend.domain.document.repository;

import com.stolink.backend.domain.document.entity.ManuscriptJob;
import com.stolink.backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ManuscriptJobRepository extends JpaRepository<ManuscriptJob, UUID> {

    List<ManuscriptJob> findByProjectOrderByCreatedAtDesc(Project project);

    List<ManuscriptJob> findByUserIdAndStatusIn(UUID userId, List<ManuscriptJob.JobStatus> statuses);

    Optional<ManuscriptJob> findByIdAndUserId(UUID id, UUID userId);
}
