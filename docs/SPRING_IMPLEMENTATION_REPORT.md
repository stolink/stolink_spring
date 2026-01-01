# Spring 팀 구현 완료 보고서 - AI Backend 팀에게

> **작성일**: 2026-01-01  
> **작성자**: Spring Backend 팀  
> **목적**: 대용량 문서 분석 아키텍처 구현 완료 보고

---

## 📋 구현 완료 항목

### ✅ Phase 1: 엔티티 및 DB 스키마 수정

#### Document 엔티티 수정

```java
package com.stolink.backend.domain.document.entity;

import com.stolink.backend.domain.project.entity.Project;
import com.stolink.backend.global.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Document parent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentType type;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content = "";

    @Column(columnDefinition = "TEXT")
    private String synopsis = "";

    @Column(name = "\"order\"", nullable = false)
    private Integer order = 0;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DocumentStatus status = DocumentStatus.DRAFT;

    // AI 분석 상태 (대용량 문서 분석 아키텍처)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AnalysisStatus analysisStatus = AnalysisStatus.NONE;

    @Column
    private Integer analysisRetryCount = 0;

    @Column(length = 50)
    private String label;

    @Column(length = 7)
    private String labelColor;

    @Column(nullable = false)
    private Integer wordCount = 0;

    private Integer targetWordCount;

    @Column(nullable = false)
    private Boolean includeInCompile = true;

    @Column(columnDefinition = "text")
    private String keywords; // Comma-separated tags

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Builder
    public Document(UUID id, Project project, Document parent, DocumentType type, String title, String content,
            String synopsis, Integer order, DocumentStatus status, String label, String labelColor, Integer wordCount,
            Integer targetWordCount, Boolean includeInCompile, String keywords, String notes) {
        this.id = id;
        this.project = project;
        this.parent = parent;
        this.type = type;
        this.title = title;
        this.content = content;
        this.synopsis = synopsis;
        this.order = order;
        this.status = status;
        this.label = label;
        this.labelColor = labelColor;
        this.wordCount = wordCount;
        this.targetWordCount = targetWordCount;
        this.includeInCompile = includeInCompile;
        this.keywords = keywords;
        this.notes = notes;
    }

    public void updateContent(String content) {
        this.content = content;
        this.wordCount = calculateWordCount(content);
    }

    public void update(String title, String synopsis, Integer order, DocumentStatus status,
            Integer targetWordCount, Boolean includeInCompile, String notes) {
        if (title != null)
            this.title = title;
        if (synopsis != null)
            this.synopsis = synopsis;
        if (order != null)
            this.order = order;
        if (status != null)
            this.status = status;
        if (targetWordCount != null)
            this.targetWordCount = targetWordCount;
        if (includeInCompile != null)
            this.includeInCompile = includeInCompile;
        if (notes != null)
            this.notes = notes;
    }

    public void updateLabel(String label, String labelColor) {
        if (label != null)
            this.label = label;
        if (labelColor != null)
            this.labelColor = labelColor;
    }

    public void updateKeywords(String keywords) {
        this.keywords = keywords;
    }

    /**
     * 문서의 부모를 변경합니다 (폴더 이동)
     * 
     * @param newParent 새로운 부모 문서 (null이면 루트로 이동)
     * @param newOrder  새 부모 아래에서의 순서
     */
    public void updateParent(Document newParent, int newOrder) {
        this.parent = newParent;
        this.order = newOrder;
    }

    private int calculateWordCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Simple word count - can be enhanced
        return text.replaceAll("<[^>]*>", "").trim().length();
    }

    public enum DocumentType {
        FOLDER, TEXT
    }

    public enum DocumentStatus {
        DRAFT, REVISED, FINAL
    }

    /**
     * AI 분석 상태 (대용량 문서 분석 아키텍처)
     */
    public enum AnalysisStatus {
        NONE, // 분석 요청 전
        PENDING, // 분석 대기
        QUEUED, // RabbitMQ 발행됨
        PROCESSING, // Python 처리 중
        COMPLETED, // 분석 완료
        FAILED // 분석 실패
    }

    // === 분석 상태 관리 메서드 ===

    public void updateAnalysisStatus(AnalysisStatus status) {
        this.analysisStatus = status;
    }

    public void incrementRetryCount() {
        this.analysisRetryCount++;
    }

    public void resetAnalysisForRetry() {
        this.analysisStatus = AnalysisStatus.QUEUED;
        this.analysisRetryCount++;
    }
}
```

