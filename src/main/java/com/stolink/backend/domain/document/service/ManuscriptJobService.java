package com.stolink.backend.domain.document.service;

import com.stolink.backend.domain.document.dto.DocumentTreeResponse;
import com.stolink.backend.domain.document.dto.ManuscriptJobResponse;
import com.stolink.backend.domain.document.dto.ManuscriptUploadRequest;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.entity.ManuscriptJob;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.document.repository.ManuscriptJobRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManuscriptJobService {

    private final ManuscriptJobRepository jobRepository;
    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    private static final int MAX_CHARS = 5000;

    /**
     * 원고 처리 작업을 생성하고 즉시 jobId를 반환합니다.
     */
    @Transactional
    public ManuscriptJobResponse createJob(UUID userId, UUID projectId, ManuscriptUploadRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        Project project = projectRepository.findByIdAndUser(projectId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        ManuscriptJob job = ManuscriptJob.builder()
                .project(project)
                .userId(userId)
                .status(ManuscriptJob.JobStatus.PENDING)
                .progress(0)
                .message("원고 업로드 대기 중...")
                .manuscriptContent(request.getContent())
                .parentId(request.getParentId())
                .build();

        job = jobRepository.save(job);
        log.info("Created manuscript job: {} for project: {}", job.getId(), projectId);

        return ManuscriptJobResponse.from(job);
    }

    /**
     * 비동기로 원고를 처리합니다.
     */
    @Async
    @Transactional
    public void processJobAsync(UUID jobId) {
        ManuscriptJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("ManuscriptJob", "id", jobId));

        try {
            job.setStatus(ManuscriptJob.JobStatus.PROCESSING);
            job.updateProgress(5, "원고 분석 시작...");
            jobRepository.save(job);

            List<Document> createdDocuments = parseManuscriptWithProgress(job);

            job.complete(createdDocuments.size());
            jobRepository.save(job);

            log.info("Manuscript job {} completed. Created {} documents.", jobId, createdDocuments.size());

        } catch (Exception e) {
            log.error("Manuscript job {} failed: {}", jobId, e.getMessage(), e);
            job.fail("처리 중 오류가 발생했습니다: " + e.getMessage());
            jobRepository.save(job);
        }
    }

    /**
     * 작업 상태를 조회합니다.
     */
    public ManuscriptJobResponse getJobStatus(UUID userId, UUID jobId) {
        ManuscriptJob job = jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("ManuscriptJob", "id", jobId));
        return ManuscriptJobResponse.from(job);
    }

    /**
     * 진행률을 업데이트하면서 원고를 파싱합니다.
     */
    private List<Document> parseManuscriptWithProgress(ManuscriptJob job) {
        String content = job.getManuscriptContent();
        String[] lines = content.split("\\r?\\n");
        Project project = job.getProject();

        Document rootParent = null;
        if (job.getParentId() != null) {
            rootParent = documentRepository.findById(job.getParentId()).orElse(null);
        }

        // 챕터 제목 패턴 (제N장, Chapter N 등)
        Pattern chapterPattern = Pattern.compile(
                "^\\s*(?:(?:제|第)\\s*[0-9一二三四五六七八九十]+\\s*(?:부|권|편|장)|(?:Part|Chapter|Book|Volume)\\s+[0-9IVXLCDM]+)\\s*[.:\\-]?\\s*.*$",
                Pattern.CASE_INSENSITIVE);
        // 섹션 구분선 패턴 (***, ---)
        Pattern sectionDividerPattern = Pattern.compile("^\\s*[*=-]{3,}\\s*$");

        StringBuilder contentBuilder = new StringBuilder();
        String pendingChapterTitle = null; // 다음 섹션에 적용할 챕터 제목
        int sectionNumber = 1; // 섹션 번호 (챕터 제목이 없을 때 사용)
        List<Document> createdDocuments = new ArrayList<>();

        int rootOrder = getNextOrder(project, rootParent);
        int documentOrder = 0;

        int totalLines = lines.length;
        int processedLines = 0;

        job.updateProgress(10, "챕터 구조 분석 중...");
        jobRepository.save(job);

        for (String line : lines) {
            String trimmedLine = line.trim();
            Matcher chapterMatcher = chapterPattern.matcher(line);
            Matcher dividerMatcher = sectionDividerPattern.matcher(line);

            // 챕터 제목 발견 (폴더를 만들지 않고 다음 섹션에 적용할 제목으로 저장)
            if (chapterMatcher.find()) {
                // 이전 섹션이 있으면 먼저 저장
                if (contentBuilder.length() > 0) {
                    String sectionTitle = pendingChapterTitle != null ? pendingChapterTitle : "섹션 " + sectionNumber++;
                    createdDocuments.addAll(saveSection(project, rootParent, contentBuilder, sectionTitle, rootOrder + documentOrder++));
                    pendingChapterTitle = null;
                }
                // 다음 섹션에 사용할 제목으로 저장
                pendingChapterTitle = trimmedLine;
            }
            // 섹션 구분선 발견
            else if (dividerMatcher.find()) {
                if (contentBuilder.length() > 0) {
                    String sectionTitle = pendingChapterTitle != null ? pendingChapterTitle : "섹션 " + sectionNumber++;
                    createdDocuments.addAll(saveSection(project, rootParent, contentBuilder, sectionTitle, rootOrder + documentOrder++));
                    pendingChapterTitle = null;
                }
            }
            // 일반 텍스트
            else {
                if (!trimmedLine.isEmpty() || contentBuilder.length() > 0) {
                    contentBuilder.append(line).append("\n");
                }
            }

            // 진행률 업데이트 (매 1000줄마다)
            processedLines++;
            if (processedLines % 1000 == 0) {
                int progress = 10 + (int) ((processedLines / (double) totalLines) * 80);
                job.updateProgress(progress, String.format("처리 중... (%d/%d 줄)", processedLines, totalLines));
                jobRepository.save(job);
            }
        }

        // 마지막 데이터 저장
        if (contentBuilder.length() > 0) {
            String sectionTitle = pendingChapterTitle != null ? pendingChapterTitle : "섹션 " + sectionNumber;
            createdDocuments.addAll(saveSection(project, rootParent, contentBuilder, sectionTitle, rootOrder + documentOrder));
        }

        job.updateProgress(95, "문서 저장 완료, 마무리 중...");
        jobRepository.save(job);

        return createdDocuments;
    }

    private int getNextOrder(Project project, Document parent) {
        return documentRepository.findMaxOrderByProjectAndParent(project, parent)
                .map(max -> max + 1)
                .orElse(0);
    }

    private List<Document> saveSection(Project project, Document parent,
            StringBuilder builder, String title, int order) {
        List<Document> created = new ArrayList<>();
        if (builder.length() == 0)
            return created;

        String content = builder.toString().trim();
        // 5000자 제한 로직 제거 (통으로 저장)
        if (!content.isEmpty()) {
            String finalTitle = (title != null && !title.isEmpty()) ? title : (parent != null ? "본문" : "프롤로그");

            Document doc = Document.builder()
                    .project(project)
                    .parent(parent)
                    .type(Document.DocumentType.TEXT)
                    .title(finalTitle)
                    .content(content) // 전체 내용 저장
                    .wordCount(content.length())
                    .targetWordCount(0)
                    .order(order++)
                    .status(Document.DocumentStatus.DRAFT)
                    .includeInCompile(true)
                    .build();
            documentRepository.save(doc);
            created.add(doc);
        }
        builder.setLength(0);
        return created;
    }
}
