package com.stolink.backend.domain.draft.service;

import com.stolink.backend.domain.draft.dto.DraftBulkCreateRequest;
import com.stolink.backend.domain.draft.dto.DraftCreateRequest;
import com.stolink.backend.domain.draft.dto.DraftDetailResponse;
import com.stolink.backend.domain.draft.dto.DraftResponse;
import com.stolink.backend.domain.draft.entity.Draft;
import com.stolink.backend.domain.draft.repository.DraftRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DraftService {

        private final DraftRepository draftRepository;
        private final UserRepository userRepository;

        @Transactional
        public DraftResponse createDraft(UUID userId, DraftCreateRequest request) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

                Draft draft = Draft.builder()
                                .user(user)
                                .documentId(request.documentId())
                                .projectId(request.projectId())
                                .title(request.title())
                                .content(request.content())
                                .graphSnapshot(request.graphSnapshot())
                                .workTitle(request.workTitle())
                                .workSynopsis(request.workSynopsis())
                                .workGenre(request.workGenre())
                                .workCoverUrl(request.workCoverUrl())
                                .build();

                Draft savedDraft = draftRepository.save(draft);
                return DraftResponse.from(savedDraft);
        }

        public DraftDetailResponse getDraft(UUID draftId) {
                Draft draft = draftRepository.findById(draftId)
                                .orElseThrow(() -> new ResourceNotFoundException("Draft", "id", draftId));
                return DraftDetailResponse.from(draft);
        }

        @Transactional
        public Draft getDraftEntity(UUID draftId) {
                return draftRepository.findByIdWithUser(draftId)
                                .orElseThrow(() -> new ResourceNotFoundException("Draft", "id", draftId));
        }

        @Transactional
        public void updatePublishStatus(UUID draftId, Draft.PublishStatus status) {
                Draft draft = draftRepository.findById(draftId)
                                .orElseThrow(() -> new ResourceNotFoundException("Draft", "id", draftId));
                draft.updatePublishStatus(status);
        }

        @Transactional
        public void updatePublishResult(UUID draftId, Long workId, Long chapterId) {
                Draft draft = draftRepository.findById(draftId)
                                .orElseThrow(() -> new ResourceNotFoundException("Draft", "id", draftId));
                draft.updatePublishResult(workId, chapterId);
        }

        /**
         * Bulk Draft 생성 - 다중 섹션을 한 번에 배포하거나 병합하여 배포
         * 
         * @param userId  사용자 ID
         * @param request Bulk 생성 요청 (documentIds 배열 포함)
         * @return 생성된 Draft 응답
         */
        @Transactional
        public DraftResponse createBulkDraft(UUID userId, DraftBulkCreateRequest request) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

                Draft draft = Draft.builder()
                                .user(user)
                                .documentIds(request.documentIds()) // 다중 Document ID
                                .documentId(!request.isMerged() && !request.documentIds().isEmpty()
                                                ? request.documentIds().get(0)
                                                : null) // 개별 배포 시 단일 ID 필드도 채움
                                .projectId(request.projectId())
                                .title(request.title())
                                .content(request.content())
                                .graphSnapshot(request.graphSnapshot())
                                .isMerged(request.isMerged()) // 병합 모드 여부
                                .workTitle(request.workTitle())
                                .workSynopsis(request.workSynopsis())
                                .workGenre(request.workGenre())
                                .workCoverUrl(request.workCoverUrl())
                                .build();

                Draft savedDraft = draftRepository.save(draft);
                return DraftResponse.from(savedDraft);
        }
}