#### Section 엔티티 신규 생성
- `src/main/java/com/stolink/backend/domain/document/entity/Section.java`
- `src/main/java/com/stolink/backend/domain/document/repository/SectionRepository.java`

```java
@Entity
@Table(name = "sections")
public class Section {
    UUID id;
    Document document;        // FK: Document(TEXT) 참조
    Integer sequenceOrder;
    String navTitle;
    String content;
    String embeddingJson;     // 1536차원 벡터 (JSON 형태)
    String relatedCharactersJson;
    String relatedEventsJson;
}
```

---

### ✅ Phase 2: RabbitMQ 메시지 스키마

#### 새로 생성된 DTO 클래스

**1. DocumentAnalysisMessage.java** (Spring → Python 분석 요청)
- `src/main/java/com/stolink/backend/domain/ai/dto/DocumentAnalysisMessage.java`

```java
public class DocumentAnalysisMessage {
    private String messageType = "DOCUMENT_ANALYSIS";
    private String documentId;
    private String projectId;
    private String parentFolderId;
    private String chapterTitle;
    private Integer documentOrder;
    private Integer totalDocumentsInChapter;
    private Integer analysisPass;  // 1
    private String callbackUrl;
    private AnalysisContext context;
    private String traceId;

    public static class AnalysisContext {
        private List<Map<String, Object>> existingCharacters;
        private List<Map<String, Object>> existingEvents;
        // ...
    }
}
```

**2. DocumentAnalysisCallbackDTO.java** (Python → Spring 분석 결과)
- `src/main/java/com/stolink/backend/domain/ai/dto/DocumentAnalysisCallbackDTO.java`

```java
public class DocumentAnalysisCallbackDTO {
    private String messageType;
    private String documentId;
    private String parentFolderId;
    private String status; // COMPLETED, FAILED
    private List<SectionDTO> sections;
    private List<Map<String, Object>> characters;
    private List<Map<String, Object>> events;
    // ...
    
    public static class SectionDTO {
        private Integer sequenceOrder;
        private String navTitle;
        private String content;
        private List<Double> embedding;
        // ...
    }
}
```

**3. GlobalMergeMessage.java** (2차 Pass 트리거)
- `src/main/java/com/stolink/backend/domain/ai/dto/GlobalMergeMessage.java`

```java
public class GlobalMergeMessage {
    private String messageType = "GLOBAL_MERGE";
    private String projectId;
    private String callbackUrl;
    private String traceId;
}
```

**4. GlobalMergeCallbackDTO.java** (2차 Pass 결과)
- `src/main/java/com/stolink/backend/domain/ai/dto/GlobalMergeCallbackDTO.java`

```java
public class GlobalMergeCallbackDTO {
    private String messageType;
    private String projectId;
    private String status;
    private List<CharacterMergeDTO> characterMerges;
    // ...

    public static class CharacterMergeDTO {
        private String primaryId;
        private List<String> mergedIds;
        private String canonicalName;
        private List<String> mergedAliases;
        private Double confidence;
    }
}
```

**5. AnalysisStatusUpdateDTO.java** (상태 업데이트 요청)
- `src/main/java/com/stolink/backend/domain/ai/dto/AnalysisStatusUpdateDTO.java`

```java
public class AnalysisStatusUpdateDTO {
    private AnalysisStatus status;
    private String traceId;
}
```

#### RabbitMQ 큐 추가

