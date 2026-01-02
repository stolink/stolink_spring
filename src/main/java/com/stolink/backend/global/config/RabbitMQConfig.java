package com.stolink.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.queues.analysis}")
    private String analysisQueue;

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
    public Queue analysisQueue() {
        return new Queue(analysisQueue, true);
    }

    @Bean
    public Queue imageQueue() {
        return new Queue(imageQueue, true);
    }

    /**
     * 문서 분석 큐 (대용량 분석 아키텍처)
     */
    @Bean
    public Queue documentAnalysisQueue() {
        return new Queue(documentAnalysisQueue, true);
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
}
