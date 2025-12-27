package com.stolink.backend.global.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 이미지 생성용 별도 RabbitMQ 인스턴스 설정
 * gitsecrets.md의 RABBITMQ_IMAGE_* 환경변수 사용
 */
@Configuration
public class RabbitMQImageConfig {

    @Value("${app.rabbitmq.image.host:localhost}")
    private String host;

    @Value("${app.rabbitmq.image.port:5672}")
    private int port;

    @Value("${app.rabbitmq.image.username:guest}")
    private String username;

    @Value("${app.rabbitmq.image.password:guest}")
    private String password;

    @Value("${app.rabbitmq.image.virtual-host:stolink}")
    private String virtualHost;

    @Value("${app.rabbitmq.queues.image}")
    private String imageQueue;

    /**
     * 이미지 생성용 별도 ConnectionFactory
     */
    @Bean
    @Qualifier("imageConnectionFactory")
    public ConnectionFactory imageConnectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        connectionFactory.setVirtualHost(virtualHost);
        return connectionFactory;
    }

    /**
     * JSON 메시지 변환기
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 이미지 생성용 RabbitTemplate
     */
    @Bean
    @Qualifier("imageRabbitTemplate")
    public RabbitTemplate imageRabbitTemplate(
            @Qualifier("imageConnectionFactory") ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    /**
     * 이미지 생성 큐 (별도 RabbitMQ 인스턴스용)
     */
    @Bean
    @Qualifier("imageGenerationQueue")
    public Queue imageGenerationQueue() {
        return new Queue(imageQueue, true); // durable
    }
}
