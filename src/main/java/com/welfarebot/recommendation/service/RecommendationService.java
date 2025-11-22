package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.recommendation.dto.ChatResponse;
import com.welfarebot.recommendation.dto.GptMatchResponse;
import com.welfarebot.recommendation.model.Benefit;
import com.welfarebot.recommendation.model.BenefitMatchLog;
import com.welfarebot.recommendation.model.User;
import com.welfarebot.recommendation.model.UserPreRecommendation;
import com.welfarebot.recommendation.repository.BenefitMatchLogRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int RECOMMENDATION_COUNT = 3;
    private static final double BASE_TAG_BOOST_AMOUNT = 0.15;
    private static final double SIGNAL_BOOST_AMOUNT = 0.05;

    private static final Map<String, List<String>> BASE_TAG_CATEGORY_KEYWORDS = Map.of(
            "미취업", List.of("일자리"),
            "저소득", List.of("주거", "생계", "지역자원"),
            "심리위험", List.of("심리", "정신", "건강", "상담")
    );

    private static final Map<String, List<String>> SIGNAL_CATEGORY_KEYWORDS = Map.of(
            "주거불안", List.of("주거"),
            "미취업", List.of("일자리"),
            "심리위험", List.of("심리", "정신", "건강", "상담"),
            "부채", List.of("금융", "대출", "보증", "부채")
    );

    private final OpenAiService openAiService;
    private final BenefitCatalogService catalogService;
    private final PreRecommendationService preRecommendationService;
    private final UserService userService;
    private final RejectService rejectService;
    private final BenefitMatchLogRepository benefitMatchLogRepository;
    private final ObjectMapper objectMapper;

    public ChatResponse chatRecommend(Long userId, String message) {
        User user = (userId != null) ? userService.find(userId).orElse(null) : null;
        List<String> baseTags = normalizeTags(user != null ? userService.parseTags(user) : List.of());

        GptMatchResponse gpt = openAiService.analyzeMessage(message, user);
        List<String> signals = normalizeTags(normalizeSignals(gpt.getSignals()));
        boolean insufficientInfo = Boolean.TRUE.equals(gpt.getInsufficientInfo());

        if (!isMinimalConditionSatisfied(user, baseTags, signals, insufficientInfo)) {
            log.info("[MC] insufficient info or no signals → recommendations skipped");
            return ChatResponse.builder()
                    .assistantMessage(determineAssistantMessage(gpt.getAssistantMessage()))
                    .recommendations(List.of())
                    .build();
        }
        log.info("[MC] content-level minimal condition satisfied → generating recommendations");

        List<UserPreRecommendation> pool = preRecommendationService.getPool(user);
        if (pool.isEmpty()) {
            log.warn("[Recommendation] Pre recommendation pool is empty. Falling back to catalog");
            pool = preRecommendationService.getPool(null);
        }

        Set<String> rejected = rejectService.getRejectedBenefitIds(userId);

        List<ScoredResult> scoredResults = pool.stream()
                .map(entry -> scoreEntry(entry, baseTags, signals, rejected))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparingDouble(ScoredResult::finalScore).reversed())
                .limit(RECOMMENDATION_COUNT)
                .collect(Collectors.toList());

        if (scoredResults.isEmpty()) {
            log.warn("[Recommendation] No recommendations after scoring. Using fallback.");
            scoredResults = catalogService.getAll().stream()
                    .limit(RECOMMENDATION_COUNT)
                    .map(benefit -> new ScoredResult(
                            benefit,
                            0.5,
                            0.5,
                            List.of(),
                            List.of(),
                            buildItem(benefit, 0.5)
                    ))
                    .collect(Collectors.toList());
        }

        if (userId != null) {
            scoredResults.forEach(result -> saveMatchLog(userId, result));
        }

        List<ChatResponse.RecommendationItem> items = scoredResults.stream()
                .map(ScoredResult::item)
                .collect(Collectors.toList());

        return ChatResponse.builder()
                .assistantMessage(determineAssistantMessage(gpt.getAssistantMessage()))
                .recommendations(items)
                .build();
    }

    private Optional<ScoredResult> scoreEntry(UserPreRecommendation entry,
                                              List<String> baseTags,
                                              List<String> signals,
                                              Set<String> rejected) {
        if (entry == null || entry.getBenefitId() == null) {
            return Optional.empty();
        }
        Benefit benefit = catalogService.findById(entry.getBenefitId()).orElse(null);
        if (benefit == null) {
            return Optional.empty();
        }
        if (rejected.contains(entry.getBenefitId())) {
            return Optional.empty();
        }
        double baseScore = Optional.ofNullable(entry.getBaseScore()).orElse(0.5);
        double score = baseScore;
        List<BoostLog> tagLogs = new ArrayList<>();
        List<BoostLog> signalLogs = new ArrayList<>();

        score = applyBaseTagBoost(score, baseTags, benefit, tagLogs);
        score = applySignalBoost(score, signals, benefit, signalLogs);
        score = Math.min(score, 1.0);

        ChatResponse.RecommendationItem item = buildItem(benefit, score);
        return Optional.of(new ScoredResult(benefit, baseScore, score, tagLogs, signalLogs, item));
    }

    private ChatResponse.RecommendationItem buildItem(Benefit benefit, double score) {
        return ChatResponse.RecommendationItem.builder()
                .benefitId(benefit.getBenefit_id())
                .title(benefit.getTitle())
                .category(benefit.getCategory())
                .score(score)
                .summary(benefit.getSummary())
                .location(ChatResponse.RecommendationItem.Location.builder()
                        .lat(benefit.getLocation() != null ? benefit.getLocation().getLat() : null)
                        .lng(benefit.getLocation() != null ? benefit.getLocation().getLng() : null)
                        .build())
                .build();
    }

    private double applyBaseTagBoost(double score, List<String> baseTags, Benefit benefit, List<BoostLog> logs) {
        if (baseTags.isEmpty() || benefit.getCategory() == null) {
            return score;
        }
        String category = benefit.getCategory().toLowerCase(Locale.ROOT);
        double boosted = score;

        for (String tag : baseTags) {
            List<String> keywords = BASE_TAG_CATEGORY_KEYWORDS.get(tag);
            if (keywords == null) {
                continue;
            }
            boolean match = keywords.stream().anyMatch(category::contains);
            if (match) {
                boosted += BASE_TAG_BOOST_AMOUNT;
                logs.add(new BoostLog("baseTag:" + tag, BASE_TAG_BOOST_AMOUNT));
                log.info("[Boost] baseTags={}, benefit={}, +{} 적용됨", tag, benefit.getBenefit_id(), BASE_TAG_BOOST_AMOUNT);
            }
        }
        return boosted;
    }

    private double applySignalBoost(double score, List<String> signals, Benefit benefit, List<BoostLog> logs) {
        if (signals.isEmpty() || benefit.getCategory() == null) {
            return score;
        }
        String category = benefit.getCategory().toLowerCase(Locale.ROOT);
        double boosted = score;

        for (String signal : signals) {
            List<String> keywords = SIGNAL_CATEGORY_KEYWORDS.get(signal);
            if (keywords == null) {
                continue;
            }
            boolean match = keywords.stream().anyMatch(category::contains);
            if (match) {
                boosted += SIGNAL_BOOST_AMOUNT;
                logs.add(new BoostLog("signal:" + signal, SIGNAL_BOOST_AMOUNT));
                log.info("[Boost] signal={}, benefit={}, +{} 적용됨", signal, benefit.getBenefit_id(), SIGNAL_BOOST_AMOUNT);
            }
        }
        return boosted;
    }

    private List<String> normalizeSignals(List<String> signals) {
        if (signals == null) {
            return List.of();
        }
        return signals;
    }

    private List<String> normalizeTags(List<String> source) {
        if (source == null) {
            return List.of();
        }
        return source.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    private boolean isMinimalConditionSatisfied(User user, List<String> baseTags, List<String> signals, boolean insufficientInfo) {
        if (insufficientInfo) {
            return false;
        }
        if (signals == null || signals.isEmpty()) {
            return false;
        }
        if (baseTags == null || baseTags.isEmpty()) {
            if (user != null && user.getBaseTags() != null && !user.getBaseTags().isBlank()) {
                log.warn("[MC] baseTags JSON 존재하지만 파싱 결과가 비어 있습니다. raw={}", user.getBaseTags());
            }
            return false;
        }
        return true;
    }

    private boolean isMinimalConditionSatisfied(List<String> baseTags, List<String> signals, boolean insufficientInfo) {
        if (insufficientInfo) {
            return false;
        }
        if (signals == null || signals.isEmpty()) {
            return false;
        }
        if (baseTags == null || baseTags.isEmpty()) {
            return false;
        }
        return true;
    }

    private String determineAssistantMessage(String message) {
        if (message == null || message.isBlank()) {
            return "말씀해주신 상황을 바탕으로 도움될 수 있는 지원을 정리해 보았어요.";
        }
        return message;
    }

    private void saveMatchLog(Long userId, ScoredResult result) {
        if (userId == null) {
            return;
        }
        try {
            BenefitMatchLog logEntity = new BenefitMatchLog();
            logEntity.setUserId(userId);
            logEntity.setBenefitId(result.benefit().getBenefit_id());
            logEntity.setBaseScore(result.baseScore());
            logEntity.setBoostedScore(result.finalScore());
            logEntity.setAppliedBoostTags(toJson(result.tagBoostLogs()));
            logEntity.setAppliedSignals(toJson(result.signalBoostLogs()));
            logEntity.setCreatedAt(LocalDateTime.now());
            benefitMatchLogRepository.save(logEntity);
        } catch (Exception e) {
            log.warn("[Recommendation] Failed to save match log", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[Recommendation] JSON 직렬화 실패", e);
            return "[]";
        }
    }

    private record ScoredResult(
            Benefit benefit,
            double baseScore,
            double finalScore,
            List<BoostLog> tagBoostLogs,
            List<BoostLog> signalBoostLogs,
            ChatResponse.RecommendationItem item
    ) {
    }

    private record BoostLog(String type, double amount) {
    }
}
