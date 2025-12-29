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

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.queues.analysis}")
    private String analysisQueue;

    @Value("${app.rabbitmq.queues.image}")
    private String imageQueue;

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
     * JSON 메시지 변환기 (snake_case 직렬화 지원)
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
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
    public RabbitTemplate imageRabbitTemplate(@Qualifier("imageConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    /**
     * Agent RabbitTemplate (Analysis용)
     */
    @Bean
    public RabbitTemplate agentRabbitTemplate(@Qualifier("agentConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
