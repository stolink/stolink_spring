package com.stolink.backend.global.infrastructure.storead;

import com.stolink.backend.global.infrastructure.storead.dto.StoreadPublishRequest;
import com.stolink.backend.global.infrastructure.storead.dto.StoreadPublishResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class StoreadClient {

    private final WebClient webClient;
    private final String serviceKey;

    public StoreadClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.storead.base-url}") String baseUrl,
            @Value("${app.storead.service-key}") String serviceKey) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.serviceKey = serviceKey;
    }

    public StoreadPublishResponse publish(StoreadPublishRequest request) {
        return webClient.post()
                .uri("/api/internal/chapters/publish")
                .header("X-Service-Key", serviceKey)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(StoreadPublishResponse.class)
                .block(); // Synchronous for now
    }
}
