package com.stolink.backend.global.config;

import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${app.rabbitmq.queues.analysis}")
    private String analysisQueue;

    @Value("${app.rabbitmq.queues.image}")
    private String imageQueue;

    @Bean
    public Queue analysisQueue() {
        return new Queue(analysisQueue, true); // durable
    }

    @Bean
    public Queue imageQueue() {
        return new Queue(imageQueue, true); // durable
    }
}
