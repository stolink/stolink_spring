package com.stolink.backend.domain.ai.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.stolink.backend.domain.ai.entity.DocumentSummary;

/**
 * DocumentSummary Repository (읽기 전용)
 * 
 * FastAPI AI Backend에서 데이터를 쓰며, Spring에서는 읽기 전용으로 사용합니다.
 */
@Repository
public interface DocumentSummaryRepository extends JpaRepository<DocumentSummary, UUID> {

    /**
     * 특정 문서의 특정 레벨 요약 조회
     */
    Optional<DocumentSummary> findByDocumentIdAndLevel(UUID documentId, Integer level);

    /**
     * 특정 문서의 모든 레벨 요약 조회
     */
    List<DocumentSummary> findByDocumentId(UUID documentId);

    /**
     * 특정 프로젝트의 특정 레벨 요약 전체 조회
     */
    List<DocumentSummary> findByProjectIdAndLevel(UUID projectId, Integer level);

    /**
     * 특정 프로젝트의 모든 요약 조회
     */
    List<DocumentSummary> findByProjectId(UUID projectId);

    /**
     * 특정 프로젝트의 모든 챕터 요약 조회 (level=3)
     */
    default List<DocumentSummary> findChapterSummariesByProjectId(UUID projectId) {
        return findByProjectIdAndLevel(projectId, 3);
    }

    /**
     * 특정 프로젝트의 전체 소설 요약 조회 (level=1)
     */
    default Optional<DocumentSummary> findNovelSummaryByProjectId(UUID projectId) {
        return findByProjectIdAndLevel(projectId, 1).stream().findFirst();
    }
}
