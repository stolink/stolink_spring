package com.stolink.backend.domain.draft.repository;

import com.stolink.backend.domain.draft.entity.Draft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface DraftRepository extends JpaRepository<Draft, UUID> {
    
    @Modifying
    @Query("DELETE FROM Draft d WHERE d.expiresAt < :now")
    void deleteExpiredDrafts(@Param("now") LocalDateTime now);
}
