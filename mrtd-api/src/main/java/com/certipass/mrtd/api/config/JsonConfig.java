package com.certipass.mrtd.api.config;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.PropertyNamingStrategies;

@Configuration
public class JsonConfig {
    @Bean
    JsonMapperBuilderCustomizer snakeCaseJsonCustomizer() {
        return builder -> builder.propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    @Bean
    com.fasterxml.jackson.databind.ObjectMapper coreObjectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .propertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.SNAKE_CASE)
                .build();
    }
}
