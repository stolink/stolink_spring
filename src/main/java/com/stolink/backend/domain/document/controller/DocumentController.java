package com.stolink.backend.domain.document.controller;

import com.stolink.backend.domain.document.dto.CreateDocumentRequest;
import com.stolink.backend.domain.document.dto.DocumentTreeResponse;
import com.stolink.backend.domain.document.dto.UpdateDocumentRequest;
import com.stolink.backend.domain.document.dto.ReorderDocumentsRequest;
import com.stolink.backend.domain.document.dto.BulkUpdateRequest;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.service.DocumentService;
import com.stolink.backend.global.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping("/projects/{pid}/documents")
    public ApiResponse<List<DocumentTreeResponse>> getDocuments(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID pid) {
        List<DocumentTreeResponse> tree = documentService.getDocumentTree(userId, pid);
        return ApiResponse.ok(tree);
    }

    @PostMapping("/projects/{pid}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentTreeResponse> createDocument(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody CreateDocumentRequest request) {
        DocumentTreeResponse document = documentService.createDocument(userId, request);
        return ApiResponse.created(document);
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<Document> getDocument(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        Document document = documentService.getDocument(userId, id);
        return ApiResponse.ok(document);
    }

    @GetMapping("/documents/{id}/content")
    public ApiResponse<Map<String, String>> getContent(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        Document document = documentService.getDocument(userId, id);
        return ApiResponse.ok(Map.of("content", document.getContent() != null ? document.getContent() : ""));
    }

    @PatchMapping("/documents/{id}")
    public ApiResponse<Document> updateDocument(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id,
            @RequestBody UpdateDocumentRequest request) {
        Document document = documentService.updateDocument(userId, id, request);
        return ApiResponse.ok(document);
    }

    @PatchMapping("/documents/{id}/content")
    public ApiResponse<Map<String, Object>> updateContent(
            @RequestHeader("X-User-Id") UUID userId,
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
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID id) {
        documentService.deleteDocument(userId, id);
    }

    @PostMapping("/documents/reorder")
    public ApiResponse<Void> reorderDocuments(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody ReorderDocumentsRequest request) {
        documentService.reorderDocuments(userId, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/documents/bulk-update")
    public ApiResponse<Void> bulkUpdateDocuments(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody BulkUpdateRequest request) {
        documentService.bulkUpdateDocuments(userId, request);
        return ApiResponse.ok(null);
    }
}
