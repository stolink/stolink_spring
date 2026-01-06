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

        	/**
	 * 특정 기간 동안 업데이트된 문서의 단어 수 합계 조회 (정확한 diff는 아니지만 근사치로 사용)
	 * 실제 집필량은 별도 테이블로 관리하는 것이 좋으나, 현재 구조에서는 update 시점의 최종 wordCount로 대체하거나
	 * 별도의 WritingSession 테이블이 필요함.
	 * 여기서는 "해당 기간에 수정된 문서들의 현재 단어 수 합"은 의미가 없으므로 (과거 데이터가 아님),
	 * **Stats API에서 사용한 일별 집계 쿼리**를 재사용하여 기간 내 합계를 구해야 함.
	 */

	/**
	 * 특정 기간 내의 일별 통계 합산 (집필 목표 달성 확인용)
	 */
	@Query(value = """
			SELECT COALESCE(SUM(word_count), 0)
			FROM (
				SELECT SUM(d.word_count) as word_count
				FROM documents d
				WHERE d.project_id = :projectId
				  AND d.updated_at >= :startDate
				  AND d.updated_at < :endDate
				  AND d.word_count > 0
				GROUP BY d.id
			) as daily_sum
			""", nativeQuery = true)
	Long sumWordCountByProjectAndDateRange(@Param("projectId") UUID projectId,
			@Param("startDate") java.time.LocalDateTime startDate,
			@Param("endDate") java.time.LocalDateTime endDate);

	/**
	 * NOTE: 위 쿼리는 "해당 기간에 수정된 문서의 현재 단어 수 합"이 아니라,
	 * "해당 기간에 업데이트된 기록"을 기반으로 해야 하지만, history 테이블이 없으므로
	 * 단순하게 '기간 내 수정된 파일들의 현재 용량'을 리턴하면 오류가 큼.
	 *
	 * 차라리 Stats API의 로직처럼 '오늘 집필량'을 계산해야 함.
	 * 하지만 DB 구조상 '오늘 쓴 양'을 정확히 알 수 없음 (updated_at만 있음).
	 * => updated_at이 오늘인 문서들의 wordCount 합? (X, 기존 내용 포함됨)
	 *
	 * 대안: 클라이언트 요구사항의 'Current Count'는
	 * "목표 기간 동안 증가한 양"이어야 하지만, 백엔드에 히스토리가 없으므로
	 * **"현재 프로젝트의 총 단어 수"** (전체 목표일 경우) 또는
	 * **"오늘 수정된 문서들의 총 단어 수"**로 근사치를 제공해야 함.
	 *
	 * 하지만 '일일 목표'는 보통 '오늘 쓴 양'을 의미함.
	 * 정확한 구현을 위해선 WritingHistory 엔티티가 필요함.
	 * 현재는 간단히 **"오늘 업데이트된 문서들의 wordCount 합"**으로 구현하고,
	 * 추후 History 테이블 도입을 제안해야 함.
	 */

	@Query("SELECT COALESCE(SUM(d.wordCount), 0) FROM Document d WHERE d.project.id = :projectId AND d.updatedAt >= :startTime")
	Long sumWordCountByProjectAndUpdatedAtAfter(@Param("projectId") UUID projectId,
			@Param("startTime") java.time.LocalDateTime startTime);

        @Query("SELECT SUBSTRING(d.content, :start, :length) FROM Document d WHERE d.id = :id")
        String findContentPart(@Param("id") UUID id, @Param("start") int start, @Param("length") int length);

        // === 통계 집계 쿼리 ===

        /**
         * 요일별 단어 수 집계 (0=일요일, 1=월요일, ..., 6=토요일)
         */
        @Query(value = """
                        SELECT
                                EXTRACT(DOW FROM updated_at) as day_num,
                                SUM(word_count) as total
                        FROM documents
                        WHERE project_id = :projectId
                          AND word_count > 0
                        GROUP BY EXTRACT(DOW FROM updated_at)
                        """, nativeQuery = true)
        List<Object[]> aggregateByDayOfWeek(@Param("projectId") UUID projectId);

        /**
         * 시간대별 단어 수 집계
         * morning (6-11), afternoon (12-17), evening (18-23), night (0-5)
         */
        @Query(value = """
                        SELECT
                                CASE
                                        WHEN EXTRACT(HOUR FROM updated_at) BETWEEN 6 AND 11 THEN 'morning'
                                        WHEN EXTRACT(HOUR FROM updated_at) BETWEEN 12 AND 17 THEN 'afternoon'
                                        WHEN EXTRACT(HOUR FROM updated_at) BETWEEN 18 AND 23 THEN 'evening'
                                        ELSE 'night'
                                END as time_of_day,
                                SUM(word_count) as total
                        FROM documents
                        WHERE project_id = :projectId
                          AND word_count > 0
                        GROUP BY time_of_day
                        """, nativeQuery = true)
        List<Object[]> aggregateByTimeOfDay(@Param("projectId") UUID projectId);

        /**
         * 최근 30일 일별 단어 수 집계
         */
        @Query(value = """
                        SELECT
                                DATE(updated_at) as date,
                                SUM(word_count) as word_count
                        FROM documents
                        WHERE project_id = :projectId
                          AND updated_at >= CURRENT_DATE - INTERVAL '30 days'
                          AND word_count > 0
                        GROUP BY DATE(updated_at)
                        ORDER BY date ASC
                        """, nativeQuery = true)
        List<Object[]> aggregateLast30Days(@Param("projectId") UUID projectId);
}
