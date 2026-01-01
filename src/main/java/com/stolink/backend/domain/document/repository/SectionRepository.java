package com.stolink.backend.domain.document.repository;

import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Section Repository
 * 
 * AI 분석으로 생성된 Section(의미적 분할 단위)을 관리합니다.
 */
@Repository
public interface SectionRepository extends JpaRepository<Section, UUID> {

    /**
     * 특정 문서의 모든 Section을 순서대로 조회
     */
    List<Section> findByDocumentOrderBySequenceOrderAsc(Document document);

    /**
     * 문서 ID로 Section 조회
     */
    List<Section> findByDocumentIdOrderBySequenceOrderAsc(UUID documentId);

    /**
     * 특정 문서의 Section 수 조회
     */
    long countByDocument(Document document);

    /**
     * 특정 문서의 Section 수 조회 (ID 기반)
     */
    long countByDocumentId(UUID documentId);

    /**
     * 프로젝트 내 모든 Section 조회 (Document를 통해)
     */
    @Query("SELECT s FROM Section s WHERE s.document.project.id = :projectId ORDER BY s.document.order, s.sequenceOrder")
    List<Section> findAllByProjectId(@Param("projectId") UUID projectId);

    /**
     * 특정 문서의 모든 Section 삭제
     */
    void deleteAllByDocument(Document document);

    /**
     * 문서 ID로 모든 Section 삭제
     */
    void deleteAllByDocumentId(UUID documentId);
}
