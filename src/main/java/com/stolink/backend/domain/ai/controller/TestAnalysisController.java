package com.stolink.backend.domain.ai.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stolink.backend.domain.ai.dto.DocumentAnalysisMessage;
import com.stolink.backend.domain.ai.service.DocumentAnalysisPublisher;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.domain.project.repository.ProjectRepository;
import com.stolink.backend.domain.user.entity.AuthProvider;
import com.stolink.backend.domain.user.entity.User;
import com.stolink.backend.domain.user.repository.UserRepository;
import com.stolink.backend.global.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 연동 테스트용 컨트롤러
 * 
 * 개발/로컬 환경에서만 활성화됩니다.
 * 운영 환경에서는 비활성화됩니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/test/analysis")
@RequiredArgsConstructor
@Profile({ "dev", "local", "test" })
public class TestAnalysisController {

        private final DocumentAnalysisPublisher documentAnalysisPublisher;
        private final DocumentRepository documentRepository;
        private final ProjectRepository projectRepository;
        private final UserRepository userRepository;

        @Qualifier("agentRabbitTemplate")
        private final RabbitTemplate agentRabbitTemplate;

        @Value("${app.rabbitmq.queues.document-analysis:document_analysis_queue}")
        private String documentAnalysisQueue;

        @Value("${app.ai.callback-base-url:http://stolink-backend:8080/api/ai-callback}")
        private String callbackBaseUrl;

        /**
         * 프로젝트 분석 시작 (Batch 발행)
         * 
         * @param projectId 프로젝트 ID
         * @return 발행된 메시지 수
         */
        @PostMapping("/project/{projectId}/start")
        public ApiResponse<Map<String, Object>> startProjectAnalysis(@PathVariable UUID projectId) {
                log.info("Starting analysis for project: {}", projectId);

                int publishedCount = documentAnalysisPublisher.publishAnalysisForProject(projectId);

                return ApiResponse.ok(Map.of(
                                "projectId", projectId,
                                "publishedMessages", publishedCount,
                                "status", "STARTED",
                                "message", publishedCount + "개 문서 분석 시작"));
        }

        /**
         * 단일 문서 분석 테스트
         * 
         * @param documentId 문서 ID
         * @return 발행 결과
         */
        @PostMapping("/document/{documentId}/analyze")
        public ApiResponse<Map<String, Object>> analyzeDocument(@PathVariable UUID documentId) {
                Document document = documentRepository.findById(documentId)
                                .orElseThrow(() -> new IllegalArgumentException("문서를 찾을 수 없습니다: " + documentId));

                if (document.getType() != Document.DocumentType.TEXT) {
                        return ApiResponse.<Map<String, Object>>builder()
                                        .status(HttpStatus.BAD_REQUEST)
                                        .message("TEXT 타입 문서만 분석할 수 있습니다.")
                                        .build();
                }

                documentAnalysisPublisher.publishAnalysisForDocument(document);

                return ApiResponse.ok(Map.of(
                                "documentId", documentId,
                                "title", document.getTitle(),
                                "status", "QUEUED",
                                "message", "분석 요청이 발행되었습니다."));
        }

        /**
         * 수동 메시지 발행 테스트
         * 
         * 직접 메시지를 구성하여 RabbitMQ로 발행합니다.
         */
        @PostMapping("/manual")
        public ApiResponse<Map<String, Object>> sendManualMessage(@RequestBody Map<String, Object> request) {
                String documentId = (String) request.getOrDefault("documentId", "test-doc-001");
                String projectId = (String) request.getOrDefault("projectId", "test-project-001");
                String parentFolderId = (String) request.getOrDefault("parentFolderId", "test-folder-001");
                String chapterTitle = (String) request.getOrDefault("chapterTitle", "제1장");

                DocumentAnalysisMessage message = DocumentAnalysisMessage.builder()
                                .documentId(documentId)
                                .projectId(projectId)
                                .parentFolderId(parentFolderId)
                                .chapterTitle(chapterTitle)
                                .documentOrder(1)
                                .totalDocumentsInChapter(1)
                                .analysisPass(1)
                                .callbackUrl(callbackBaseUrl)
                                .context(DocumentAnalysisMessage.AnalysisContext.builder()
                                                .existingCharacters(List.of())
                                                .existingEvents(List.of())
                                                .existingRelationships(List.of())
                                                .existingSettings(List.of())
                                                .build())
                                .traceId("test-" + UUID.randomUUID().toString().substring(0, 8))
                                .build();

                agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message);

                log.info("Manual test message sent: documentId={}, projectId={}", documentId, projectId);