```java
package com.stolink.backend.global.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.queues.analysis}")
    private String analysisQueue;

    @Value("${app.rabbitmq.queues.image}")
    private String imageQueue;

    // 대용량 분석 아키텍처 큐
    @Value("${app.rabbitmq.queues.document-analysis:document_analysis_queue}")
    private String documentAnalysisQueue;

    @Value("${app.rabbitmq.queues.global-merge:global_merge_queue}")
    private String globalMergeQueue;

    // Image RabbitMQ 설정
    @Value("${app.rabbitmq.image.host}")
    private String imageHost;

    @Value("${app.rabbitmq.image.port}")
    private int imagePort;

    @Value("${app.rabbitmq.image.username}")
    private String imageUsername;

    @Value("${app.rabbitmq.image.password}")
    private String imagePassword;

    @Value("${app.rabbitmq.image.virtual-host}")
    private String imageVirtualHost;

    // Agent RabbitMQ 설정
    @Value("${app.rabbitmq.agent.host}")
    private String agentHost;

    @Value("${app.rabbitmq.agent.port}")
    private int agentPort;

    @Value("${app.rabbitmq.agent.username}")
    private String agentUsername;

    @Value("${app.rabbitmq.agent.password}")
    private String agentPassword;

    @Value("${app.rabbitmq.agent.virtual-host}")
    private String agentVirtualHost;

    @Bean
    public Queue analysisQueue() {
        return new Queue(analysisQueue, true);
    }

    @Bean
    public Queue imageQueue() {
        return new Queue(imageQueue, true);
    }

    /**
     * 문서 분석 큐 (대용량 분석 아키텍처)
     */
    @Bean
    public Queue documentAnalysisQueue() {
        return new Queue(documentAnalysisQueue, true);
    }

    /**
     * 글로벌 병합 큐 (2차 Pass)
     */
    @Bean
    public Queue globalMergeQueue() {
        return new Queue(globalMergeQueue, true);
    }

    /**
     * JSON 메시지 변환기 (snake_case 직렬화 지원)
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Image RabbitMQ ConnectionFactory
     */
    @Bean
    @Primary
    public ConnectionFactory imageConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(imageHost);
        factory.setPort(imagePort);
        factory.setUsername(imageUsername);
        factory.setPassword(imagePassword);
        factory.setVirtualHost(imageVirtualHost);
        return factory;
    }

    /**
     * Agent RabbitMQ ConnectionFactory
     */
    @Bean
    public ConnectionFactory agentConnectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(agentHost);
        factory.setPort(agentPort);
        factory.setUsername(agentUsername);
        factory.setPassword(agentPassword);
        factory.setVirtualHost(agentVirtualHost);
        return factory;
    }

    /**
     * Image RabbitTemplate (기본)
     */
    @Bean
    @Primary
    public RabbitTemplate imageRabbitTemplate(
            @Qualifier("imageConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    /**
     * Agent RabbitTemplate (Analysis용)
     */
    @Bean
    public RabbitTemplate agentRabbitTemplate(
            @Qualifier("agentConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
```

---

### ✅ Phase 3: Callback 서비스 확장

#### AICallbackService 신규 메서드

