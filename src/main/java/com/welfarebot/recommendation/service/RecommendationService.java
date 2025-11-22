package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.recommendation.dto.ChatResponse;
import com.welfarebot.recommendation.dto.GptMatchResponse;
import com.welfarebot.recommendation.model.Benefit;
import com.welfarebot.recommendation.model.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final OpenAiService openAiService;
    private final BenefitCatalogService catalogService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public ChatResponse chatRecommend(Long userId, String message) {

        GptMatchResponse gpt = openAiService.analyzeMessage(message);

        Optional<User> userOpt = userService.find(userId);

        if (needsMoreInfo(message, gpt.getSignals())) {
            return ChatResponse.builder()
                    .assistantMessage("현재 상황을 조금 더 알 수 있을까요?")
                    .recommendations(List.of())
                    .build();
        }

        List<Map.Entry<String, Double>> top =
                gpt.getScores().entrySet().stream()
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .limit(10)
                        .collect(Collectors.toList());

        List<ChatResponse.RecommendationItem> recs = new ArrayList<>();
        for (Map.Entry<String, Double> e : top) {
            Optional<Benefit> ob = catalogService.findById(e.getKey());
            if (ob.isEmpty()) {
                continue;
            }

            Benefit b = ob.get();
            double score = Optional.ofNullable(e.getValue()).orElse(0d);
            if (isOutsideAgeRange(userOpt, b)) {
                continue;
            }
            score = applyBaseTagBoost(userOpt, b, score);
            recs.add(ChatResponse.RecommendationItem.builder()
                    .benefitId(b.getBenefit_id())
                    .title(b.getTitle())
                    .category(b.getCategory())
                    .score(score)
                    .summary(b.getSummary())
                    .location(ChatResponse.RecommendationItem.Location.builder()
                            .lat(b.getLocation() != null ? b.getLocation().getLat() : null)
                            .lng(b.getLocation() != null ? b.getLocation().getLng() : null)
                            .build())
                    .build());
        }

        return ChatResponse.builder()
                .assistantMessage(
                        gpt.getAssistantMessage() != null
                                ? gpt.getAssistantMessage()
                                : "말씀해주신 상황을 바탕으로 산격동에서 받을 수 있는 지원을 찾아봤어요."
                )
                .recommendations(recs)
                .build();
    }

    private boolean isOutsideAgeRange(Optional<User> userOpt, Benefit benefit) {
        if (userOpt.isEmpty() || benefit.getEligibility() == null) {
            return false;
        }
        Integer userAge = userOpt.get().getAge();
        if (userAge == null) {
            return false;
        }
        Benefit.Eligibility eligibility = benefit.getEligibility();
        Integer min = eligibility.getAge_min();
        Integer max = eligibility.getAge_max();
        if ((min != null && userAge < min) || (max != null && userAge > max)) {
            log.debug("[Eligibility] {} skipped due to age mismatch: userAge={}, required={}~{}",
                    benefit.getBenefit_id(), userAge,
                    min != null ? min : "-",
                    max != null ? max : "-");
            return true;
        }
        return false;
    }

    private double applyBaseTagBoost(Optional<User> userOpt, Benefit benefit, double score) {
        if (userOpt.isEmpty()) {
            return score;
        }
        User user = userOpt.get();
        if (user.getBaseTags() == null) {
            return score;
        }
        try {
            List<String> tags = objectMapper.readValue(user.getBaseTags(), new TypeReference<List<String>>() {});
            if (tags.contains("미취업") && containsIgnoreCase(benefit.getCategory(), "일자리")) {
                score += 0.05;
            }
            if (tags.contains("저소득")
                    && (containsIgnoreCase(benefit.getCategory(), "주거")
                        || containsIgnoreCase(benefit.getCategory(), "생활"))) {
                score += 0.05;
            }
        } catch (Exception ignored) {
            // ignore parsing errors
        }
        return score;
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (value == null || keyword == null) {
            return false;
        }
        return value.toLowerCase().contains(keyword.toLowerCase());
    }

    private boolean needsMoreInfo(String message, List<String> signals) {
        if (message == null || signals == null || signals.isEmpty()) {
            return false;
        }
        String normalizedMessage = message.toLowerCase();
        boolean hasDigit = normalizedMessage.matches(".*\\d+.*");

        if (signals.contains("주거불안") && !hasDigit) {
            log.debug("[MoreInfo] Message missing rent amount detail for signals {}", signals);
            return true;
        }
        return false;
    }
}
