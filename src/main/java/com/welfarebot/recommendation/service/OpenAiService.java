package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.recommendation.dto.ConversationTurn;
import com.welfarebot.recommendation.dto.GptMatchResponse;
import com.welfarebot.recommendation.model.User;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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

    public GptMatchResponse analyzeMessage(String userMessage, User user, List<ConversationTurn> history) {
        String systemPrompt = loadPrompt("prompt_match_engine.txt");
        List<Map<String, String>> messages = buildMessages(systemPrompt, user, history, userMessage);

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

    private String buildUserProfileBlock(User user) {
        String name = (user != null && user.getName() != null && !user.getName().isBlank())
                ? user.getName()
                : "알 수 없음";
        String age = (user != null && user.getAge() != null) ? user.getAge().toString() : "미입력";
        String residence = (user != null && user.getResidence() != null && !user.getResidence().isBlank())
                ? user.getResidence()
                : "미입력";
        List<String> tags = parseTags(user);
        return """
{
  "name": %s,
  "age": %s,
  "residence": %s,
  "baseTags": %s
}
이 정보는 이미 확인된 사실이다. assistantMessage에서 다시 묻지 않는다.
""".formatted(
                toJsonValue(name),
                toJsonValue(age),
                toJsonValue(residence),
                toJsonValue(tags)
        );
    }

    private List<Map<String, String>> buildMessages(String systemPrompt,
                                                    User user,
                                                    List<ConversationTurn> history,
                                                    String latestMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", "[USER PROFILE]\n" + buildUserProfileBlock(user)));

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

    private List<String> parseTags(User user) {
        if (user == null || user.getBaseTags() == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(user.getBaseTags(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
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

    private String toJsonValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            if (value == null) {
                return "null";
            }
            return "\"" + value.toString().replace("\"", "\\\"") + "\"";
        }
    }
}