```java
    // ============================================================
    // 대용량 문서 분석 아키텍처 (Document Analysis Architecture)
    // ============================================================

    private final com.stolink.backend.domain.document.repository.DocumentRepository documentRepository;
    private final com.stolink.backend.domain.document.repository.SectionRepository sectionRepository;
    private final DocumentAnalysisPublisher documentAnalysisPublisher;

    /**
     * 문서 분석 결과 콜백 처리 (1차 Pass)
     * 
     * 각 Document(TEXT)의 분석 결과를 처리하고 Section을 저장합니다.
     * 모든 문서 분석 완료 시 2차 Pass(글로벌 병합)를 트리거합니다.
     */
    @Transactional
    public void handleDocumentAnalysisCallback(com.stolink.backend.domain.ai.dto.DocumentAnalysisCallbackDTO callback) {
        log.info("Processing document analysis callback for document: {}, status: {}",
                callback.getDocumentId(), callback.getStatus());

        UUID documentId = UUID.fromString(callback.getDocumentId());

        // 문서 조회
        com.stolink.backend.domain.document.entity.Document document = documentRepository.findById(documentId)
                .orElse(null);
        if (document == null) {
            log.error("Document not found: {}", callback.getDocumentId());
            return;
        }

        // 실패 처리
        if (callback.isFailed()) {
            log.error("Document analysis failed for {}: {}", callback.getDocumentId(), callback.getError());
            document.updateAnalysisStatus(com.stolink.backend.domain.document.entity.Document.AnalysisStatus.FAILED);
            documentRepository.save(document);
            return;
        }

        // 1. Section 저장
        saveSections(document, callback.getSections());

        // 2. 임시 캐릭터/이벤트/설정 저장 (기존 로직 재사용)
        Project project = document.getProject();
        if (callback.getCharacters() != null && !callback.getCharacters().isEmpty()) {
            java.util.Map<String, Object> tempResult = new java.util.HashMap<>();
            tempResult.put("characters", callback.getCharacters());
            saveCharacters(tempResult, project);
        }
        if (callback.getEvents() != null && !callback.getEvents().isEmpty()) {
            java.util.Map<String, Object> tempResult = new java.util.HashMap<>();
            tempResult.put("events", callback.getEvents());
            saveEvents(tempResult, project);
        }
        if (callback.getSettings() != null && !callback.getSettings().isEmpty()) {
            java.util.Map<String, Object> tempResult = new java.util.HashMap<>();
            tempResult.put("settings", callback.getSettings());
            saveSettings(tempResult, project);
        }

        // 3. 문서 상태 업데이트
        document.updateAnalysisStatus(com.stolink.backend.domain.document.entity.Document.AnalysisStatus.COMPLETED);
        documentRepository.save(document);

        // 4. 1차 Pass 완료 체크 및 2차 Pass 트리거
        checkAndTriggerGlobalMerge(project, callback.getTraceId());

        log.info("Document analysis callback processed for: {} (processing_time: {}ms)",
                callback.getDocumentId(), callback.getProcessingTimeMs());
    }

    /**
     * Section 저장
     */
    private void saveSections(com.stolink.backend.domain.document.entity.Document document,
            java.util.List<com.stolink.backend.domain.ai.dto.DocumentAnalysisCallbackDTO.SectionDTO> sections) {
        if (sections == null || sections.isEmpty()) {
            log.info("No sections to save for document: {}", document.getId());
            return;
        }

        // 기존 Section 삭제 (재분석 시)
        sectionRepository.deleteAllByDocument(document);

        for (com.stolink.backend.domain.ai.dto.DocumentAnalysisCallbackDTO.SectionDTO sectionDTO : sections) {
            com.stolink.backend.domain.document.entity.Section section = com.stolink.backend.domain.document.entity.Section.builder()
                    .document(document)
                    .sequenceOrder(sectionDTO.getSequenceOrder())
                    .navTitle(sectionDTO.getNavTitle())
                    .content(sectionDTO.getContent())
                    .embeddingJson(toJson(sectionDTO.getEmbedding()))
                    .relatedCharactersJson(toJson(sectionDTO.getRelatedCharacters()))
                    .relatedEventsJson(toJson(sectionDTO.getRelatedEvents()))
                    .build();

            sectionRepository.save(section);
        }

        log.info("Saved {} sections for document: {}", sections.size(), document.getId());
    }

    /**
     * 1차 Pass 완료 체크 및 2차 Pass 트리거
     */
    private void checkAndTriggerGlobalMerge(Project project, String traceId) {
        UUID projectId = project.getId();

        // TEXT 문서 총 수
        long totalTextDocuments = documentRepository.countTextDocumentsByProjectId(projectId);

        // COMPLETED 상태 문서 수
        long completedDocuments = documentRepository.countByProjectIdAndTypeTextAndAnalysisStatus(
                projectId,
                com.stolink.backend.domain.document.entity.Document.AnalysisStatus.COMPLETED);

        log.info("Project {} - 1차 Pass 진행률: {}/{}", projectId, completedDocuments, totalTextDocuments);

        if (completedDocuments == totalTextDocuments && totalTextDocuments > 0) {
            log.info("Project {} - 모든 문서 분석 완료! 2차 Pass(글로벌 병합) 트리거", projectId);
            documentAnalysisPublisher.publishGlobalMerge(projectId, traceId);
        }
    }

    /**
     * 글로벌 병합 결과 콜백 처리 (2차 Pass)
     * 
     * Entity Resolution(캐릭터 병합) 결과를 적용합니다.
     */
    @Transactional
    public void handleGlobalMergeCallback(com.stolink.backend.domain.ai.dto.GlobalMergeCallbackDTO callback) {
        log.info("Processing global merge callback for project: {}, status: {}",
                callback.getProjectId(), callback.getStatus());

        if (!callback.isSuccess()) {
            log.error("Global merge failed for project {}: {}", callback.getProjectId(), callback.getError());
            return;
        }

        String projectId = callback.getProjectId();

        // 캐릭터 병합 적용
        if (callback.getCharacterMerges() != null) {
            for (com.stolink.backend.domain.ai.dto.GlobalMergeCallbackDTO.CharacterMergeDTO merge : callback.getCharacterMerges()) {
                applyCharacterMerge(merge, projectId);
            }
        }

        // 일관성 보고서 로깅
        if (callback.getConsistencyReport() != null) {
            log.info("Global merge consistency report for project {}: {}", projectId, callback.getConsistencyReport());
        }

        log.info("Global merge callback processed for project: {} (processing_time: {}ms)",
                callback.getProjectId(), callback.getProcessingTimeMs());
    }

    /**
     * 캐릭터 병합 적용
     */
    private void applyCharacterMerge(com.stolink.backend.domain.ai.dto.GlobalMergeCallbackDTO.CharacterMergeDTO merge, String projectId) {
        String primaryId = merge.getPrimaryId();
        java.util.List<String> mergedIds = merge.getMergedIds();

        if (primaryId == null || mergedIds == null || mergedIds.isEmpty()) {
            return;
        }

        // Primary 캐릭터 조회
        Optional<Character> primaryCharOpt = characterRepository.findById(primaryId);
        if (primaryCharOpt.isEmpty()) {
            log.warn("Primary character not found for merge: {}", primaryId);
            return;
        }

        Character primaryChar = primaryCharOpt.get();

        // Aliases 통합
        java.util.Set<String> allAliases = new java.util.HashSet<>();
        String existingAliasesJson = primaryChar.getAliasesJson();
        if (existingAliasesJson != null) {
            try {
                java.util.List<String> existingAliases = objectMapper.readValue(existingAliasesJson,
                        objectMapper.getTypeFactory().constructCollectionType(java.util.List.class, String.class));
                allAliases.addAll(existingAliases);
            } catch (Exception e) {
                log.warn("Failed to parse existing aliases: {}", e.getMessage());
            }
        }
        if (merge.getMergedAliases() != null) {
            allAliases.addAll(merge.getMergedAliases());
        }

        primaryChar.setAliasesJson(toJson(new java.util.ArrayList<>(allAliases)));
        characterRepository.save(primaryChar);

        // 중복 캐릭터 삭제
        for (String oldId : mergedIds) {
            try {
                characterRepository.deleteById(oldId);
                log.info("Deleted merged character: {} (merged into {})", oldId, primaryId);
            } catch (Exception e) {
                log.warn("Failed to delete merged character {}: {}", oldId, e.getMessage());
            }
        }

        log.info("Applied character merge: {} <- {} (aliases: {})",
                primaryId, mergedIds, merge.getMergedAliases());
    }
```

