package com.stolink.backend.domain.draft.service;

import com.stolink.backend.domain.draft.entity.Draft;
import com.stolink.backend.global.infrastructure.storead.StoreadClient;
import com.stolink.backend.global.infrastructure.storead.dto.StoreadPublishRequest;
import com.stolink.backend.global.infrastructure.storead.dto.StoreadPublishResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishService {

    private final DraftService draftService;
    private final StoreadClient storeadClient;

    public void publishDraft(UUID userId, UUID draftId) {
        // 1. 상태 변경 및 검증 (트랜잭션 분리) - DraftService.getDraftEntity와 updatePublishStatus 사용
        Draft draft = draftService.getDraftEntity(draftId);

        if (!draft.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Draft ownership mismatch");
        }

        if (draft.getPublishStatus() == Draft.PublishStatus.PUBLISHED) {
            throw new IllegalStateException("Draft is already published");
        }

        draftService.updatePublishStatus(draftId, Draft.PublishStatus.PUBLISHING);

        try {
            // 2. 외부 API 호출을 위한 요청 데이터 생성 (메모리 내 작업)
            StoreadPublishRequest request = StoreadPublishRequest.builder()
                    .authorEmail(draft.getUser().getEmail())
                    .workTitle(draft.getWorkTitle())
                    .workSynopsis(draft.getWorkSynopsis())
                    .workGenre(draft.getWorkGenre())
                    .workCoverUrl(draft.getWorkCoverUrl())
                    .chapterTitle(draft.getTitle())
                    .chapterContent(draft.getContent())
                    .build();

            // 3. 외부 API 호출 (트랜잭션 없음)
            StoreadPublishResponse response = storeadClient.publish(request);

            // 4. 결과 반영 (트랜잭션 분리)
            draftService.updatePublishResult(draftId, response.workId(), response.chapterId());
            log.info("Successfully published draft {} to Storead. WorkId: {}, ChapterId: {}", 
                    draftId, response.workId(), response.chapterId());

        } catch (Exception e) {
            log.error("Failed to publish draft {} to Storead", draftId, e);
            draftService.updatePublishStatus(draftId, Draft.PublishStatus.FAILED);
            throw e;
        }
    }
}

