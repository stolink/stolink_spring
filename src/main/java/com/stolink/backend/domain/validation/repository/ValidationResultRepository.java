package com.stolink.backend.domain.validation.repository;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.validation.entity.ValidationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ValidationResultRepository extends JpaRepository<ValidationResult, UUID> {

    List<ValidationResult> findByProject(Project project);

    Optional<ValidationResult> findByJobId(String jobId);

    List<ValidationResult> findByProjectOrderByCreatedAtDesc(Project project);

    void deleteAllByProject(Project project);
}
