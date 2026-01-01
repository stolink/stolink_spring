package com.stolink.backend.domain.document.controller;

import com.stolink.backend.domain.document.dto.CreateDocumentRequest;
import com.stolink.backend.domain.document.dto.DocumentTreeResponse;
import com.stolink.backend.domain.document.dto.DocumentResponse;
import com.stolink.backend.domain.document.dto.UpdateDocumentRequest;
import com.stolink.backend.domain.document.dto.ReorderDocumentsRequest;
import com.stolink.backend.domain.document.dto.BulkUpdateRequest;
import com.stolink.backend.domain.document.dto.ManuscriptUploadRequest;
import com.stolink.backend.domain.document.dto.ManuscriptJobResponse;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.service.DocumentService;
import com.stolink.backend.domain.document.service.ManuscriptJobService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final ManuscriptJobService manuscriptJobService;

    @GetMapping("/projects/{pid}/documents")
    public ApiResponse<List<DocumentTreeResponse>> getDocuments(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID pid) {
        List<DocumentTreeResponse> tree = documentService.getDocumentTree(userId, pid);
        return ApiResponse.ok(tree);
    }

    /**
     * 특정 폴더의 직계 자식 문서를 페이징하여 조회 (무한 스크롤용)
     * TEXT 타입 문서만 반환, order 기준 오름차순
     */
    @GetMapping("/documents/{folderId}/children")
    public ApiResponse<Page<DocumentTreeResponse>> getChildren(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // size 최대값 제한 (100)
        int limitedSize = Math.min(size, 100);

        Pageable pageable = PageRequest.of(page, limitedSize, Sort.by("order").ascending());
        Page<DocumentTreeResponse> result = documentService.getChildren(userId, folderId, pageable);
        return ApiResponse.ok(result);
    }

    @PostMapping("/projects/{pid}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentTreeResponse> createDocument(
            @AuthenticationPrincipal UUID userId,
            @RequestBody CreateDocumentRequest request) {
        DocumentTreeResponse document = documentService.createDocument(userId, request);
        return ApiResponse.created(document);
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<DocumentResponse> getDocument(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        Document document = documentService.getDocument(userId, id);
        return ApiResponse.ok(DocumentResponse.from(document));
    }

    @GetMapping("/documents/{id}/content")
    public ApiResponse<Map<String, String>> getContent(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        Document document = documentService.getDocument(userId, id);
        return ApiResponse.ok(Map.of("content", document.getContent() != null ? document.getContent() : ""));
    }

    @GetMapping("/documents/{id}/content/page")
    public ApiResponse<Map<String, Object>> getDocumentContentPage(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10000") int size) {
        return ApiResponse.ok(documentService.getPagedContent(userId, id, page, size));
    }

    @PatchMapping("/documents/{id}")
    public ApiResponse<DocumentResponse> updateDocument(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody UpdateDocumentRequest request) {
        Document document = documentService.updateDocument(userId, id, request);
        return ApiResponse.ok(DocumentResponse.from(document));
    }

    @PatchMapping("/documents/{id}/content")
    public ApiResponse<Map<String, Object>> updateContent(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String content = body.get("content");
        Document document = documentService.updateDocumentContent(userId, id, content);

        return ApiResponse.ok(Map.of(
                "id", document.getId(),
                "wordCount", document.getWordCount(),
                "updatedAt", document.getUpdatedAt()));
    }

    @DeleteMapping("/documents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        documentService.deleteDocument(userId, id);
    }

    @PostMapping("/documents/reorder")
    public ApiResponse<Void> reorderDocuments(
            @AuthenticationPrincipal UUID userId,
            @RequestBody ReorderDocumentsRequest request) {
        documentService.reorderDocuments(userId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/documents/bulk-update")
    public ApiResponse<Void> bulkUpdateDocuments(
            @AuthenticationPrincipal UUID userId,
            @RequestBody BulkUpdateRequest request) {
        documentService.bulkUpdateDocuments(userId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/projects/{pid}/manuscript/upload")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<ManuscriptJobResponse> uploadManuscript(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID pid,
            @RequestBody ManuscriptUploadRequest request) {
        request.setProjectId(pid);
        ManuscriptJobResponse job = manuscriptJobService.createJob(userId, pid, request);
        // 비동기로 처리 시작
        manuscriptJobService.processJobAsync(job.getJobId());
        return ApiResponse.accepted(job);
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<ManuscriptJobResponse> getJobStatus(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID jobId) {
        ManuscriptJobResponse job = manuscriptJobService.getJobStatus(userId, jobId);
        return ApiResponse.ok(job);
    }
}
