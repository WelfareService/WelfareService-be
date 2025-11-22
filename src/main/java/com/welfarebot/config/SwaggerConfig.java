package com.welfarebot.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public GroupedOpenApi welfareOpenApi() {
        return GroupedOpenApi.builder()
                .group("welfarebot")
                .packagesToScan("com.welfarebot")
                .build();
    }
}
