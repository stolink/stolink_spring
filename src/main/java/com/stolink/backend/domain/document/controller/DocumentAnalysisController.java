package com.stolink.backend.domain.document.controller;

import com.stolink.backend.domain.ai.dto.AnalysisStatusUpdateDTO;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.entity.Document.AnalysisStatus;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

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
