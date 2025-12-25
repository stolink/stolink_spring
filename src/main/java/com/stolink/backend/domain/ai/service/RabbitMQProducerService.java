package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        rabbitTemplate.convertAndSend(analysisQueue, task);
        log.info("Analysis task sent to queue: {}", task.getJobId());
    }

    public void sendImageGenerationTask(Object task) {
        rabbitTemplate.convertAndSend(imageQueue, task);
        log.info("Image generation task sent to queue");
    }
}
