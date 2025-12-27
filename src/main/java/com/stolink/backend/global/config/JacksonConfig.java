package com.stolink.backend.global.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.neo4j.driver.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;

@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.serializerByType(Value.class, new Neo4jValueSerializer());
        };
    }

    public static class Neo4jValueSerializer extends JsonSerializer<Value> {
        @Override
        public void serialize(Value value, JsonGenerator gen, SerializerProvider serializers)
                throws java.io.IOException {
            if (value == null || value.isNull()) {
                gen.writeNull();
            } else {
                gen.writeObject(value.asObject());
            }
        }
    }
}
