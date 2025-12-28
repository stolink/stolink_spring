package com.stolink.backend.domain.share.service;

import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.share.dto.CreateShareRequest;
import com.stolink.backend.domain.share.dto.SharedDocumentResponse;
import com.stolink.backend.domain.share.dto.SharedProjectResponse;
import com.stolink.backend.domain.share.dto.ShareResponse;
import com.stolink.backend.domain.share.entity.Share;
import com.stolink.backend.domain.share.repository.ShareRepository;
import com.stolink.backend.global.common.exception.AccessDeniedException;
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

    public ShareResponse getShareSettings(UUID userId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!project.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Project not found");
        }

        Share share = shareRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));

        return ShareResponse.builder()
                .shareId(share.getId())
                .projectId(projectId)
                .hasPassword(share.getPassword() != null && !share.getPassword().isEmpty())
                .build();
    }

    @Transactional
    public ShareResponse createShareLink(UUID userId, UUID projectId, CreateShareRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!project.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Project not found");
        }

        Share share = shareRepository.findByProjectId(projectId).orElse(null);
        if (share == null) {
            share = Share.builder()
                    .project(project)
                    .password(request.getPassword())
                    .build();
        } else {
            share.updatePassword(request.getPassword());
        }

        shareRepository.save(share);
        return ShareResponse.builder()
                .shareId(share.getId())
                .projectId(projectId)
                .hasPassword(share.getPassword() != null && !share.getPassword().isEmpty())
                .build();
    }

    @Transactional
    public void deleteShareLink(UUID userId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));

        if (!project.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Project not found");
        }

        Share share = shareRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));

        shareRepository.delete(share);
    }

    public SharedProjectResponse getSharedProject(UUID shareId, String password) {
        Share share = shareRepository.findById(shareId)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found"));

        // Password check
        if (share.getPassword() != null && !share.getPassword().isEmpty()) {
            if (password == null || !password.equals(share.getPassword())) {
                throw new AccessDeniedException("Invalid password");
            }
        }

        Project project = share.getProject();
        List<Document> allDocuments = documentRepository.findByProject(project);

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
