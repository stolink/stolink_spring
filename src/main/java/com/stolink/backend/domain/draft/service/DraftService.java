package com.stolink.backend.domain.draft.service;

import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.draft.dto.DraftBulkCreateRequest;
import com.stolink.backend.domain.draft.dto.DraftCreateRequest;
import com.stolink.backend.domain.draft.dto.DraftDetailResponse;
import com.stolink.backend.domain.draft.dto.DraftResponse;
import com.stolink.backend.domain.draft.entity.Draft;
import com.stolink.backend.domain.draft.repository.DraftRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DraftService {

        private final DraftRepository draftRepository;
        private final UserRepository userRepository;
        private final DocumentRepository documentRepository;
        private final ProjectRepository projectRepository;

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
                                .workCoverImage(request.workCoverImage())
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

                // 1. 프로젝트 존재 여부 및 소유권 검증
                Project project = projectRepository.findById(UUID.fromString(request.projectId()))
                                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", request.projectId()));

                if (!project.getUser().getId().equals(userId)) {
                        throw new IllegalArgumentException("해당 프로젝트에 대한 접근 권한이 없습니다.");
                }

                // 2. Document 존재 여부 및 프로젝트 소속 검증
                List<UUID> documentUUIDs = request.documentIds().stream()
                                .map(UUID::fromString)
                                .toList();
                List<Document> documents = documentRepository.findAllById(documentUUIDs);

                if (documents.size() != request.documentIds().size()) {
                        throw new IllegalArgumentException("일부 문서를 찾을 수 없습니다. 유효한 문서 ID를 확인해주세요.");
                }

                // 모든 문서가 해당 프로젝트에 속하는지 확인
                boolean allBelongToProject = documents.stream()
                                .allMatch(doc -> doc.getProject().getId().equals(project.getId()));
                if (!allBelongToProject) {
                        throw new IllegalArgumentException("일부 문서가 해당 프로젝트에 속하지 않습니다.");
                }

                // 3. 병합 모드일 때 title과 content 필수 검증
                if (request.isMerged()) {
                        if (request.title() == null || request.title().isBlank()) {
                                throw new IllegalArgumentException("병합 배포 시 제목(title)은 필수입니다.");
                        }
                        if (request.content() == null || request.content().isBlank()) {
                                throw new IllegalArgumentException("병합 배포 시 내용(content)은 필수입니다.");
                        }
                }

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
                                .workCoverImage(request.workCoverImage())
                                .build();

                Draft savedDraft = draftRepository.save(draft);
                return DraftResponse.from(savedDraft);
        }
}
