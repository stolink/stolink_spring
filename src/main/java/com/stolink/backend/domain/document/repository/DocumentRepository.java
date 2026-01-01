package com.stolink.backend.domain.document.repository;

import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

        List<Document> findByProject(Project project);

        /**
         * 특정 부모 폴더의 직계 자식 중 지정된 타입의 문서를 페이징하여 조회
         * 무한 스크롤 (통합뷰) 지원용
         */
        Page<Document> findByParentAndTypeOrderByOrderAsc(Document parent, Document.DocumentType type,
                        Pageable pageable);

        List<Document> findByProjectAndParentIsNullOrderByOrder(Project project);

        List<Document> findByParentOrderByOrder(Document parent);

        Optional<Document> findByIdAndProject(UUID id, Project project);

        @Query("SELECT d FROM Document d WHERE d.project = :project AND d.parent IS NULL ORDER BY d.order")
        List<Document> findRootDocuments(@Param("project") Project project);

        @Query("SELECT SUM(d.wordCount) FROM Document d WHERE d.project = :project")
        Long sumWordCountByProject(@Param("project") Project project);

        @Query("SELECT COUNT(d) FROM Document d WHERE d.project = :project AND d.type = 'TEXT'")
        Long countTextDocumentsByProject(@Param("project") Project project);

        @Query("SELECT d FROM Document d LEFT JOIN FETCH d.parent WHERE d.project = :project ORDER BY d.order ASC")
        List<Document> findByProjectWithParent(@Param("project") Project project);

        void deleteAllByProject(Project project);

        // === 대용량 분석 아키텍처 관련 메서드 ===

        /**
         * 프로젝트 내 TEXT 타입 문서 중 특정 분석 상태인 문서 수 조회
         */
        @Query("SELECT COUNT(d) FROM Document d WHERE d.project.id = :projectId AND d.type = 'TEXT' AND d.analysisStatus = :status")
        long countByProjectIdAndTypeTextAndAnalysisStatus(
                        @Param("projectId") UUID projectId,
                        @Param("status") Document.AnalysisStatus status);

        /**
         * 분석 상태가 FAILED이고 재시도 횟수가 maxRetry 미만인 문서 조회
         */
        @Query("SELECT d FROM Document d WHERE d.analysisStatus = 'FAILED' AND d.analysisRetryCount < :maxRetry")
        List<Document> findFailedDocumentsForRetry(@Param("maxRetry") int maxRetry);

        /**
         * 프로젝트 ID로 TEXT 타입 문서 조회 (분석 대상)
         */
        @Query("SELECT d FROM Document d WHERE d.project.id = :projectId AND d.type = 'TEXT' ORDER BY d.order")
        List<Document> findTextDocumentsByProjectId(@Param("projectId") UUID projectId);

        /**
         * 프로젝트 내 TEXT 문서 총 수 조회
         */
        @Query("SELECT COUNT(d) FROM Document d WHERE d.project.id = :projectId AND d.type = 'TEXT'")
        long countTextDocumentsByProjectId(@Param("projectId") UUID projectId);

        /**
         * 원고 업로드 시 다음 order 값 조회
         */
        @Query("SELECT MAX(d.order) FROM Document d WHERE d.project = :project AND ((:parent IS NULL AND d.parent IS NULL) OR d.parent = :parent)")
        Optional<Integer> findMaxOrderByProjectAndParent(@Param("project") Project project,
                        @Param("parent") Document parent);

        @Query("SELECT SUBSTRING(d.content, :start, :length) FROM Document d WHERE d.id = :id")
        String findContentPart(@Param("id") UUID id, @Param("start") int start, @Param("length") int length);
}
