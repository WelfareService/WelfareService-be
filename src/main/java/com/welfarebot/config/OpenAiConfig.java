package com.welfarebot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfig {

    @Bean
    public RestClient openAiRestClient(RestClient.Builder builder, OpenAiProperties properties) {
        return builder
                .baseUrl(properties.getBaseUrl())
                .build();
    }
}
