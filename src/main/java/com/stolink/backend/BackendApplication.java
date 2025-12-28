package com.stolink.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = "com.stolink.backend.domain", excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(type = org.springframework.context.annotation.FilterType.REGEX, pattern = "com\\.stolink\\.backend\\.domain\\.character\\.repository\\..*"))
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}
