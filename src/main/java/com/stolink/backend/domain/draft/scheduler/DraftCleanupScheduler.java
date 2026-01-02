package com.stolink.backend.domain.draft.scheduler;

import com.stolink.backend.domain.draft.repository.DraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DraftCleanupScheduler {

    private final DraftRepository draftRepository;

    @Scheduled(fixedRate = 3600000) // 1시간마다 실행
    @Transactional
    public void cleanupExpiredDrafts() {
        LocalDateTime now = LocalDateTime.now();
        draftRepository.deleteExpiredDrafts(now);
        log.info("Expired drafts cleanup executed at {}", now);
    }
}
