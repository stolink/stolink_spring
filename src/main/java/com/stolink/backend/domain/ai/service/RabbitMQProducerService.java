package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
import com.stolink.backend.domain.ai.dto.ImageGenerationTaskDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RabbitMQProducerService {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitTemplate imageRabbitTemplate;

    @Value("${app.rabbitmq.queues.analysis}")
    private String analysisQueue;

    @Value("${app.rabbitmq.queues.image}")
    private String imageQueue;

    public RabbitMQProducerService(
            RabbitTemplate rabbitTemplate,
            @Qualifier("imageRabbitTemplate") RabbitTemplate imageRabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        this.imageRabbitTemplate = imageRabbitTemplate;
    }

    public void sendAnalysisTask(AnalysisTaskDTO task) {
        rabbitTemplate.convertAndSend(analysisQueue, task);
        log.info("Analysis task sent to queue: {}", task.getJobId());
    }

    /**
     * 이미지 생성 작업을 별도 RabbitMQ 인스턴스로 전송
     */
    public void sendImageGenerationTask(ImageGenerationTaskDTO task) {
        imageRabbitTemplate.convertAndSend(imageQueue, task);
        log.info("Image generation task sent to queue: jobId={}, action={}, characterId={}",
                task.getJobId(), task.getAction(), task.getCharacterId());
    }
}

