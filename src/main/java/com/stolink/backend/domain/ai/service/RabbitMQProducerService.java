package com.stolink.backend.domain.ai.service;

import com.stolink.backend.domain.ai.dto.AnalysisTaskDTO;
import com.stolink.backend.domain.ai.dto.GlobalMergeRequestDTO;
import com.stolink.backend.domain.ai.dto.ImageGenerationTaskDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RabbitMQProducerService {

    private final RabbitTemplate imageRabbitTemplate;
    private final RabbitTemplate agentRabbitTemplate;

    @Value("${app.rabbitmq.queues.analysis}")
    private String analysisQueue;

    @Value("${app.rabbitmq.queues.image}")
    private String imageQueue;

    @Value("${app.rabbitmq.queues.document-analysis}")
    private String documentAnalysisQueue;

    @Value("${app.rabbitmq.queues.global-merge}")
    private String globalMergeQueue;

    public RabbitMQProducerService(
            @Qualifier("imageRabbitTemplate") RabbitTemplate imageRabbitTemplate,
            @Qualifier("agentRabbitTemplate") RabbitTemplate agentRabbitTemplate) {
        this.imageRabbitTemplate = imageRabbitTemplate;
        this.agentRabbitTemplate = agentRabbitTemplate;
    }

    /**
     * Analysis 작업을 Agent RabbitMQ로 전송
     */
    public void sendAnalysisTask(AnalysisTaskDTO task) {
        try {
            // NOTE: Using documentAnalysisQueue for the new architecture
            agentRabbitTemplate.convertAndSend(documentAnalysisQueue, task);
            log.info("Analysis task sent to Agent RabbitMQ: jobId={}, projectId={}",
                    task.getJobId(), task.getProjectId());
        } catch (AmqpException e) {
            log.error("Failed to send analysis task: jobId={}, projectId={}",
                    task.getJobId(), task.getProjectId(), e);
            throw new RuntimeException("RabbitMQ message delivery failed", e);
        }
    }

    /**
     * Global Merge 요청 전송
     */
    public void sendGlobalMergeRequest(GlobalMergeRequestDTO request) {
        try {
            agentRabbitTemplate.convertAndSend(globalMergeQueue, request);
            log.info("Global merge request sent to Agent RabbitMQ: projectId={}, traceId={}",
                    request.getProjectId(), request.getTraceId());
        } catch (AmqpException e) {
            log.error("Failed to send global merge request: projectId={}",
                    request.getProjectId(), e);
            throw new RuntimeException("RabbitMQ message delivery failed", e);
        }
    }

    /**
     * Image 생성 작업을 Image RabbitMQ로 전송
     */
    public void sendImageGenerationTask(ImageGenerationTaskDTO task) {
        try {
            imageRabbitTemplate.convertAndSend(imageQueue, task);
            log.info("Image generation task sent to Image RabbitMQ: jobId={}, userId={}, projectId={}, characterId={}",
                    task.getJobId(), task.getUserId(), task.getProjectId(), task.getCharacterId());
        } catch (AmqpException e) {
            log.error("Failed to send image generation task: jobId={}, userId={}, projectId={}, characterId={}",
                    task.getJobId(), task.getUserId(), task.getProjectId(), task.getCharacterId(), e);
            throw new RuntimeException("RabbitMQ message delivery failed", e);
        }
    }
}