---

### ✅ Phase 4: 상태 관리 API

#### 문서 분석 상태 API

```java
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
```

#### 재시도 Scheduler

```java
package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 분석 실패 문서 재시도 스케줄러
 * 
 * FAILED 상태인 문서를 주기적으로 확인하여 재발행합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysisRetryScheduler {

    private final DocumentRepository documentRepository;
    private final DocumentAnalysisPublisher documentAnalysisPublisher;

    @Value("${app.analysis.max-retry-count:3}")
    private int maxRetryCount;

    /**
     * 1분마다 실패한 문서 재시도
     */
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void retryFailedDocuments() {
        List<Document> failedDocuments = documentRepository.findFailedDocumentsForRetry(maxRetryCount);

        if (failedDocuments.isEmpty()) {
            return;
        }

        log.info("재시도 대상 문서 {}개 발견", failedDocuments.size());

        for (Document doc : failedDocuments) {
            try {
                doc.resetAnalysisForRetry();
                documentRepository.save(doc);

                documentAnalysisPublisher.publishAnalysisForDocument(doc);

                log.info("문서 {} 재시도 발행 완료 (시도 횟수: {})", doc.getId(), doc.getAnalysisRetryCount());
            } catch (Exception e) {
                log.error("문서 {} 재시도 발행 실패: {}", doc.getId(), e.getMessage());
            }
        }
    }
}
```

---

### ✅ Phase 5: Batch 발행 서비스

