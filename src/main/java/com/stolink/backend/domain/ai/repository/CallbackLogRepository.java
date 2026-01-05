package com.stolink.backend.domain.ai.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.stolink.backend.domain.ai.entity.CallbackLog;

/**
 * 콜백 로그 Repository
 */
public interface CallbackLogRepository extends JpaRepository<CallbackLog, UUID> {

    /**
     * jobId로 이미 처리된 콜백인지 확인
     */
    boolean existsByJobId(String jobId);

    /**
     * jobId로 콜백 로그 조회
     */
    Optional<CallbackLog> findByJobId(String jobId);
}
