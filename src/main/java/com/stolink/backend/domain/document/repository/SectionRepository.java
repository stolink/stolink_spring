package com.stolink.backend.domain.document.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.entity.Section;

/**
 * Section Repository
 * 
 * 문서 섹션(RAG 청크) 데이터 접근 인터페이스
 */
@Repository
public interface SectionRepository extends JpaRepository<Section, UUID> {
    
    /**
     * 특정 문서의 모든 섹션 조회
     */
    List<Section> findByDocument(Document document);
    
    /**
     * 특정 문서의 섹션을 순서대로 조회
     */
    List<Section> findByDocumentOrderBySequenceOrder(Document document);
}
