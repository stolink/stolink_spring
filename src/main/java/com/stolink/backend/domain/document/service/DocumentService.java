package com.stolink.backend.domain.document.service;

import com.stolink.backend.domain.document.dto.CreateDocumentRequest;
import com.stolink.backend.domain.document.dto.DocumentTreeResponse;
import com.stolink.backend.domain.document.dto.UpdateDocumentRequest;
import com.stolink.backend.domain.document.dto.ReorderDocumentsRequest;
import com.stolink.backend.domain.document.dto.BulkUpdateRequest;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public List<DocumentTreeResponse> getDocumentTree(UUID userId, UUID projectId) {
        User user = getUserOrThrow(userId);
        Project project = getProjectOrThrow(projectId, user);

        List<Document> rootDocuments = documentRepository.findRootDocuments(project);
        return buildTree(rootDocuments);
    }

    @Transactional
    public DocumentTreeResponse createDocument(UUID userId, CreateDocumentRequest request) {
        User user = getUserOrThrow(userId);
        Project project = getProjectOrThrow(request.getProjectId(), user);

        Document parent = null;
        if (request.getParentId() != null) {
            parent = documentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Document", "id", request.getParentId()));
        }

        Document.DocumentType type;
        try {
            type = Document.DocumentType.valueOf(request.getType().toUpperCase());
        } catch (Exception e) {
            type = Document.DocumentType.TEXT;
        }

        Document document = Document.builder()
                .project(project)
                .parent(parent)
                .type(type)
                .title(request.getTitle())
                .content("")
                .synopsis(request.getSynopsis() != null ? request.getSynopsis() : "")
                .order(getNextOrder(project, parent))
                .status(Document.DocumentStatus.DRAFT)
                .wordCount(0)
                .includeInCompile(true)
                .targetWordCount(request.getTargetWordCount())
                .build();

        document = documentRepository.save(document);
        log.info("Document created: {} in project: {}", document.getId(), request.getProjectId());

        return DocumentTreeResponse.from(document);
    }

    public Document getDocument(UUID userId, UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        // Verify ownership
        User user = getUserOrThrow(userId);
        if (!document.getProject().getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        return document;
    }

    @Transactional
    public Document updateDocumentContent(UUID userId, UUID documentId, String content) {
        Document document = getDocument(userId, documentId);
        document.updateContent(content);
        log.info("Document content updated: {}", documentId);
        return document;
    }

    @Transactional
    public Document updateDocument(UUID userId, UUID documentId, UpdateDocumentRequest request) {
        Document document = getDocument(userId, documentId);

        // parentId 변경 처리 (문서 이동)
        if (request.getParentId() != null || isParentChangeRequested(request)) {
            moveDocument(document, request.getParentId());
        }

        if (request.getContent() != null) {
            document.updateContent(request.getContent());
        }

        if (request.getKeywords() != null) {
            String keywords = String.join(",", request.getKeywords());
            document.updateKeywords(keywords);
        }

        document.update(
            request.getTitle(),
            request.getSynopsis(),
            request.getOrder(),
            request.getStatus(),
            request.getTargetWordCount(),
            request.getIncludeInCompile(),
            request.getNotes()
        );

        document.updateLabel(request.getLabel(), request.getLabelColor());

        log.info("Document updated: {}", documentId);
        return document;
    }

    /**
     * 문서를 다른 폴더로 이동합니다.
     */
    private void moveDocument(Document document, UUID newParentId) {
        Document newParent = null;
        
        if (newParentId != null) {
            newParent = documentRepository.findById(newParentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document", "id", newParentId));
            
            // 순환 참조 방지: 자기 자신이나 자신의 하위 폴더로 이동 불가
            if (document.getId().equals(newParentId)) {
                throw new IllegalArgumentException("문서를 자기 자신으로 이동할 수 없습니다.");
            }
            
            if (isDescendant(document, newParent)) {
                throw new IllegalArgumentException("문서를 자신의 하위 폴더로 이동할 수 없습니다.");
            }
            
            // 폴더 타입만 자식을 가질 수 있음
            if (newParent.getType() != Document.DocumentType.FOLDER) {
                throw new IllegalArgumentException("일반 문서 아래로는 이동할 수 없습니다. 폴더만 자식을 가질 수 있습니다.");
            }
        }
        
        // 새 부모 아래에서의 순서 계산 (마지막 순서)
        int newOrder = getNextOrder(document.getProject(), newParent);
        document.updateParent(newParent, newOrder);
        
        log.info("Document {} moved to parent {}", document.getId(), newParentId);
    }

    /**
     * targetDocument가 ancestorDocument의 하위(자손)인지 확인합니다.
     */
    private boolean isDescendant(Document ancestorDocument, Document targetDocument) {
        Document current = targetDocument.getParent();
        while (current != null) {
            if (current.getId().equals(ancestorDocument.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * 요청에서 parentId 변경이 명시적으로 요청되었는지 확인 (null로 이동하는 경우 포함)
     * 실제 구현에서는 JSON에서 parentId 필드가 존재하는지 확인하는 별도 로직이 필요할 수 있음
     */
    private boolean isParentChangeRequested(UpdateDocumentRequest request) {
        // 현재는 parentId가 null이 아닐 때만 이동 처리
        // 루트로 이동하려면 별도 API나 플래그가 필요
        return false;
    }


    @Transactional
    public void deleteDocument(UUID userId, UUID documentId) {
        Document document = getDocument(userId, documentId);
        documentRepository.delete(document);
        log.info("Document deleted: {}", documentId);
    }

    @Transactional
    public void reorderDocuments(UUID userId, ReorderDocumentsRequest request) {
        User user = getUserOrThrow(userId);

        for (int i = 0; i < request.getOrderedIds().size(); i++) {
            UUID documentId = request.getOrderedIds().get(i);
            Document document = getDocument(userId, documentId);

            // Verify the document belongs to the same parent
            UUID currentParentId = document.getParent() != null ? document.getParent().getId() : null;
            if ((request.getParentId() == null && currentParentId != null) ||
                (request.getParentId() != null && !request.getParentId().equals(currentParentId))) {
                throw new IllegalArgumentException("Document " + documentId + " does not belong to parent " + request.getParentId());
            }

            document.update(null, null, i, null, null, null, null);
        }

        log.info("Reordered {} documents under parent {}", request.getOrderedIds().size(), request.getParentId());
    }

    @Transactional
    public void bulkUpdateDocuments(UUID userId, BulkUpdateRequest request) {
        for (BulkUpdateRequest.DocumentUpdate update : request.getUpdates()) {
            updateDocument(userId, update.getId(), update.getChanges());
        }
        log.info("Bulk updated {} documents", request.getUpdates().size());
    }

    private List<DocumentTreeResponse> buildTree(List<Document> documents) {
        List<DocumentTreeResponse> tree = new ArrayList<>();

        for (Document doc : documents) {
            DocumentTreeResponse node = DocumentTreeResponse.from(doc);

            // Recursively load children
            List<Document> children = documentRepository.findByParentOrderByOrder(doc);
            if (!children.isEmpty()) {
                node.setChildren(buildTree(children));
            }

            tree.add(node);
        }

        return tree;
    }

    private int getNextOrder(Project project, Document parent) {
        List<Document> siblings = parent != null
                ? documentRepository.findByParentOrderByOrder(parent)
                : documentRepository.findRootDocuments(project);

        return siblings.size();
    }

    private User getUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private Project getProjectOrThrow(UUID projectId, User user) {
        return projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }
}
