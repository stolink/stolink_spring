package com.stolink.backend.domain.draft.service;

import com.stolink.backend.domain.draft.entity.Draft;
import com.stolink.backend.domain.draft.repository.DraftRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import com.stolink.backend.global.infrastructure.storead.StoreadClient;
import com.stolink.backend.global.infrastructure.storead.dto.StoreadPublishRequest;
import com.stolink.backend.global.infrastructure.storead.dto.StoreadPublishResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublishService {

    private final DraftRepository draftRepository;
    private final StoreadClient storeadClient;

    @Transactional
    public void publishDraft(UUID userId, UUID draftId) {
        Draft draft = draftRepository.findById(draftId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft", "id", draftId));

        if (!draft.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Draft ownership mismatch");
        }

        if (draft.getPublishStatus() == Draft.PublishStatus.PUBLISHED) {
            throw new IllegalStateException("Draft is already published");
        }

        try {
            draft.updatePublishStatus(Draft.PublishStatus.PUBLISHING);
            draftRepository.saveAndFlush(draft);

            StoreadPublishRequest request = StoreadPublishRequest.builder()
                    .authorEmail(draft.getUser().getEmail())
                    .workTitle(draft.getWorkTitle())
                    .workSynopsis(draft.getWorkSynopsis())
                    .workGenre(draft.getWorkGenre())
                    .workCoverUrl(draft.getWorkCoverUrl())
                    .chapterTitle(draft.getTitle())
                    .chapterContent(draft.getContent())
                    .build();

            StoreadPublishResponse response = storeadClient.publish(request);

            draft.updatePublishResult(response.workId(), response.chapterId());
            log.info("Successfully published draft {} to Storead. WorkId: {}, ChapterId: {}", 
                    draftId, response.workId(), response.chapterId());

        } catch (Exception e) {
            log.error("Failed to publish draft {} to Storead", draftId, e);
            draft.updatePublishStatus(Draft.PublishStatus.FAILED);
            throw e;
        }
    }
}
