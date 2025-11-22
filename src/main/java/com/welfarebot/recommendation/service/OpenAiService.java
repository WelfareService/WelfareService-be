package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.welfarebot.recommendation.dto.GptMatchResponse;
import com.welfarebot.recommendation.model.User;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key}")
    private String apiKey;
    @Value("${openai.model}")
    private String model;
    @Value("${openai.base-url}")
    private String baseUrl;

    public GptMatchResponse analyzeMessage(String userMessage, User user) {
        String systemPrompt = loadPrompt("prompt_match_engine.txt")
                .replace("<<USER_PROFILE_BLOCK>>", buildUserProfileBlock(user));

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", new Object[]{
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                }
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
            log.debug("[OpenAI] raw response={}", raw);
            String content = objectMapper.readTree(raw)
                    .get("choices").get(0)
                    .get("message").get("content").asText();

            return objectMapper.readValue(content, GptMatchResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("GPT 응답 파싱 실패", e);
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

    private String buildUserProfileBlock(User user) {
        if (user == null) {
            return """
            - name: 알 수 없음
            - age: 미입력
            - residence: 미입력
            - base tags: []
            """;
        }
        String name = user.getName() != null ? user.getName() : "알 수 없음";
        String age = user.getAge() != null ? user.getAge().toString() : "미입력";
        String residence = user.getResidence() != null ? user.getResidence() : "미입력";
        List<String> tags = parseTags(user);
        return String.format("""
        - name: %s
        - age: %s
        - residence: %s
        - base tags: %s
        이 정보는 이미 확인된 사실이다. assistantMessage에서 다시 묻지 않는다.
        """, name, age, residence, tags);
    }

    private List<String> parseTags(User user) {
        if (user.getBaseTags() == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(user.getBaseTags(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
