package com.stolink.backend.domain.ai.repository;

import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {

    Optional<AnalysisJob> findByJobId(String jobId);

    List<AnalysisJob> findByProject(Project project);

    List<AnalysisJob> findByProjectOrderByCreatedAtDesc(Project project);

    List<AnalysisJob> findByStatus(AnalysisJob.JobStatus status);
}
