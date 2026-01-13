package com.stolink.backend.domain.validation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.validation.entity.ValidationResult;

@Repository
public interface ValidationResultRepository extends JpaRepository<ValidationResult, UUID> {

    List<ValidationResult> findByDocumentId(UUID documentId);

    Optional<ValidationResult> findByJobId(String jobId);

    List<ValidationResult> findByDocumentIdOrderByCreatedAtDesc(UUID documentId);

    void deleteAllByDocumentId(UUID documentId);
}