```java
package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.ai.dto.DocumentAnalysisMessage;
import com.stolink.backend.domain.ai.dto.GlobalMergeMessage;
import com.stolink.backend.domain.document.entity.Document;
import com.stolink.backend.domain.document.entity.Document.AnalysisStatus;
import com.stolink.backend.domain.document.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 문서 분석 메시지 발행 서비스
 * 
 * 대용량 문서 분석을 위한 RabbitMQ 메시지 발행을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAnalysisPublisher {

    private final DocumentRepository documentRepository;

    @Qualifier("agentRabbitTemplate")
    private final RabbitTemplate agentRabbitTemplate;

    @Value("${app.rabbitmq.queues.document-analysis:document_analysis_queue}")
    private String documentAnalysisQueue;

    @Value("${app.rabbitmq.queues.global-merge:global_merge_queue}")
    private String globalMergeQueue;

    @Value("${app.callback.base-url:http://localhost:8080}")
    private String callbackBaseUrl;

    /**
     * 프로젝트 내 모든 TEXT 문서에 대해 분석 요청 발행
     * 
     * @param projectId 프로젝트 ID
     * @return 발행된 메시지 수
     */
    @Transactional
    public int publishAnalysisForProject(UUID projectId) {
        List<Document> textDocuments = documentRepository.findTextDocumentsByProjectId(projectId);

        if (textDocuments.isEmpty()) {
            log.warn("프로젝트 {}에 분석할 TEXT 문서가 없습니다.", projectId);
            return 0;
        }

        int totalDocuments = textDocuments.size();
        log.info("프로젝트 {} - {}개 문서 분석 요청 시작", projectId, totalDocuments);

        long startTime = System.currentTimeMillis();

        for (Document doc : textDocuments) {
            // 상태를 PENDING으로 업데이트
            doc.updateAnalysisStatus(AnalysisStatus.PENDING);
            documentRepository.save(doc);

            // 메시지 생성 및 발행
            DocumentAnalysisMessage message = buildMessage(doc, projectId, totalDocuments);
            agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message);

            // 상태를 QUEUED로 업데이트
            doc.updateAnalysisStatus(AnalysisStatus.QUEUED);
            documentRepository.save(doc);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("프로젝트 {} - {}개 메시지 발행 완료 ({}ms)", projectId, totalDocuments, duration);

        return totalDocuments;
    }

    /**
     * 단일 문서 분석 요청 발행
     */
    @Transactional
    public void publishAnalysisForDocument(Document document) {
        document.updateAnalysisStatus(AnalysisStatus.PENDING);
        documentRepository.save(document);

        DocumentAnalysisMessage message = buildMessage(
                document,
                document.getProject().getId(),
                1);

        agentRabbitTemplate.convertAndSend(documentAnalysisQueue, message);

        document.updateAnalysisStatus(AnalysisStatus.QUEUED);
        documentRepository.save(document);

        log.info("문서 {} 분석 요청 발행 완료", document.getId());
    }

    /**
     * 글로벌 병합 (2차 Pass) 요청 발행
     */
    public void publishGlobalMerge(UUID projectId, String traceId) {
        GlobalMergeMessage message = GlobalMergeMessage.builder()
                .projectId(projectId.toString())
                .callbackUrl(callbackBaseUrl + "/api/ai-callback")
                .traceId(traceId)
                .build();

        agentRabbitTemplate.convertAndSend(globalMergeQueue, message);
        log.info("프로젝트 {} 글로벌 병합 요청 발행 완료", projectId);
    }

    /**
     * 분석 메시지 생성
     */
    private DocumentAnalysisMessage buildMessage(Document document, UUID projectId, int totalDocuments) {
        Document parent = document.getParent();
        String parentFolderId = parent != null ? parent.getId().toString() : null;
        String chapterTitle = parent != null ? parent.getTitle() : document.getTitle();

        return DocumentAnalysisMessage.builder()
                .documentId(document.getId().toString())
                .projectId(projectId.toString())
                .parentFolderId(parentFolderId)
                .chapterTitle(chapterTitle)
                .documentOrder(document.getOrder())
                .totalDocumentsInChapter(totalDocuments)
                .analysisPass(1)
                .callbackUrl(callbackBaseUrl + "/api/ai-callback")
                .context(DocumentAnalysisMessage.AnalysisContext.builder()
                        .existingCharacters(List.of()) // 1차 Pass는 빈 배열
                        .existingEvents(List.of())
                        .existingRelationships(List.of())
                        .existingSettings(List.of())
                        .build())
                .traceId(UUID.randomUUID().toString())
                .build();
    }
}
```

---

## 📊 빌드 상태

```
BUILD SUCCESSFUL in 17s
5 actionable tasks: 5 executed
```

---

## 🔄 Python 팀 연동 가이드

### 1. 문서 분석 메시지 수신

Python Consumer가 `document_analysis_queue`에서 수신:

```json
{
  "message_type": "DOCUMENT_ANALYSIS",
  "document_id": "uuid-of-text-document",
  "project_id": "project-uuid",
  "parent_folder_id": "chapter-folder-uuid",
  "chapter_title": "제1장",
  "document_order": 1,
  "analysis_pass": 1,
  "callback_url": "http://spring-backend:8080/api/ai-callback",
  "context": {
    "existing_characters": [],
    "existing_events": []
  },
  "trace_id": "req-..."
}
```

