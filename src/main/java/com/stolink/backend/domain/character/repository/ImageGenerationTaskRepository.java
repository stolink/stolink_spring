package com.stolink.backend.domain.character.repository;

import com.stolink.backend.domain.character.entity.ImageGenerationTask;
import com.stolink.backend.domain.character.entity.ImageGenerationTask.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// 이미지 생성 작업 Repository
@Repository
public interface ImageGenerationTaskRepository extends JpaRepository<ImageGenerationTask, String> {
    
    // 사용자 ID로 작업 목록 조회
    List<ImageGenerationTask> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    // 프로젝트 ID로 작업 목록 조회
    List<ImageGenerationTask> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    
    // 캐릭터 ID로 작업 목록 조회
    List<ImageGenerationTask> findByCharacterIdOrderByCreatedAtDesc(UUID characterId);
    
    // 상태별 작업 목록 조회
    List<ImageGenerationTask> findByStatusOrderByCreatedAtDesc(TaskStatus status);
    
    // 재시도 가능한 작업 조회 (PENDING/FAILED 상태이면서 재시도 횟수 < 3)
    @Query("SELECT t FROM ImageGenerationTask t " +
           "WHERE (t.status = 'PENDING' OR t.status = 'FAILED') " +
           "AND t.retryCount < 3 " +
           "AND t.createdAt > :since " +
           "ORDER BY t.createdAt ASC")
    List<ImageGenerationTask> findRetryableTasks(LocalDateTime since);
    
    // 특정 시간 이후 생성된 미완료 작업 조회
    @Query("SELECT t FROM ImageGenerationTask t " +
           "WHERE t.status IN ('PENDING', 'SENT', 'PROCESSING') " +
           "AND t.createdAt > :since " +
           "ORDER BY t.createdAt DESC")
    List<ImageGenerationTask> findIncompleteTasks(LocalDateTime since);
    
    // 사용자의 특정 캐릭터에 대한 최근 작업 조회
    Optional<ImageGenerationTask> findFirstByUserIdAndCharacterIdOrderByCreatedAtDesc(
            UUID userId, UUID characterId);
}