                return ApiResponse.ok(Map.of(
                                "documentId", documentId,
                                "projectId", projectId,
                                "queue", documentAnalysisQueue,
                                "callbackUrl", message.getCallbackUrl(),
                                "traceId", message.getTraceId(),
                                "status", "SENT"));
        }

        /**
         * RabbitMQ 연결 상태 확인
         */
        @GetMapping("/health")
        public ApiResponse<Map<String, Object>> checkHealth() {
                boolean connected = false;
                String errorMessage = null;

                try {
                        // 간단한 연결 확인
                        agentRabbitTemplate.execute(channel -> {
                                channel.queueDeclarePassive(documentAnalysisQueue);
                                return null;
                        });
                        connected = true;
                } catch (Exception e) {
                        errorMessage = e.getMessage();
                        log.error("RabbitMQ connection failed: {}", e.getMessage());
                }

                return ApiResponse.ok(Map.of(
                                "rabbitmq", connected ? "connected" : "disconnected",
                                "queue", documentAnalysisQueue,
                                "callbackUrl", callbackBaseUrl,
                                "error", errorMessage != null ? errorMessage : "none"));
        }

        /**
         * E2E 테스트용 Setup 엔드포인트
         * 
         * 테스트 유저, 프로젝트, 문서를 한 번에 생성하고 분석을 시작합니다.
         */
        @PostMapping("/e2e/setup")
        public ApiResponse<Map<String, Object>> setupE2ETest(@RequestBody Map<String, Object> request) {
                String content = (String) request.getOrDefault("content", "테스트 원고입니다. 주인공 김철수는 서울에 살고 있습니다.");
                String title = (String) request.getOrDefault("title", "E2E 테스트 문서");
                String projectTitle = (String) request.getOrDefault("projectTitle", "E2E 테스트 프로젝트");

                log.info("Starting E2E test setup: projectTitle={}, docTitle={}", projectTitle, title);

                // 1. 테스트 유저 생성 또는 조회
                User testUser = userRepository.findByEmail("e2e-test@stolink.test")
                                .orElseGet(() -> {
                                        User newUser = User.builder()
                                                        .email("e2e-test@stolink.test")
                                                        .password("e2e-test-password")
                                                        .nickname("E2E Test User")
                                                        .provider(AuthProvider.LOCAL)
                                                        .providerId("e2e-test-001")
                                                        .build();
                                        return userRepository.save(newUser);
                                });

                // 2. 프로젝트 생성
                Project project = Project.builder()
                                .title(projectTitle)
                                .user(testUser)
                                .genre(Project.Genre.OTHER)
                                .status(Project.ProjectStatus.WRITING)
                                .build();
                project = projectRepository.save(project);

                // 3. 문서 생성
                Document document = Document.builder()
                                .project(project)
                                .title(title)
                                .content(content)
                                .type(Document.DocumentType.TEXT)
                                .order(0)
                                .wordCount(content.length())
                                .includeInCompile(true)
                                .build();
                document = documentRepository.save(document);

                // 4. 분석 시작
                documentAnalysisPublisher.publishAnalysisForDocument(document);

                log.info("E2E test setup completed: userId={}, projectId={}, documentId={}",
                                testUser.getId(), project.getId(), document.getId());

                return ApiResponse.ok(Map.of(
                                "userId", testUser.getId(),
                                "projectId", project.getId(),
                                "documentId", document.getId(),
                                "documentTitle", title,
                                "contentLength", content.length(),
                                "status", "ANALYSIS_STARTED",
                                "message", "E2E 테스트 설정 완료. 분석이 시작되었습니다."));
        }

        /**
         * E2E 테스트 정리 - 테스트 데이터 삭제
         */
        @DeleteMapping("/e2e/cleanup")
        public ApiResponse<Map<String, Object>> cleanupE2ETest() {
                log.info("Starting E2E test cleanup");

                return userRepository.findByEmail("e2e-test@stolink.test")
                                .map(testUser -> {
                                        // 테스트 유저의 프로젝트들 삭제
                                        List<Project> projects = projectRepository.findByUser(testUser);
                                        for (Project project : projects) {
                                                documentRepository.deleteAllByProject(project);
                                        }
                                        projectRepository.deleteAll(projects);
                                        userRepository.delete(testUser);

                                        log.info("E2E test cleanup completed: deleted {} projects", projects.size());
                                        return ApiResponse.<Map<String, Object>>ok(Map.of(
                                                        "deleted", true,
                                                        "projectsDeleted", projects.size(),
                                                        "message", "E2E 테스트 데이터 정리 완료"));
                                })
                                .orElse(ApiResponse.ok(Map.of(
                                                "deleted", false,
                                                "message", "삭제할 테스트 데이터가 없습니다.")));
        }
}
