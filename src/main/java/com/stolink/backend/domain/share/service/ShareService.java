package com.stolink.backend.domain.share.service;

import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.share.dto.SharedDocumentResponse;
import com.stolink.backend.domain.share.dto.SharedProjectResponse;
import com.stolink.backend.domain.share.dto.ShareResponse;
import com.stolink.backend.domain.share.entity.Share;
import com.stolink.backend.domain.share.repository.ShareRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShareService {

    private final ShareRepository shareRepository;
    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public ShareResponse getShareSettings(UUID userId, UUID projectId) {
        Share share = shareRepository.findByProjectIdWithUser(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));

        if (!share.getProject().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Project not found");
        }

        return ShareResponse.from(share);
    }

    @Transactional
    public ShareResponse createShareLink(UUID userId, UUID projectId, com.stolink.backend.domain.share.dto.CreateShareRequest request) {
        // Try to find existing share with project and user in one query
        Share share = shareRepository.findByProjectIdWithUser(projectId).orElse(null);

        String passwordHash = null;
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            passwordHash = passwordEncoder.encode(request.getPassword());
        }

        java.time.LocalDateTime expiresAt = null;
        if (request.getExpiresIn() != null) {
            expiresAt = calculateExpiry(request.getExpiresIn());
        }

        if (share == null) {
            // Only fetch project if share doesn't exist
            Project project = projectRepository.findByIdWithUser(projectId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

            if (!project.getUser().getId().equals(userId)) {
                throw new ResourceNotFoundException("Project not found");
            }

            share = Share.builder()
                    .project(project)
                    .passwordHash(passwordHash)
                    .expiresAt(expiresAt)
                    .build();
            shareRepository.save(share);
        } else {
            // Already have everything we need for check
            if (!share.getProject().getUser().getId().equals(userId)) {
                throw new ResourceNotFoundException("Project not found");
            }
            // Update existing share settings
            share.updateSettings(passwordHash, expiresAt);
        }

        return ShareResponse.from(share);
    }

    private java.time.LocalDateTime calculateExpiry(String expiresIn) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if ("7d".equals(expiresIn)) {
            return now.plusDays(7);
        } else if ("30d".equals(expiresIn)) {
            return now.plusDays(30);
        }
        return null;
    }

    @Transactional
    public void deleteShareLink(UUID userId, UUID projectId) {
        Share share = shareRepository.findByProjectIdWithUser(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));

        if (!share.getProject().getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Project not found");
        }

        shareRepository.delete(share);
    }

    @Transactional
    public SharedProjectResponse getSharedProject(UUID shareId, String password) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found", "id", shareId));

        // 1. 만료 확인
        if (share.getExpiresAt() != null && share.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GONE, "Share link has expired");
        }

        // 2. 비밀번호 확인
        if (share.getPasswordHash() != null) {
            if (password == null || !passwordEncoder.matches(password, share.getPasswordHash())) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Invalid password");
            }
        }

        // 3. 조회수 증가
        share.incrementViewCount();

        Project project = share.getProject();
        List<Document> allDocuments = documentRepository.findByProjectWithParent(project);

        List<SharedDocumentResponse> documentTree = buildDocumentTree(allDocuments);

        return SharedProjectResponse.from(project, documentTree);
    }

    private static final int MAX_TREE_DEPTH = 10;

    private List<SharedDocumentResponse> buildDocumentTree(List<Document> documents) {
        // Group by parent ID
        Map<UUID, List<Document>> childrenMap = documents.stream()
                .filter(doc -> doc.getParent() != null)
                .collect(Collectors.groupingBy(doc -> doc.getParent().getId()));

        // Start with root documents
        List<Document> rootDocs = documents.stream()
                .filter(doc -> doc.getParent() == null)
                .sorted((d1, d2) -> Integer.compare(d1.getOrder(), d2.getOrder()))
                .collect(Collectors.toList());

        return rootDocs.stream()
                .map(doc -> convertToSharedResponse(doc, childrenMap, 0))
                .collect(Collectors.toList());
    }

    private SharedDocumentResponse convertToSharedResponse(Document doc, Map<UUID, List<Document>> childrenMap,
            int depth) {
        SharedDocumentResponse response = SharedDocumentResponse.from(doc);

        if (depth >= MAX_TREE_DEPTH) {
            response.setChildren(new ArrayList<>());
            return response;
        }

        List<Document> children = childrenMap.getOrDefault(doc.getId(), new ArrayList<>());
        children.sort((d1, d2) -> Integer.compare(d1.getOrder(), d2.getOrder()));

        List<SharedDocumentResponse> childResponses = children.stream()
                .map(child -> convertToSharedResponse(child, childrenMap, depth + 1))
                .collect(Collectors.toList());

        response.setChildren(childResponses);
        return response;
    }
}
