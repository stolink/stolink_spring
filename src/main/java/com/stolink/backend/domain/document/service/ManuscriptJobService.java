package com.stolink.backend.domain.document.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stolink.backend.domain.ai.service.AIAnalysisService;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ManuscriptJobService {

    private final ManuscriptJobRepository jobRepository;
    private final DocumentRepository documentRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AIAnalysisService aiAnalysisService;

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
    public void processJobAsync(UUID jobId) {
        ManuscriptJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("ManuscriptJob", "id", jobId));

        try {
            job.setStatus(ManuscriptJob.JobStatus.PROCESSING);
            job.updateProgress(5, "원고 분석 시작...");
            jobRepository.save(job);

            List<Document> createdDocuments = parseManuscriptWithProgress(job);

            // Batch Insert로 성능 최적화
            if (!createdDocuments.isEmpty()) {
                documentRepository.saveAll(createdDocuments);
            }

            job.complete(createdDocuments.size());
            jobRepository.save(job);

            log.info("Manuscript job {} completed. Created {} documents.", jobId, createdDocuments.size());

            // AI 분석 자동 트리거
            if (!createdDocuments.isEmpty()) {
                UUID projectId = job.getProject().getId();
                log.info("Triggering AI analysis for project: {}", projectId);
                aiAnalysisService.triggerProjectAnalysis(projectId);
            }

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
        Project project = job.getProject();

        Document rootParent = null;
        if (job.getParentId() != null) {
            rootParent = documentRepository.findById(job.getParentId()).orElse(null);
        }

        job.updateProgress(10, "챕터 구조 분석 중...");
        jobRepository.save(job);

        // 정규식 없는 상태 기반 파서 사용 (Defensive Parsing)
        List<ImprovedManuscriptParser.ParsedSection> sections = ImprovedManuscriptParser.parse(content);

        List<Document> createdDocuments = new ArrayList<>();
        int rootOrder = getNextOrder(project, rootParent);
        int documentOrder = 0;
        int totalSections = sections.size();

        for (int i = 0; i < totalSections; i++) {
            ImprovedManuscriptParser.ParsedSection section = sections.get(i);

            // 섹션 생성 (저장하지 않고 객체만 반환)
            createdDocuments
                    .addAll(createSectionDocuments(project, rootParent, section.getContent(), section.getTitle(),
                            rootOrder + documentOrder++));

            // 진행률 업데이트
            if (i % 5 == 0 || i == totalSections - 1) { // 너무 잦은 업데이트 방지
                int progress = 10 + (int) (((double) (i + 1) / totalSections) * 80);
                job.updateProgress(progress, String.format("문서 생성 중... (%d/%d 챕터)", i + 1, totalSections));
                jobRepository.save(job);
            }
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

    private List<Document> createSectionDocuments(Project project, Document parent,
            String contentRaw, String title, int order) {
        List<Document> created = new ArrayList<>();
        String content = contentRaw.trim();

        if (content.isEmpty())
            return created;

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
        // save 호출 제거 (Batch Insert를 위해 객체만 리턴)
        created.add(doc);

        return created;
    }
}
