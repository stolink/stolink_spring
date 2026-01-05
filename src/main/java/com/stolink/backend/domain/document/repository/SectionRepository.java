package com.stolink.backend.domain.document.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stolink.backend.domain.document.entity.Section;

public interface SectionRepository extends JpaRepository<Section, UUID> {

    @Modifying
    @Query("DELETE FROM Section s WHERE s.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);
}
