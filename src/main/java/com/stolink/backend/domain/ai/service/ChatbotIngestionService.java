package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.document.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotIngestionService {

    @Value("${app.ai.chatbot-base-url:http://stolink-ai-server:8000}")
    private String chatbotBaseUrl;

    /**
     * 문서를 챗봇 서버로 비동기 전송합니다.
     * 
     * @Async 어노테이션을 통해 별도 스레드에서 실행되므로, 메인 트랜잭션이나 응답 시간에 영향을 주지 않습니다.
     */
    @Async
    public void ingestDocument(Map<String, Object> payload) {
        try {
            String url = chatbotBaseUrl + "/api/v1/chatbot/ingest";

            // payload는 호출부(DocumentService)에서 안전하게 구성해서 전달받음 (LazyLoading 이슈 방지)

            WebClient.create()
                    .post()
                    .uri(url)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(5))
                    .subscribe(
                            response -> log.debug("Chatbot ingestion success for doc: {}", payload.get("documentId")),
                            error -> log.warn("Chatbot ingestion failed for doc: {}. Error: {}",
                                    payload.get("documentId"), error.getMessage()));

            log.info("Triggered async chatbot ingestion for document: {}", payload.get("documentId"));

        } catch (Exception e) {
            log.warn("Failed to trigger chatbot ingestion for document: {}", payload.get("documentId"), e);
        }
    }
}
