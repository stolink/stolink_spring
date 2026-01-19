package com.stolink.backend.domain.draft.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

/**
 * Bulk Draft 생성 요청 DTO
 * 다중 섹션을 한 번에 배포하거나, 여러 섹션을 병합하여 배포할 때 사용
 */
public record DraftBulkCreateRequest(
        // 배포할 Document ID 목록 (필수)
        @NotEmpty(message = "documentIds는 최소 1개 이상 필요합니다") List<String> documentIds,

        @NotBlank(message = "projectId is required") String projectId,

        // 병합 배포 시 사용할 제목 (병합 모드에서 필수)
        String title,

        // 병합된 컨텐츠 (병합 모드에서 필수)
        String content,

        // 인물관계도 스냅샷 데이터
        Map<String, Object> graphSnapshot,

        // 병합 모드 여부 (true: 모든 섹션을 하나의 에피소드로 병합)
        boolean isMerged,

        // Work 생성용 필드 (storead에서 사용)
        @NotBlank(message = "workTitle is required for publishing") String workTitle,

        String workSynopsis,
        String workGenre,
        String workCoverImage) {
}
