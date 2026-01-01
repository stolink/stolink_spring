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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.stolink.backend.domain.document.dto.ManuscriptUploadRequest;

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

        // N+1 문제 해결: 전체 문서를 한 번에 조회 (Parent Fetch Join)
        List<Document> allDocs = documentRepository.findByProjectWithParent(project);

        return buildTreeInMemory(allDocs);
    }

    private List<DocumentTreeResponse> buildTreeInMemory(List<Document> documents) {
        Map<UUID, DocumentTreeResponse> dtoMap = new HashMap<>();
        List<DocumentTreeResponse> roots = new ArrayList<>();

        // 1. 모든 문서를 DTO로 변환하여 맵에 저장
        for (Document doc : documents) {
            dtoMap.put(doc.getId(), DocumentTreeResponse.from(doc));
        }

        // 2. 부모-자식 관계 연결
        // 입력된 documents가 이미 order 순으로 정렬되어 있으므로, 순서대로 처리하면 자식 리스트도 정렬됨
        for (Document doc : documents) {
            DocumentTreeResponse dto = dtoMap.get(doc.getId());
            if (doc.getParent() == null) {
                roots.add(dto);
            } else {
                DocumentTreeResponse parentDto = dtoMap.get(doc.getParent().getId());
                if (parentDto != null) {
                    parentDto.getChildren().add(dto);
                }
            }
        }
        return roots;
    }

    /**
     * 특정 폴더의 직계 자식 문서를 페이징하여 반환 (무한 스크롤용)
     * TEXT 타입 문서만 반환하며, order 기준 오름차순 정렬
     */
    public Page<DocumentTreeResponse> getChildren(UUID userId, UUID folderId, Pageable pageable) {
        User user = getUserOrThrow(userId);
        Document folder = documentRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", folderId));

        // 권한 검증: 프로젝트 소유자 확인
        if (!folder.getProject().getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        // FOLDER 타입인지 확인
        if (folder.getType() != Document.DocumentType.FOLDER) {
            throw new IllegalArgumentException("폴더가 아닌 문서입니다.");
        }

        // TEXT 타입 자식만 페이징 조회
        Page<Document> children = documentRepository.findByParentAndTypeOrderByOrderAsc(
                folder, Document.DocumentType.TEXT, pageable);

        return children.map(DocumentTreeResponse::from);
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

    public Map<String, Object> getPagedContent(UUID userId, UUID documentId, int page, int size) {
        Document document = getDocument(userId, documentId);
        String content = document.getContent();
        if (content == null)
            content = "";

        // 최대 사이즈 제한 (예: 100KB)
        int safeSize = Math.min(Math.max(size, 100), 100000);
        int totalLength = content.length();
        int start = Math.min(page * safeSize, totalLength);
        int end = Math.min(start + safeSize, totalLength);

        String chunk = content.substring(start, end);
        boolean hasNext = end < totalLength;

        return Map.of(
                "content", chunk,
                "page", page,
                "size", safeSize,
                "totalLength", totalLength,
                "hasNext", hasNext);
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
                request.getNotes());

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
        getUserOrThrow(userId);

        for (int i = 0; i < request.getOrderedIds().size(); i++) {
            UUID documentId = request.getOrderedIds().get(i);
            Document document = getDocument(userId, documentId);

            // Verify the document belongs to the same parent
            UUID currentParentId = document.getParent() != null ? document.getParent().getId() : null;
            if ((request.getParentId() == null && currentParentId != null) ||
                    (request.getParentId() != null && !request.getParentId().equals(currentParentId))) {
                throw new IllegalArgumentException(
                        "Document " + documentId + " does not belong to parent " + request.getParentId());
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

    @Transactional
    public List<DocumentTreeResponse> parseManuscript(UUID userId, ManuscriptUploadRequest request) {
        User user = getUserOrThrow(userId);
        Project project = getProjectOrThrow(request.getProjectId(), user);

        String[] lines = request.getContent().split("\\r?\\n");

        Document rootParent = null;
        if (request.getParentId() != null) {
            rootParent = documentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Document", "id", request.getParentId()));
        }

        // Approved Regex Patterns (v5) - 더 엄격한 패턴
        // 챕터: "제n장", "Chapter n", "Part n" 등 명확한 챕터 표시만 매칭
        Pattern folderPattern = Pattern.compile(
                "^\\s*(?:(?:제|第)\\s*[0-9一二三四五六七八九十]+\\s*(?:부|권|편|장)|(?:Part|Chapter|Book|Volume)\\s+[0-9IVXLCDM]+)\\s*[.:\\-]?\\s*.*$",
                Pattern.CASE_INSENSITIVE);

        // 섹션: 장면 전환선(***,---,===)만 매칭, 숫자 기반 섹션은 제외
        Pattern textPattern = Pattern.compile(
                "^\\s*[*=-]{3,}\\s*$");

        Document currentChapter = null; // Folder
        StringBuilder contentBuilder = new StringBuilder();
        String currentSectionTitle = null;

        int rootOrder = getNextOrder(project, rootParent);
        int chapterOrderAdj = 0;
        int sectionOrderAdj = 0;
        List<Document> createdDocuments = new ArrayList<>();

        for (String line : lines) {
            String trimmedLine = line.trim();

            Matcher folderMatcher = folderPattern.matcher(line);
            Matcher textMatcher = textPattern.matcher(line);

            if (folderMatcher.find()) {
                // 이전 데이터 저장
                createdDocuments.addAll(saveSectionWithSplitting(project,
                        currentChapter != null ? currentChapter : rootParent, contentBuilder,
                        currentSectionTitle, sectionOrderAdj++));

                // 새로운 챕터(폴더) 생성
                currentChapter = Document.builder()
                        .project(project)
                        .parent(rootParent)
                        .type(Document.DocumentType.FOLDER)
                        .title(trimmedLine)
                        .order(rootOrder + chapterOrderAdj++)
                        .status(Document.DocumentStatus.DRAFT)
                        .build();
                currentChapter = documentRepository.save(currentChapter);
                currentSectionTitle = null;
                sectionOrderAdj = 0;
                log.info("Parsed Chapter Folder: {}", trimmedLine);
            } else if (textMatcher.find()) {
                // 이전 데이터 저장
                createdDocuments.addAll(saveSectionWithSplitting(project,
                        currentChapter != null ? currentChapter : rootParent, contentBuilder,
                        currentSectionTitle, sectionOrderAdj++));

                // 새로운 섹션(텍스트) 타이틀 설정
                currentSectionTitle = trimmedLine;
                log.info("Parsed Section Trigger: {}", trimmedLine);
            } else {
                // 일반 본문
                if (!trimmedLine.isEmpty() || contentBuilder.length() > 0) {
                    contentBuilder.append(line).append("\n");
                }
                // 5,000자 분할은 saveSectionWithSplitting 내부 while 루프에서 처리됨
            }
        }

        // 마지막 데이터 저장
        createdDocuments.addAll(
                saveSectionWithSplitting(project, currentChapter != null ? currentChapter : rootParent, contentBuilder,
                        currentSectionTitle, sectionOrderAdj++));
        log.info("Manuscript parsing completed for project: {}. Created {} documents.", request.getProjectId(),
                createdDocuments.size());

        return createdDocuments.stream()
                .map(DocumentTreeResponse::from)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 섹션을 저장하되, 5000자가 넘으면 문단/문장 단위로 쪼개어 여러 문서로 저장합니다.
     *
     * @return 생성된 문서 목록
     */
    private List<Document> saveSectionWithSplitting(Project project, Document parent, StringBuilder builder,
            String title,
            int order) {
        List<Document> created = new ArrayList<>();
        if (builder.length() == 0)
            return created;

        String rawContent = builder.toString();
        int partCount = 1;
        final int MAX_CHARS = 5000;

        log.info("Starting split for content length: {}", rawContent.length());

        while (!rawContent.isEmpty()) {
            String chunk;
            String remaining;

            if (rawContent.length() > MAX_CHARS) {
                int splitIndex = -1;

                // 1차: 줄바꿈으로 분할 시도
                splitIndex = rawContent.lastIndexOf("\n", MAX_CHARS);

                // 2차: 줄바꿈이 없으면 마침표로 분할 시도
                if (splitIndex <= 0) {
                    splitIndex = rawContent.lastIndexOf(". ", MAX_CHARS);
                    if (splitIndex > 0) {
                        splitIndex += 1; // 마침표 포함
                    }
                }

                // 3차: 그래도 없으면 강제로 MAX_CHARS에서 자르기
                if (splitIndex <= 0) {
                    splitIndex = MAX_CHARS;
                }

                chunk = rawContent.substring(0, splitIndex).trim();
                remaining = rawContent.substring(splitIndex).trim();

                log.info("Split at index {}: chunk={} chars, remaining={} chars",
                        splitIndex, chunk.length(), remaining.length());

                // 빈 청크 방지
                if (chunk.isEmpty()) {
                    rawContent = remaining;
                    continue;
                }
            } else {
                chunk = rawContent.trim();
                remaining = "";
            }

            if (!chunk.isEmpty()) {
                String finalTitle = (title != null && !title.isEmpty()) ? title : (parent != null ? "본문" : "프롤로그");
                if (partCount > 1 || !remaining.isEmpty()) {
                    finalTitle += " (" + partCount + ")";
                }

                Document doc = Document.builder()
                        .project(project)
                        .parent(parent)
                        .type(Document.DocumentType.TEXT)
                        .title(finalTitle)
                        .content(chunk)
                        .wordCount(chunk.length())
                        .order(order++)
                        .status(Document.DocumentStatus.DRAFT)
                        .includeInCompile(true)
                        .build();
                documentRepository.save(doc);
                created.add(doc);
                log.info("Saved section '{}' with {} chars", finalTitle, chunk.length());
                partCount++;
            }
            rawContent = remaining;
        }
        builder.setLength(0);
        return created;
    }
}
