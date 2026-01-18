package com.stolink.backend.domain.character.event;

import java.util.UUID;

/**
 * 이미지 생성 요청 이벤트
 * 트랜잭션 커밋 후 RabbitMQ 메시지 발송을 위해 사용됨
 */
public record ImageGenerationRequestedEvent(
        String jobId,
        UUID userId,
        UUID projectId,
        UUID characterId,
        String description,
        String action,
        String originalImageUrl) {
}
