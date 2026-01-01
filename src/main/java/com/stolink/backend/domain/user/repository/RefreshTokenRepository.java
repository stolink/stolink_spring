package com.stolink.backend.domain.user.repository;

import com.stolink.backend.domain.user.entity.RefreshToken;
import com.stolink.backend.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * 토큰 문자열로 RefreshToken 조회
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * 사용자의 모든 RefreshToken 조회
     */
    List<RefreshToken> findAllByUser(User user);

    /**
     * 사용자의 모든 RefreshToken 삭제 (전체 로그아웃)
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.user = :user")
    void deleteAllByUser(@Param("user") User user);

    /**
     * 특정 토큰 삭제
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.token = :token")
    void deleteByToken(@Param("token") String token);

    /**
     * 만료된 토큰 정리 (배치 작업용)
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);

    /**
     * 사용자의 유효한 토큰 개수 조회 (디바이스 수 제한용)
     */
    @Query("SELECT COUNT(r) FROM RefreshToken r WHERE r.user = :user AND r.expiresAt > :now")
    long countActiveTokensByUser(@Param("user") User user, @Param("now") LocalDateTime now);
}
