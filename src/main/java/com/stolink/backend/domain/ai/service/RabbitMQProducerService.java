package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
import com.stolink.backend.domain.ai.dto.ImageGenerationTaskDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RabbitMQProducerService {

    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.queues.analysis}")
    private String analysisQueue;

    @Value("${app.rabbitmq.queues.image}")
    private String imageQueue;

    public void sendAnalysisTask(AnalysisTaskDTO task) {
        try {
            rabbitTemplate.convertAndSend(analysisQueue, task);
            log.info("Analysis task sent to queue: jobId={}", task.getJobId());
        } catch (AmqpException e) {
            log.error("Failed to send analysis task: jobId={}", task.getJobId(), e);
            throw new RuntimeException("RabbitMQ message delivery failed", e);
        }
    }

    public void sendImageGenerationTask(ImageGenerationTaskDTO task) {
        try {
            rabbitTemplate.convertAndSend(imageQueue, task);
            log.info("Image generation task sent to queue: characterId={}, jobId={}", 
                     task.getCharacterId(), task.getJobId());
        } catch (AmqpException e) {
            log.error("Failed to send image generation task: characterId={}, jobId={}", 
                      task.getCharacterId(), task.getJobId(), e);
            throw new RuntimeException("RabbitMQ message delivery failed", e);
        }
    }
}
