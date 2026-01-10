package com.stolink.backend.domain.ai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.project.entity.Project;

@Repository
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {

    Optional<AnalysisJob> findByJobId(String jobId);

    List<AnalysisJob> findByProject(Project project);

    List<AnalysisJob> findByProjectOrderByCreatedAtDesc(Project project);

    List<AnalysisJob> findByStatus(AnalysisJob.JobStatus status);

    List<AnalysisJob> findByDocumentIdAndTraceIdAndStatus(java.util.UUID documentId, String traceId,
            AnalysisJob.JobStatus status);

    Optional<AnalysisJob> findByTraceId(String traceId);

    void deleteAllByProject(Project project);
}
