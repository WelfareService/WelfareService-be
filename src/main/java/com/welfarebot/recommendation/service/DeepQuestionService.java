package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class DeepQuestionService {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;
    @Value("${openai.model}")
    private String model;
    @Value("${openai.base-url}")
    private String baseUrl;

    public String generateFollowup(String history, List<String> signals) {

        String prompt = loadPrompt("prompt_deep_question_engine.txt")
                .replace("<<CONVERSATION_HISTORY>>", history != null ? history : "")
                .replace("<<DETECTED_SIGNALS_JSON>>", objectToJson(signals));

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", prompt)
                )
        );

        String raw = restClientBuilder.baseUrl(baseUrl)
                .build()
                .post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            return objectMapper.readTree(raw)
                    .get("choices").get(0)
                    .get("message").get("content").asText();
        } catch (Exception e) {
            throw new RuntimeException("Followup 생성 실패", e);
        }
    }

    private String loadPrompt(String filename) {
        try {
            return new String(
                    new ClassPathResource(filename).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (Exception e) {
            throw new RuntimeException("프롬프트 로딩 실패", e);
        }
    }

    private String objectToJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }
}