### 2. content 조회

```python
# documents 테이블에서 직접 조회
content = await db.query(
    "SELECT content FROM documents WHERE id = %s AND type = 'TEXT'",
    msg.document_id
)
```

### 3. PROCESSING 상태 업데이트

분석 시작 시:
```http
PATCH http://spring-backend:8080/api/documents/{document_id}/analysis-status
{
  "status": "PROCESSING",
  "traceId": "req-..."
}
```

### 4. 분석 완료 Callback

```http
POST http://spring-backend:8080/api/ai-callback
Content-Type: application/json

{
  "message_type": "DOCUMENT_ANALYSIS",
  "document_id": "uuid",
  "parent_folder_id": "folder-uuid",
  "status": "COMPLETED",
  "sections": [
    {
      "sequence_order": 1,
      "nav_title": "이안의 각성",
      "content": "눈을 떴을 때...",
      "embedding": [0.123, -0.456, ...],
      "related_characters": ["이안", "나비"],
      "related_events": ["E001"]
    }
  ],
  "characters": [...],
  "events": [...],
  "settings": [...],
  "trace_id": "req-...",
  "processing_time_ms": 5000
}
```

### 5. 2차 Pass (글로벌 병합)

모든 문서 분석 완료 시 Spring이 `global_merge_queue`에 발행:

```json
{
  "message_type": "GLOBAL_MERGE",
  "project_id": "project-uuid",
  "callback_url": "http://spring-backend:8080/api/ai-callback",
  "trace_id": "req-..."
}
```

### 6. 글로벌 병합 결과 Callback

```json
{
  "message_type": "GLOBAL_MERGE_RESULT",
  "project_id": "project-uuid",
  "status": "COMPLETED",
  "character_merges": [
    {
      "primary_id": "char-이안-001",
      "merged_ids": ["char-ian-002"],
      "canonical_name": "이안",
      "merged_aliases": ["Ian"],
      "confidence": 0.95
    }
  ],
  "consistency_report": {...}
}
```

---

## ⏳ 미완료 항목 (Phase 2에서 진행 예정)

| 항목 | 상태 | 비고 |
|------|------|------|
| TransactionalEventListener 패턴 | ⏳ | DB 트랜잭션 후 RabbitMQ 발행 분리 |
| 0.5초 이내 발행 성능 테스트 | ⏳ | 365개 메시지 Batch 발행 테스트 필요 |
| AICallbackController 분기 처리 | ⏳ | message_type 기반 라우팅 |
| 단위/통합 테스트 | ⏳ | 테스트 코드 작성 필요 |

---

## 📚 생성/수정된 파일 목록

### 신규 생성 (9개 파일)
- `src/main/java/com/stolink/backend/domain/document/entity/Section.java`
- `src/main/java/com/stolink/backend/domain/document/repository/SectionRepository.java`
- `src/main/java/com/stolink/backend/domain/ai/dto/DocumentAnalysisMessage.java`
- `src/main/java/com/stolink/backend/domain/ai/dto/DocumentAnalysisCallbackDTO.java`
- `src/main/java/com/stolink/backend/domain/ai/dto/GlobalMergeMessage.java`
- `src/main/java/com/stolink/backend/domain/ai/dto/GlobalMergeCallbackDTO.java`
- `src/main/java/com/stolink/backend/domain/ai/dto/AnalysisStatusUpdateDTO.java`
- `src/main/java/com/stolink/backend/domain/ai/service/DocumentAnalysisPublisher.java`
- `src/main/java/com/stolink/backend/domain/ai/service/AnalysisRetryScheduler.java`
- `src/main/java/com/stolink/backend/domain/document/controller/DocumentAnalysisController.java`

### 수정됨 (3개 파일)
- `src/main/java/com/stolink/backend/domain/document/entity/Document.java`
- `src/main/java/com/stolink/backend/domain/document/repository/DocumentRepository.java`
- `src/main/java/com/stolink/backend/global/config/RabbitMQConfig.java`
- `src/main/java/com/stolink/backend/domain/ai/service/AICallbackService.java`

---

> 추가 질문이나 수정 요청이 있으면 말씀해 주세요! 🚀
