package com.welfarebot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "openai")
public class OpenAiProperties {

    /**
     * OpenAI API Key.
     */
    private String apiKey;

    /**
     * OpenAI API base url.
     */
    private String baseUrl = "https://api.openai.com/v1";

    /**
     * Chat completion model name.
     */
    private String model = "gpt-4.1";

    private double temperature = 0.2d;
}
