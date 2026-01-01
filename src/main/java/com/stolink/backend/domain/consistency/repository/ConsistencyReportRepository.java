package com.stolink.backend.domain.consistency.repository;

import com.stolink.backend.domain.consistency.entity.ConsistencyReport;
import com.stolink.backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsistencyReportRepository extends JpaRepository<ConsistencyReport, UUID> {

    List<ConsistencyReport> findByProject(Project project);

    Optional<ConsistencyReport> findByJobId(String jobId);

    List<ConsistencyReport> findByProjectOrderByCreatedAtDesc(Project project);

    void deleteAllByProject(Project project);
}
