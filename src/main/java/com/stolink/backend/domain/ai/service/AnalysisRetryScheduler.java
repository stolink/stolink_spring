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
