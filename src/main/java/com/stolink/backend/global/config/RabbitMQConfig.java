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
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@Configuration
public class RabbitMQConfig {

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
    public Queue imageQueue() {
        return new Queue(imageQueue, true);
    }

    /**
     * 문서 분석 큐 (대용량 분석 아키텍처)
     * 우선순위 큐 설정 (x-max-priority: 10)
     */
    @Bean
    public Queue documentAnalysisQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(documentAnalysisQueue)
                .withArgument("x-max-priority", 10)
                .build();
    }

    /**
     * 글로벌 병합 큐 (2차 Pass)
     */
    @Bean
    public Queue globalMergeQueue() {
        return new Queue(globalMergeQueue, true);
    }

    /**
     * JSON 메시지 변환기 (UTF-8 인코딩 및 snake_case 직렬화 지원)
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        return new Jackson2JsonMessageConverter(objectMapper);
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

    // ============================================================
    // Event Consumer Configuration (Analysis Event Sourcing)
    // ============================================================

    @Value("${app.rabbitmq.exchanges.analysis-events:stolink.analysis.events}")
    private String analysisEventsExchange;

    @Value("${app.rabbitmq.queues.analysis-completed:analysis.completed}")
    private String analysisCompletedQueue;

    @Value("${app.rabbitmq.queues.analysis-dlq:analysis.dlq}")
    private String analysisDlqQueue;

    /**
     * Analysis Events Exchange (Direct Exchange)
     */
    @Bean
    public org.springframework.amqp.core.DirectExchange analysisEventsExchange() {
        return new org.springframework.amqp.core.DirectExchange(analysisEventsExchange, true, false);
    }

    /**
     * Analysis Completed Queue with DLQ configuration
     */
    @Bean
    public Queue analysisCompletedQueue() {
        return org.springframework.amqp.core.QueueBuilder.durable(analysisCompletedQueue)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", analysisDlqQueue)
                .build();
    }

    /**
     * Dead Letter Queue for failed analysis events
     */
    @Bean
    public Queue analysisDlqQueue() {
        return new Queue(analysisDlqQueue, true);
    }

    /**
     * Binding: analysisEventsExchange -> analysisCompletedQueue
     */
    @Bean
    public org.springframework.amqp.core.Binding analysisCompletedBinding() {
        return org.springframework.amqp.core.BindingBuilder
                .bind(analysisCompletedQueue())
                .to(analysisEventsExchange())
                .with("analysis.completed");
    }

    /**
     * RabbitListenerContainerFactory for Agent RabbitMQ (Event Consumer용)
     */
    @Bean
    public org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory agentRabbitListenerContainerFactory(
            @Qualifier("agentConnectionFactory") ConnectionFactory connectionFactory) {
        org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(3);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false); // DLQ로 이동
        return factory;
    }
}
