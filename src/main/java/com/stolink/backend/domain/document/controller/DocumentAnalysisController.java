package com.stolink.backend.domain.document.controller;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stolink.backend.domain.ai.dto.AnalysisStatusUpdateDTO;
import com.stolink.backend.domain.ai.entity.AnalysisJob;
import com.stolink.backend.domain.ai.repository.AnalysisJobRepository;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.entity.Document.AnalysisStatus;
import com.stolink.backend.domain.document.repository.DocumentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 문서 분석 상태 관리 컨트롤러
 * 
 * Python AI Worker가 분석 상태를 업데이트할 때 사용합니다.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentAnalysisController {

        private final DocumentRepository documentRepository;
        private final com.stolink.backend.domain.document.service.DocumentService documentService;
        private final com.stolink.backend.domain.ai.service.DocumentAnalysisPublisher documentAnalysisPublisher;
        private final AnalysisJobRepository analysisJobRepository;

        @org.springframework.web.bind.annotation.PostMapping("/{id}/analyze")
        public ResponseEntity<?> analyzeDocument(
                        @org.springframework.security.core.annotation.AuthenticationPrincipal UUID userId,
                        @PathVariable UUID id,
                        @RequestBody(required = false) Map<String, String> body) {

                // 1. 문서 조회 및 권한 검증
                Document document = documentService.getDocument(userId, id);

                // 2. AnalysisJob 생성 (AI 콜백에서 document_id로 조회할 수 있도록)
                // 동일 jobId로 기존 Job이 있으면 삭제 후 새로 생성 (중복 방지)
                String jobId = document.getId().toString();
                analysisJobRepository.findByJobId(jobId).ifPresent(existingJob -> {
                        log.info("기존 AnalysisJob 삭제: jobId={}", jobId);
                        analysisJobRepository.delete(existingJob);
                });

                AnalysisJob job = AnalysisJob.builder()
                                .jobId(jobId)
                                .project(document.getProject())
                                .documentId(document.getId())
                                .status(AnalysisJob.JobStatus.PENDING)
                                .build();
                analysisJobRepository.save(job);
                log.info("AnalysisJob 생성 완료: jobId={}, documentId={}", job.getJobId(), document.getId());

                // 3. 분석 유형 확인 (기본값: full_manuscript)
                String analysisType = (body != null) ? body.getOrDefault("analysis_type", "full_manuscript")
                                : "full_manuscript";

                // 4. 분석 요청 발행
                documentAnalysisPublisher.publishAnalysisForDocument(document, analysisType);

                return ResponseEntity.ok(Map.of(
                                "documentId", id,
                                "message", "문서 분석 요청이 대기열에 등록되었습니다.",
                                "status", Document.AnalysisStatus.PENDING));
        }

        /**
         * 문서 분석 상태 업데이트
         * 
         * Python Consumer가 메시지 수신 시 PROCESSING 상태로 변경할 때 사용합니다.
         * 
         * @param id        문서 ID
         * @param updateDTO 상태 업데이트 정보
         * @return 업데이트 결과
         */
        @PatchMapping("/{id}/analysis-status")
        public ResponseEntity<?> updateAnalysisStatus(
                        @PathVariable UUID id,
                        @RequestBody AnalysisStatusUpdateDTO updateDTO) {

                Document document = documentRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + id));

                AnalysisStatus previousStatus = document.getAnalysisStatus();
                document.updateAnalysisStatus(updateDTO.getStatus());
                documentRepository.save(document);

                log.info("문서 {} 분석 상태 변경: {} -> {} (trace: {})",
                                id, previousStatus, updateDTO.getStatus(), updateDTO.getTraceId());

                return ResponseEntity.ok(Map.of(
                                "documentId", id,
                                "previousStatus", previousStatus,
                                "currentStatus", updateDTO.getStatus(),
                                "message", "분석 상태가 업데이트되었습니다."));
        }

        /**
         * 문서 분석 상태 조회
         * 
         * @param id 문서 ID
         * @return 현재 분석 상태
         */
        @GetMapping("/{id}/analysis-status")
        public ResponseEntity<?> getAnalysisStatus(@PathVariable UUID id) {
                Document document = documentRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + id));

                return ResponseEntity.ok(Map.of(
                                "documentId", id,
                                "analysisStatus", document.getAnalysisStatus(),
                                "retryCount", document.getAnalysisRetryCount()));
        }
}
