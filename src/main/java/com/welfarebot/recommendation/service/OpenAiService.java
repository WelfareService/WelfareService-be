package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.recommendation.dto.ConversationTurn;
import com.welfarebot.recommendation.dto.GptMatchResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    public GptMatchResponse analyzeMessage(String userMessage, List<ConversationTurn> history) {
        String systemPrompt = loadPrompt("prompt_match_engine.txt");
        List<Map<String, String>> messages = buildMessages(systemPrompt, history, userMessage);

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages
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

    private List<Map<String, String>> buildMessages(String systemPrompt,
                                                    List<ConversationTurn> history,
                                                    String latestMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        if (history != null) {
            for (ConversationTurn turn : history) {
                String role = normalizeRole(turn.getRole());
                if (role == null) {
                    continue;
                }
                String content = turn.getMessage() != null ? turn.getMessage() : "";
                messages.add(Map.of("role", role, "content", content));
            }
        }

        messages.add(Map.of("role", "user", "content", latestMessage != null ? latestMessage : ""));
        return messages;
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String value = role.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "assistant" -> "assistant";
            case "user" -> "user";
            default -> null;
        };
    }
}
