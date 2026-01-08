package com.stolink.backend.domain.ai.service;

import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.stolink.backend.domain.ai.dto.AnalysisCallbackDTO;
import com.stolink.backend.domain.ai.dto.event.AnalysisEvent;
import com.stolink.backend.domain.ai.entity.ProcessedEvent;
import com.stolink.backend.domain.ai.repository.ProcessedEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RabbitMQ Analysis Event Consumer
 * 
 * AI Backend가 발행하는 분석 완료/실패 이벤트를 소비하여
 * RDB에 저장합니다.
 * 
 * 기존 HTTP Callback과 병행 운영 가능 (마이그레이션 Phase 1)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AnalysisEventConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final AICallbackService aiCallbackService;

    /**
     * 분석 이벤트 처리
     * 
     * @param event AI Backend에서 발행한 분석 이벤트
     */
    @RabbitListener(queues = "${app.rabbitmq.queues.analysis-completed:analysis.completed}", containerFactory = "agentRabbitListenerContainerFactory")
    public void handleAnalysisEvent(AnalysisEvent event) {
        // MDC에 traceId 설정 (로깅용)
        if (event.getTraceId() != null) {
            MDC.put("traceId", event.getTraceId());
        }

        try {
            log.info("Received analysis event: eventId={}, eventType={}, jobId={}",
                    event.getEventId(), event.getEventType(), event.getJobId());

            // 1. Idempotency 체크 (중복 처리 방지)
            if (processedEventRepository.existsByEventId(event.getEventId())) {
                log.info("Event already processed, skipping: {}", event.getEventId());
                return;
            }

            // 2. 이벤트를 AnalysisCallbackDTO로 변환
            AnalysisCallbackDTO callback = convertToCallback(event);

            // 3. 기존 AICallbackService 로직 재사용
            aiCallbackService.handleAnalysisCallback(callback);

            // 4. 처리 완료 기록
            processedEventRepository.save(new ProcessedEvent(event.getEventId()));

            log.info("Event processed successfully: eventId={}", event.getEventId());

        } catch (Exception e) {
            log.error("Failed to process analysis event: eventId={}", event.getEventId(), e);
            // 예외를 던져서 DLQ로 이동하도록 함
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException(
                    "Failed to process event: " + event.getEventId(), e);
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * AnalysisEvent를 AnalysisCallbackDTO로 변환
     * 
     * 기존 AICallbackService.handleAnalysisCallback()은 AnalysisCallbackDTO를 받으므로
     * 이벤트 구조를 변환합니다.
     */
    private AnalysisCallbackDTO convertToCallback(AnalysisEvent event) {
        AnalysisCallbackDTO callback = new AnalysisCallbackDTO();

        // Job 식별자
        callback.setJobId(event.getJobId());

        // 상태 매핑
        if (event.isSuccess()) {
            callback.setStatus("COMPLETED");
        } else if (event.isFailed()) {
            callback.setStatus("FAILED");
            callback.setError(event.getErrorMessage());
        }

        // 분석 결과 (flat structure로 설정)
        callback.setSections(event.getSections());
        callback.setCharacters(event.getCharacters());
        callback.setEvents(event.getEvents());
        callback.setSettings(event.getSettings());
        callback.setRelationships(event.getRelationships());
        callback.setPlotIntegration(event.getPlotIntegration());
        callback.setConsistencyReport(event.getConsistencyReport());
        callback.setValidation(event.getValidation());
        callback.setEmotions(event.getEmotions());

        // 메타데이터
        callback.setProcessingTimeMs(event.getProcessingTimeMs());
        callback.setTraceId(event.getTraceId());

        return callback;
    }
}
