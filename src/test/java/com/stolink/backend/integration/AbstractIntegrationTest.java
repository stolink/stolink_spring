package com.stolink.backend.integration;

import com.stolink.backend.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    protected org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    protected org.neo4j.driver.Driver neo4jDriver;

    // OAuth2 관련 컴포넌트 Mocking (로컬/통합 테스트 시 불필요한 외부 의존성 제거)
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    protected com.stolink.backend.global.security.oauth2.CustomOAuth2UserService customOAuth2UserService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    protected com.stolink.backend.global.security.oauth2.OAuth2SuccessHandler oAuth2SuccessHandler;

    @BeforeEach
    void setUp() {
        databaseCleaner.execute();
    }
}
