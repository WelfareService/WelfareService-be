package com.welfarebot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.dto.GptMatchResponse;
import com.welfarebot.dto.RecommendationChatRequest;
import com.welfarebot.dto.RecommendationResponse;
import com.welfarebot.exception.RecommendationException;
import com.welfarebot.model.Benefit;
import com.welfarebot.model.BenefitLocation;
import com.welfarebot.model.BenefitMatchLog;
import com.welfarebot.model.ConversationSignal;
import com.welfarebot.model.User;
import com.welfarebot.repository.BenefitMatchLogRepository;
import com.welfarebot.repository.ConversationSignalRepository;
import com.welfarebot.repository.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private static final int MAX_RECOMMENDATIONS = 5;
    private static final Map<String, Double> DEFAULT_CATEGORY_BOOST = Map.of(
            "주거", 1.0,
            "일자리", 1.0,
            "생계", 1.0,
            "심리", 1.0,
            "건강", 1.0,
            "금융", 1.0,
            "지역", 1.0
    );
    private static final Map<String, Double> DEFAULT_SIGNAL_BOOST = Map.of(
            "주거불안", 1.0,
            "미취업", 1.0,
            "저소득", 1.0,
            "심리위험", 1.0
    );
    private static final Map<String, String> CATEGORY_LABELS = Map.of(
            "HOUSING", "주거",
            "JOB", "일자리",
            "LIVING", "생계",
            "HEALTH", "건강",
            "FINANCE", "금융",
            "FAMILY", "가족",
            "LOCAL", "지역"
    );

    private final UserRepository userRepository;
    private final ConversationSignalRepository conversationSignalRepository;
    private final BenefitMatchLogRepository benefitMatchLogRepository;
    private final BenefitCatalogService benefitCatalogService;
    private final OpenAiService openAiService;
    private final ObjectMapper objectMapper;

    @Transactional
    public RecommendationResponse handleChat(RecommendationChatRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new RecommendationException("사용자를 찾을 수 없습니다. user_id=" + request.userId()));

        List<ConversationSignal> recentSignals = conversationSignalRepository.findTop5ByUserOrderByCreatedAtDesc(user);
        List<String> historyMessages = recentSignals.stream()
                .sorted(Comparator.comparing(ConversationSignal::getCreatedAt))
                .map(ConversationSignal::getMessage)
                .filter(StringUtils::hasText)
                .toList();

        Map<String, Benefit> benefitMap = benefitCatalogService.getBenefitMap();
        GptMatchResponse gptResponse;
        try {
            gptResponse = openAiService.requestMatches(request.message(), historyMessages);
        } catch (Exception e) {
            throw new RecommendationException("GPT 호출에 실패했습니다.", e);
        }

        persistConversationSignals(user, request.message(), gptResponse.getSignals());
        WeightSettings weightSettings = updateUserWeights(user, gptResponse.getSignals());

        List<GptMatchResponse.GptMatchScore> sanitizedScores = sanitizeScores(gptResponse.getScores(), benefitMap);
        List<GptMatchResponse.GptMatchScore> limitedScores = sanitizedScores.stream()
                .limit(MAX_RECOMMENDATIONS)
                .toList();

        persistMatchLogs(user, limitedScores);

        List<RecommendationResponse.RecommendationItem> items = limitedScores.stream()
                .map(score -> buildRecommendationItem(score, benefitMap.get(score.getBenefitId()),
                        gptResponse.getSignals(), weightSettings))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RecommendationResponse.RecommendationItem::score).reversed())
                .limit(MAX_RECOMMENDATIONS)
                .toList();

        return new RecommendationResponse(items);
    }

    private void persistConversationSignals(User user, String message, List<String> signals) {
        try {
            ConversationSignal conversationSignal = new ConversationSignal();
            conversationSignal.setUser(user);
            conversationSignal.setMessage(message);
            List<String> safeSignals = signals == null ? List.of() : signals;
            conversationSignal.setSignalsJson(objectMapper.writeValueAsString(safeSignals));
            conversationSignalRepository.save(conversationSignal);
        } catch (JsonProcessingException e) {
            log.error("대화 신호 JSON 직렬화 실패", e);
            throw new RecommendationException("대화 신호 저장에 실패했습니다.", e);
        }
    }

    private void persistMatchLogs(User user, List<GptMatchResponse.GptMatchScore> scores) {
        for (GptMatchResponse.GptMatchScore score : scores) {
            try {
                BenefitMatchLog logEntity = new BenefitMatchLog();
                logEntity.setUser(user);
                logEntity.setBenefitId(score.getBenefitId());
                logEntity.setScore(score.getScore());
                logEntity.setReason(score.getReason());
                logEntity.setPayload(objectMapper.writeValueAsString(score));
                benefitMatchLogRepository.save(logEntity);
            } catch (JsonProcessingException e) {
                log.error("혜택 매칭 로그 직렬화 실패 benefitId={}", score.getBenefitId(), e);
                throw new RecommendationException("혜택 매칭 로그 저장에 실패했습니다.", e);
            }
        }
    }

    private RecommendationResponse.RecommendationItem buildRecommendationItem(
            GptMatchResponse.GptMatchScore score,
            Benefit benefit,
            List<String> signals,
            WeightSettings weightSettings
    ) {
        if (benefit == null || score.getBenefitId() == null) {
            return null;
        }
        double gptScore = Math.max(0d, Math.min(1d, score.getScore()));
        double categoryBoost = resolveCategoryBoost(weightSettings, benefit);
        double signalBoost = resolveSignalBoost(weightSettings, signals);
        double finalScore = gptScore * categoryBoost * signalBoost;

        BenefitLocation location = benefit.primaryLocation();
        RecommendationResponse.Location locationDto = location == null ? null :
                new RecommendationResponse.Location(location.getLat(), location.getLng());

        return new RecommendationResponse.RecommendationItem(
                benefit.getId(),
                benefit.getTitle(),
                benefit.getCategory(),
                finalScore,
                benefit.getSummary(),
                locationDto
        );
    }

    private double resolveCategoryBoost(WeightSettings weightSettings, Benefit benefit) {
        String categoryKey = translateCategory(benefit.getCategory());
        return weightSettings.categoryBoost.getOrDefault(categoryKey, 1.0);
    }

    private double resolveSignalBoost(WeightSettings weightSettings, List<String> signals) {
        if (signals == null || signals.isEmpty()) {
            return 1.0;
        }
        return signals.stream()
                .filter(StringUtils::hasText)
                .map(signal -> weightSettings.signalBoost.getOrDefault(signal, 1.0))
                .max(Double::compareTo)
                .orElse(1.0);
    }

    private WeightSettings updateUserWeights(User user, List<String> signals) {
        String originalJson = user.getWeightJson();
        WeightSettings settings = parseWeightSettings(originalJson);
        boolean updated = !StringUtils.hasText(originalJson);
        if (signals != null) {
            for (String signal : signals) {
                if (!StringUtils.hasText(signal)) {
                    continue;
                }
                double boosted = settings.signalBoost.getOrDefault(signal, 1.0) * 1.15;
                settings.signalBoost.put(signal, boosted);
                updated = true;
            }
        }
        if (updated) {
            try {
                String updatedJson = objectMapper.writeValueAsString(settings);
                user.setWeightJson(updatedJson);
                userRepository.save(user);
            } catch (JsonProcessingException e) {
                log.error("사용자 가중치 JSON 직렬화 실패", e);
                throw new RecommendationException("사용자 가중치 저장 실패", e);
            }
        }
        return settings;
    }

    private WeightSettings parseWeightSettings(String weightJson) {
        try {
            if (weightJson != null && !weightJson.isBlank()) {
                WeightSettings settings = objectMapper.readValue(weightJson, WeightSettings.class);
                settings.ensureDefaults();
                return settings;
            }
        } catch (Exception e) {
            log.warn("사용자 가중치 파싱 실패. 기본값을 사용합니다.", e);
        }
        return WeightSettings.defaultSettings();
    }

    private List<GptMatchResponse.GptMatchScore> sanitizeScores(List<GptMatchResponse.GptMatchScore> scores,
                                                               Map<String, Benefit> benefitMap) {
        List<GptMatchResponse.GptMatchScore> filtered = new ArrayList<>();
        if (scores != null) {
            for (GptMatchResponse.GptMatchScore score : scores) {
                if (score.getBenefitId() != null && benefitMap.containsKey(score.getBenefitId())) {
                    filtered.add(score);
                }
            }
        }
        if (filtered.isEmpty()) {
            log.info("GPT가 반환한 점수가 없어 기본 우선순위 혜택으로 대체합니다.");
            return fallbackScores();
        }
        return filtered;
    }

    private List<GptMatchResponse.GptMatchScore> fallbackScores() {
        return benefitCatalogService.getAll().stream()
                .filter(benefit -> benefit.getId() != null)
                .sorted(Comparator.comparingDouble(this::resolvePriorityScore).reversed())
                .limit(MAX_RECOMMENDATIONS)
                .map(benefit -> {
                    GptMatchResponse.GptMatchScore score = new GptMatchResponse.GptMatchScore();
                    score.setBenefitId(benefit.getId());
                    score.setScore(0.5d);
                    score.setReason("기본 추천");
                    return score;
                })
                .toList();
    }

    private double resolvePriorityScore(Benefit benefit) {
        return benefit.getPriorityScore() != null ? benefit.getPriorityScore() : 0.5;
    }

    private String translateCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return "주거";
        }
        return CATEGORY_LABELS.getOrDefault(category.toUpperCase(), category);
    }

    private static class WeightSettings {
        private Map<String, Double> categoryBoost = new LinkedHashMap<>();
        private Map<String, Double> signalBoost = new LinkedHashMap<>();

        public Map<String, Double> getCategoryBoost() {
            return categoryBoost;
        }

        public void setCategoryBoost(Map<String, Double> categoryBoost) {
            this.categoryBoost = categoryBoost;
        }

        public Map<String, Double> getSignalBoost() {
            return signalBoost;
        }

        public void setSignalBoost(Map<String, Double> signalBoost) {
            this.signalBoost = signalBoost;
        }

        static WeightSettings defaultSettings() {
            WeightSettings settings = new WeightSettings();
            settings.categoryBoost.putAll(DEFAULT_CATEGORY_BOOST);
            settings.signalBoost.putAll(DEFAULT_SIGNAL_BOOST);
            return settings;
        }

        void ensureDefaults() {
            if (categoryBoost == null) {
                categoryBoost = new HashMap<>();
            }
            if (signalBoost == null) {
                signalBoost = new HashMap<>();
            }
            DEFAULT_CATEGORY_BOOST.forEach(categoryBoost::putIfAbsent);
            DEFAULT_SIGNAL_BOOST.forEach(signalBoost::putIfAbsent);
        }
    }
}
