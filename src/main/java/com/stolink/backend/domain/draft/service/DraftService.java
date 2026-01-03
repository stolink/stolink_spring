package com.stolink.backend.domain.draft.service;

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
}
