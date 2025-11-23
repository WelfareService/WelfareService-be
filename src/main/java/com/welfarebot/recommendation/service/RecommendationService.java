package com.welfarebot.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.welfarebot.recommendation.config.RecommendationPolicyLoader;
import com.welfarebot.recommendation.config.RecommendationPolicyLoader.BoostRule;
import com.welfarebot.recommendation.dto.ChatRequest;
import com.welfarebot.recommendation.dto.ChatResponse;
import com.welfarebot.recommendation.dto.GptMatchResponse;
import com.welfarebot.recommendation.model.Benefit;
import com.welfarebot.recommendation.model.BenefitMatchLog;
import com.welfarebot.recommendation.model.User;
import com.welfarebot.recommendation.model.UserPreRecommendation;
import com.welfarebot.recommendation.repository.BenefitMatchLogRepository;
import com.welfarebot.recommendation.service.RecommendationSessionTracker.RecommendationSessionState;
import com.welfarebot.recommendation.service.SignalOntologyService.SignalNormalizationResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
    private static final String DECISION_MC_SKIPPED = "MC_SKIPPED";
    private static final String DECISION_TOP3 = "TOP3_ISSUED";
    private static final String DECISION_OVERRIDE = "RECOMMENDATION_OVERRIDE";
    private static final String DECISION_UNKNOWN = "UNKNOWN_SIGNAL";

    private final OpenAiService openAiService;
    private final BenefitCatalogService catalogService;
    private final PreRecommendationService preRecommendationService;
    private final UserService userService;
    private final RejectService rejectService;
    private final BenefitMatchLogRepository benefitMatchLogRepository;
    private final ObjectMapper objectMapper;
    private final RecommendationPolicyLoader policyLoader;
    private final SignalOntologyService signalOntologyService;

    public RecommendationResult chatRecommend(ChatRequest request, RecommendationSessionState sessionState) {
        User user = (request.getUserId() != null) ? userService.find(request.getUserId()).orElse(null) : null;
        List<String> baseTags = normalizeBaseTags(user != null ? userService.parseTags(user) : List.of());

        GptMatchResponse gptResponse = openAiService.analyzeMessage(request.getMessage(), user, request.getHistory());
        SignalNormalizationResult signalResult = signalOntologyService.normalizeSignals(gptResponse.getSignals());
        List<String> canonicalSignals = signalResult.canonicalSignals();
        boolean insufficientInfo = Boolean.TRUE.equals(gptResponse.getInsufficientInfo());

        List<UserPreRecommendation> pool = preRecommendationService.getPool(user);
        boolean poolAvailable = !pool.isEmpty();

        boolean alreadyIssued = isAlreadyIssued(user, sessionState);
        boolean forceOverride = alreadyIssued && isReRecommendTrigger(request.getMessage());

        MinimalConditionResult mcResult = evaluateMinimalCondition(insufficientInfo, canonicalSignals, poolAvailable,
                alreadyIssued, forceOverride, signalResult.hasUnknownOnly());
        RiskLevel riskLevel = determineRiskLevel(canonicalSignals, mcResult);

        if (!mcResult.passed()) {
            String decisionType = signalResult.hasUnknownOnly() ? DECISION_UNKNOWN : DECISION_MC_SKIPPED;
            saveDecisionLog(user != null ? user.getId() : null, decisionType, mcResult.reasons(), canonicalSignals, riskLevel);
            return new RecommendationResult(ChatResponse.builder()
                    .assistantMessage(determineAssistantMessage(gptResponse.getAssistantMessage()))
                    .recommendations(List.of())
                    .riskLevel(riskLevel.name())
                    .build(), false, null);
        }

        Set<String> rejectedIds = rejectService.getRejectedBenefitIds(request.getUserId());
        List<ScoredResult> scoredResults = pool.stream()
                .map(entry -> scoreEntry(entry, baseTags, canonicalSignals, rejectedIds))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparingDouble(ScoredResult::finalScore).reversed())
                .limit(RECOMMENDATION_COUNT)
                .collect(Collectors.toList());

        if (scoredResults.isEmpty()) {
            log.warn("[Recommendation] No candidates remained after scoring. Fallback to catalog results.");
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

        boolean issued = !scoredResults.isEmpty();
        LocalDateTime issuedAt = issued ? LocalDateTime.now() : null;
        if (issued && user != null) {
            userService.markRecommendationIssued(user);
        }

        String decisionType = forceOverride ? DECISION_OVERRIDE : DECISION_TOP3;
        List<String> logReasons = reasonsWithOverride(mcResult);
        scoredResults.forEach(result -> saveMatchLog(user, decisionType, logReasons, canonicalSignals, riskLevel,
                result));

        List<ChatResponse.RecommendationItem> items = scoredResults.stream()
                .map(ScoredResult::item)
                .collect(Collectors.toList());

        ChatResponse response = ChatResponse.builder()
                .assistantMessage(determineAssistantMessage(gptResponse.getAssistantMessage()))
                .recommendations(items)
                .riskLevel(riskLevel.name())
                .build();

        return new RecommendationResult(response, issued, issuedAt);
    }

    private boolean isAlreadyIssued(User user, RecommendationSessionState sessionState) {
        if (user != null && Boolean.TRUE.equals(user.getRecommendationIssued())) {
            return true;
        }
        return sessionState != null && sessionState.issued();
    }

    private boolean isReRecommendTrigger(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = normalizeForSearch(message);
        return policyLoader.getReRecommendTriggers().stream()
                .map(this::normalizeForSearch)
                .anyMatch(normalized::contains);
    }

    private MinimalConditionResult evaluateMinimalCondition(boolean insufficientInfo,
                                                            List<String> canonicalSignals,
                                                            boolean poolAvailable,
                                                            boolean alreadyIssued,
                                                            boolean forceOverride,
                                                            boolean unknownOnly) {
        List<String> reasons = new ArrayList<>();
        if (insufficientInfo) {
            reasons.add("INSUFFICIENT_INFO");
        }
        if (!signalOntologyService.containsMinimalConditionSignal(canonicalSignals)) {
            reasons.add("NO_MINIMAL_SIGNAL");
        }
        if (!poolAvailable) {
            reasons.add("NO_PREPOOL");
        }
        if (alreadyIssued && !forceOverride) {
            reasons.add("ALREADY_ISSUED");
        }
        if (unknownOnly) {
            reasons.add("UNKNOWN_SIGNAL");
        }
        return new MinimalConditionResult(reasons.isEmpty(), List.copyOf(reasons), forceOverride);
    }

    private RiskLevel determineRiskLevel(List<String> signals, MinimalConditionResult mcResult) {
        if (signals == null || signals.isEmpty()) {
            return RiskLevel.NONE;
        }
        if (!mcResult.passed()) {
            return RiskLevel.LOW;
        }
        Set<String> signalSet = signals.stream().collect(Collectors.toSet());
        if ((signalSet.contains("주거불안") && signalSet.contains("미취업"))
                || (signalSet.contains("심리위험") && signalSet.contains("생계위험"))
                || (signalSet.contains("돌봄공백") && signalSet.contains("생계위험"))) {
            return RiskLevel.HIGH;
        }
        return RiskLevel.MEDIUM;
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
        Set<String> categories = policyLoader.resolveCategories(benefit.getCategory());
        BoostAccumulator accumulator = new BoostAccumulator(policyLoader.getBoostCap());
        List<BoostLog> baseTagLogs = applyBaseTagBoost(baseTags, categories, accumulator);
        List<BoostLog> signalLogs = applySignalBoost(signals, categories, accumulator);

        double finalScore = Math.min(baseScore + accumulator.totalBoost(), 1.0);
        ChatResponse.RecommendationItem item = buildItem(benefit, finalScore);
        return Optional.of(new ScoredResult(benefit, baseScore, finalScore, baseTagLogs, signalLogs, item));
    }

    private List<BoostLog> applyBaseTagBoost(List<String> baseTags,
                                             Set<String> categories,
                                             BoostAccumulator accumulator) {
        if (baseTags == null || baseTags.isEmpty() || categories.isEmpty()) {
            return List.of();
        }
        Map<String, BoostRule> rules = policyLoader.getBaseTagBoostRules();
        List<BoostLog> logs = new ArrayList<>();
        for (String tag : baseTags) {
            BoostRule rule = rules.get(tag);
            if (rule == null) {
                continue;
            }
            boolean match = rule.getCategories().stream().anyMatch(categories::contains);
            if (match) {
                double applied = accumulator.apply(rule.getBoost());
                if (applied > 0) {
                    logs.add(new BoostLog("baseTag:" + tag, applied));
                }
            }
        }
        return logs;
    }

    private List<BoostLog> applySignalBoost(List<String> signals,
                                            Set<String> categories,
                                            BoostAccumulator accumulator) {
        if (signals == null || signals.isEmpty() || categories.isEmpty()) {
            return List.of();
        }
        Map<String, BoostRule> rules = policyLoader.getSignalBoostRules();
        List<BoostLog> logs = new ArrayList<>();
        for (String signal : signals) {
            BoostRule rule = rules.get(signal);
            if (rule == null) {
                continue;
            }
            boolean match = rule.getCategories().stream().anyMatch(categories::contains);
            if (match) {
                double applied = accumulator.apply(rule.getBoost());
                if (applied > 0) {
                    logs.add(new BoostLog("signal:" + signal, applied));
                }
            }
        }
        return logs;
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

    private List<String> normalizeBaseTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return List.copyOf(normalized);
    }

    private String normalizeForSearch(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String determineAssistantMessage(String message) {
        if (message == null || message.isBlank()) {
            return "말씀해주신 상황을 바탕으로 도움될 수 있는 지원을 정리해 보았어요.";
        }
        return message;
    }

    private void saveMatchLog(User user,
                              String decisionType,
                              List<String> mcFailReasons,
                              List<String> normalizedSignals,
                              RiskLevel riskLevel,
                              ScoredResult result) {
        try {
            BenefitMatchLog entity = new BenefitMatchLog();
            entity.setUserId(user != null ? user.getId() : null);
            entity.setBenefitId(result.benefit().getBenefit_id());
            entity.setBaseScore(result.baseScore());
            entity.setBoostedScore(result.finalScore());
            entity.setAppliedBoostTags(toJson(result.tagBoostLogs()));
            entity.setAppliedSignals(toJson(result.signalBoostLogs()));
            entity.setDecisionType(decisionType);
            entity.setMcFailReasons(toJson(mcFailReasons));
            entity.setRiskLevel(riskLevel.name());
            entity.setNormalizedSignals(toJson(normalizedSignals));
            entity.setCreatedAt(LocalDateTime.now());
            benefitMatchLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("[Recommendation] Failed to save match log", e);
        }
    }

    private void saveDecisionLog(Long userId,
                                 String decisionType,
                                 List<String> reasons,
                                 List<String> normalizedSignals,
                                 RiskLevel riskLevel) {
        try {
            BenefitMatchLog entity = new BenefitMatchLog();
            entity.setUserId(userId);
            entity.setDecisionType(decisionType);
            entity.setMcFailReasons(toJson(reasons));
            entity.setAppliedBoostTags("[]");
            entity.setAppliedSignals("[]");
            entity.setRiskLevel(riskLevel.name());
            entity.setNormalizedSignals(toJson(normalizedSignals));
            entity.setCreatedAt(LocalDateTime.now());
            benefitMatchLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("[Recommendation] Failed to save decision log", e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
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

    private record MinimalConditionResult(boolean passed, List<String> reasons, boolean forcedOverride) {
    }

    private List<String> reasonsWithOverride(MinimalConditionResult mcResult) {
        if (!mcResult.forcedOverride()) {
            return mcResult.reasons();
        }
        List<String> augmented = new ArrayList<>();
        augmented.add("RE_REQUEST_TRIGGER");
        augmented.addAll(mcResult.reasons());
        return List.copyOf(augmented);
    }

    public record RecommendationResult(ChatResponse response, boolean recommendationIssued, LocalDateTime issuedAt) {
    }

    private static class BoostAccumulator {
        private final double cap;
        private double consumed = 0.0;

        private BoostAccumulator(double cap) {
            this.cap = cap;
        }

        double apply(double amount) {
            if (amount <= 0 || consumed >= cap) {
                return 0;
            }
            double remaining = cap - consumed;
            double applied = Math.min(amount, remaining);
            consumed += applied;
            return applied;
        }

        double totalBoost() {
            return consumed;
        }
    }

    private enum RiskLevel {
        HIGH,
        MEDIUM,
        LOW,
        NONE
    }
}
