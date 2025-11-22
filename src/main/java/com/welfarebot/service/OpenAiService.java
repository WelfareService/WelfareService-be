package com.welfarebot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.config.OpenAiProperties;
import com.welfarebot.dto.GptMatchResponse;
import com.welfarebot.model.Benefit;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiService {

    private final RestClient restClient;
    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;
    private final BenefitCatalogService benefitCatalogService;

    @Value("classpath:prompt_match_engine.txt")
    private Resource promptResource;

    private String basePrompt = "You are a welfare match engine. Respond with JSON.";

    @PostConstruct
    public void loadPrompt() {
        try {
            if (promptResource != null && promptResource.exists()) {
                this.basePrompt = new String(promptResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("prompt_match_engine.txt 로딩 실패. 기본 프롬프트 사용.", e);
        }
    }

    public GptMatchResponse requestMatches(String message, List<String> historyMessages) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("OpenAI API Key가 설정되지 않았습니다.");
        }
        String promptWithIds = buildPromptWithIds();
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", promptWithIds));
        if (historyMessages != null) {
            historyMessages.stream()
                    .filter(StringUtils::hasText)
                    .forEach(history -> messages.add(new Message("user", history)));
        }
        messages.add(new Message("user", message));

        ChatRequest request = new ChatRequest(
                properties.getModel(),
                messages,
                properties.getTemperature()
        );
        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + properties.getApiKey())
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        String content = response.path("choices").path(0).path("message").path("content").asText();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("GPT 응답이 비어있습니다.");
        }
        try {
            return objectMapper.readValue(content, GptMatchResponse.class);
        } catch (Exception e) {
            log.error("GPT 응답 파싱 실패: {}", content, e);
            throw new IllegalStateException("GPT 응답 파싱 실패", e);
        }
    }

    private String buildPromptWithIds() {
        List<String> ids = benefitCatalogService.getAll().stream()
                .map(Benefit::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (ids.isEmpty()) {
            return basePrompt;
        }
        return basePrompt + "\n\n사용 가능한 benefit_id 목록: " + String.join(", ", ids)
                + "\n반드시 위 목록 중에서만 benefitId를 선택하라.";
    }

    private record ChatRequest(
            String model,
            List<Message> messages,
            double temperature
    ) {
    }

    private record Message(
            String role,
            String content
    ) {
    }
}
