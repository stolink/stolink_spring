package com.stolink.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class ContextLoadTest {

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory;

    @Test
    void contextLoads() {
        // This test will fail if the application context cannot start
    }
}
